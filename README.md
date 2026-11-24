# ApunteScroll

App Android que convierte tus PDFs de estudio en un feed para scrollear, con el estilo de
Instagram y X. Cuando abres una de esas redes, ApunteScroll la tapa y te muestra tus apuntes.

- Extrae el texto de los PDFs; las páginas escaneadas pasan por OCR en el teléfono.
- Corta el texto en tarjetas cortas, organizadas por materia y sección.
- "Me gusta" = "ya lo sé": repaso espaciado, preguntas en el feed, racha y estadísticas.
- Tus apuntes quedan en el teléfono: sin cuentas ni servidor propio. (El OCR de Google, ML Kit,
  envía diagnósticos a Google; ver PLAN.md, sección 10.)

Estado: **F2 (feed)**, probado en una Galaxy S23. Se crean materias, se importan PDFs, se
procesan en segundo plano (texto + OCR) y sus tarjetas se scrollean en Inicio en dos estilos:
Hilo (una tarjeta por publicación) y Carrusel (una sección por publicación, con historias por
materia), con "lo sé", guardar y el hilo completo de cada sección. La intercepción de Instagram
y X llega en F3. Ver [PLAN.md](PLAN.md).

---

## Compilar

### Requisitos

- **JDK 17**. En esta PC está en `%LOCALAPPDATA%\Android\jdk-17`.
- **Android SDK** con:
  - `platforms;android-37.1`: se compila contra la API 37.1 porque las versiones actuales de
    Compose y AndroidX lo exigen. La app igual **apunta a la API 36** (`targetSdk`), que es lo
    que pide Google Play.
  - `build-tools;36.1.0`
  - `platform-tools` (trae `adb`, para instalar en el teléfono)

  Sin Android Studio, se instalan con las
  [herramientas de línea de comandos](https://developer.android.com/studio#command-line-tools-only)
  (`sdkmanager`), con `JAVA_HOME` ya configurado (ver abajo). Primero se aceptan las licencias
  del SDK y después se instalan los paquetes:

  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
  ```

  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-37.1" "build-tools;36.1.0" "platform-tools"
  ```
- Un archivo `local.properties` en la raíz (no va al repositorio) con la ruta del SDK, por
  ejemplo:

  ```properties
  sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
  ```

No hace falta instalar Gradle ni Android Studio: el wrapper (`gradlew.bat`) descarga Gradle 9.9.2
la primera vez.

### Comandos (PowerShell, desde la raíz del repositorio)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
```

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

El APK queda en `app\build\outputs\apk\debug\app-debug.apk`. Para instalarlo en un teléfono
conectado por USB (con la depuración USB activada):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Instalada así (no desde Google Play), el modelo de OCR se descarga la primera vez que se procesa
una página escaneada: esa vez el teléfono necesita internet. Mientras tanto el PDF queda "Leyendo
páginas" y el worker reintenta solo durante unos 18 minutos; si el modelo no llegó, el PDF queda
en error y **Reintentar** (en el menú del PDF) sigue desde la página donde había quedado.

### Versiones

| Pieza | Versión |
|---|---|
| Gradle | 9.9.2 |
| Android Gradle Plugin | 9.5.2 (con Kotlin incorporado: no se aplica `org.jetbrains.kotlin.android`) |
| Kotlin | 2.4.21 |
| KSP | 2.3.14 |
| Compose BOM | 2026.11.01 |
| compileSdk / targetSdk / minSdk | 37.1 / 36 / 26 |

---

## Estructura

```
app/src/main/java/com/apuntescroll/
├── ApunteScrollApp.kt   Application y contenedor de dependencias
├── datos/               ajustes, repositorio y base de datos Room (bd/)
├── pdf/                 copia de PDFs, texto (PdfBox), OCR (ML Kit), worker de procesamiento, rearmado
├── dominio/             limpieza del texto y corte en tarjetas (Kotlin puro, con pruebas)
├── feed/                orden del feed y publicaciones de Hilo y Carrusel (Kotlin puro, con pruebas)
└── ui/                  MainActivity, navegación, biblioteca, feed, pantallas y tema
app/src/test/            pruebas locales (JUnit) de dominio/, feed/ y del rearmado
app/schemas/             esquemas de la base (Room), para las migraciones
```

Si se cambia el armado de tarjetas (`dominio/`), hay que subir `ArmadorTarjetas.VERSION`: al
abrir la app, los PDFs ya procesados se rearman con el texto guardado y conservan el progreso de
las tarjetas que no cambiaron (PLAN.md, sección 3.4).

En F3 se suma `intercepcion/` (PLAN.md, sección 9).
