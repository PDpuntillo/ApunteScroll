package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.intercepcion.reglas.ServiciosHabilitados
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.lang.reflect.Modifier
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lo que Google Play revisa del servicio de accesibilidad (PLAN.md, sección 10.1), comprobado sobre
 * los archivos del proyecto: si una de estas pruebas falla, hay que revisar el aviso, la
 * declaración de Play Console y el video antes de cambiarla.
 */
class PoliticaIntercepcionTest {

    private val android = "http://schemas.android.com/apk/res/android"

    /** Las pruebas corren desde el módulo (app/); como respaldo, desde la raíz del repositorio. */
    private fun archivo(ruta: String): File =
        listOf(File("src/main/$ruta"), File("app/src/main/$ruta")).firstOrNull { it.exists() }
            ?: error("No se encontró src/main/$ruta")

    private fun xml(ruta: String): Element {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return f.newDocumentBuilder().parse(archivo(ruta)).documentElement
    }

    private fun Element.attr(nombre: String): String? = getAttributeNS(android, nombre).takeIf { hasAttributeNS(android, nombre) }

    private fun Element.hijos(tag: String): List<Element> {
        val nodos = getElementsByTagName(tag)
        return (0 until nodos.length).map { nodos.item(it) as Element }
    }

    /** El código sin comentarios (de línea y de bloque) ni el contenido de los textos de KDoc. */
    private fun codigo(ruta: String): String =
        archivo(ruta).readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.replace(Regex("//.*$"), "") }

    private val raiz = "java/com/apuntescroll"

    @Test
    fun `el xml del servicio pide solo cambios de ventana sin contenido`() {
        val s = xml("res/xml/servicio_intercepcion.xml")
        assertEquals("typeWindowStateChanged", s.attr("accessibilityEventTypes"))
        assertEquals("false", s.attr("canRetrieveWindowContent"))
        assertTrue(s.attr("isAccessibilityTool") != "true")
        assertTrue(s.attr("accessibilityFlags") == null || s.attr("accessibilityFlags") == "flagDefault")
    }

    @Test
    fun `el xml del servicio no pide gestos teclas capturas ni packageNames`() {
        val s = xml("res/xml/servicio_intercepcion.xml")
        listOf(
            "canPerformGestures", "canRequestFilterKeyEvents", "canTakeScreenshot",
            "canRequestTouchExplorationMode", "canRequestFingerprintGestures", "packageNames",
        ).forEach { assertEquals(it, null, s.attr(it)) }
    }

    @Test
    fun `no hay variantes del xml del servicio`() {
        val res = archivo("res")
        val variantes = res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("xml-") }
            .filter { File(it, "servicio_intercepcion.xml").exists() }
        assertEquals(emptyList<File>(), variantes)
    }

    @Test
    fun `el manifiesto no pide permisos de mas`() {
        val pedidos = xml("AndroidManifest.xml").hijos("uses-permission").mapNotNull { it.attr("name") }
        listOf(
            "android.permission.QUERY_ALL_PACKAGES", "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS", "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        ).forEach { assertFalse(it, it in pedidos) }
    }

    @Test
    fun `las queries coinciden con el catalogo`() {
        val queries = xml("AndroidManifest.xml").hijos("queries").flatMap { it.hijos("package") }.mapNotNull { it.attr("name") }
        assertEquals(Redes.CONOCIDAS.map { it.paquete }.toSet(), queries.toSet())
    }

    @Test
    fun `la pantalla interceptada va en su tarea y fuera de recientes`() {
        val a = xml("AndroidManifest.xml").hijos("activity").single { it.attr("name") == ".intercepcion.FeedInterceptadoActivity" }
        assertEquals("true", a.attr("noHistory"))
        assertEquals("true", a.attr("excludeFromRecents"))
        assertEquals("false", a.attr("exported"))
        assertEquals("singleTask", a.attr("launchMode"))
        assertEquals("com.apuntescroll.interceptado", a.attr("taskAffinity"))
        assertEquals(null, a.attr("showWhenLocked"))
    }

    @Test
    fun `el servicio exige BIND_ACCESSIBILITY_SERVICE`() {
        val s = xml("AndroidManifest.xml").hijos("service").single { it.attr("name") == ".intercepcion.ServicioIntercepcion" }
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", s.attr("permission"))
    }

    @Test
    fun `el servicio sigue llamandose ServicioIntercepcion`() {
        // Android guarda el servicio activado por su nombre: renombrarlo lo apagaría en todos los teléfonos.
        val servicios = xml("AndroidManifest.xml").hijos("service").mapNotNull { it.attr("name") }
        assertTrue(".intercepcion.ServicioIntercepcion" in servicios)
        assertTrue(codigo("$raiz/intercepcion/ServicioIntercepcion.kt").contains("class ServicioIntercepcion :"))
        assertEquals(
            "com.apuntescroll.intercepcion.ServicioIntercepcion",
            Class.forName("com.apuntescroll.intercepcion.ServicioIntercepcion", false, javaClass.classLoader).name,
        )
    }

    @Test
    fun `el servicio solo usa el nombre del paquete del evento`() {
        val prohibidas = listOf(
            "getSource", ".source", ".text", "getText", "className", "contentDescription", "isFullScreen",
            "rootInActiveWindow", "windows", "performGlobalAction", "dispatchGesture", "setServiceInfo",
            "takeScreenshot", "event.toString", "\"\$event\"", "\${event}",
        )
        listOf("ServicioIntercepcion.kt", "CoordinadorIntercepcion.kt", "DetectorAccesibilidad.kt").forEach { nombre ->
            val c = codigo("$raiz/intercepcion/$nombre")
            prohibidas.forEach { assertFalse("$nombre usa $it", c.contains(it)) }
        }
    }

    @Test
    fun `el servicio mira el consentimiento antes de leer el paquete`() {
        val c = codigo("$raiz/intercepcion/ServicioIntercepcion.kt")
        val consentimiento = c.indexOf("consentimientoVigente")
        val paquete = c.indexOf("packageName")
        assertTrue(consentimiento >= 0 && paquete >= 0)
        assertTrue(consentimiento < paquete)
    }

    @Test
    fun `ServiciosHabilitados no devuelve ni expone la lista`() {
        val clase = ServiciosHabilitados::class.java
        val publicos = clase.declaredMethods.filter { Modifier.isPublic(it.modifiers) }
        assertEquals(listOf("contiene"), publicos.map { it.name })
        publicos.forEach { assertEquals(Boolean::class.javaPrimitiveType, it.returnType) }
        // Solo INSTANCE (el object) y la marca $stable del compilador de Compose: ningún texto ni lista.
        val campos = clase.declaredFields.filter { Modifier.isPublic(it.modifiers) && it.type != clase }
        assertTrue(campos.all { it.name.startsWith("$") && it.type == Int::class.javaPrimitiveType })
    }

    @Test
    fun `no hay Log en intercepcion salvo RegistroDepuracion y solo en debug`() {
        val dir = archivo("$raiz/intercepcion")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "RegistroDepuracion.kt" }.forEach { f ->
            val c = f.readText()
            assertFalse("${f.name} importa Log", c.contains("import android.util.Log"))
            assertFalse("${f.name} usa Log", Regex("\\bLog\\.[a-z]+\\(").containsMatchIn(c))
        }
        val registro = codigo("$raiz/intercepcion/RegistroDepuracion.kt")
        assertTrue(registro.contains("if (BuildConfig.DEBUG) Log.d("))
    }

    @Test
    fun `reglas y feed no importan android`() {
        listOf("$raiz/intercepcion/reglas", "$raiz/feed").forEach { ruta ->
            archivo(ruta).walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val c = f.readText()
                assertFalse("${f.name} importa android", Regex("^import androidx?\\.", RegexOption.MULTILINE).containsMatchIn(c))
            }
        }
    }
}
