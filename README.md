# ApunteScroll

App Android que convierte tus PDFs de estudio en un feed para scrollear, con el estilo de
Instagram y X. Cuando abres una de esas redes, ApunteScroll la tapa y te muestra tus apuntes.

- Extrae el texto de los PDFs; las páginas escaneadas pasan por OCR en el teléfono.
- Corta el texto en tarjetas cortas, organizadas por materia y sección.
- "Me gusta" = "ya lo sé": repaso espaciado, preguntas en el feed, racha y estadísticas.
- Tus apuntes quedan en el teléfono: sin cuentas ni servidor propio. (El OCR de Google, ML Kit,
  envía diagnósticos a Google; ver PLAN.md, sección 10.)

Estado: **F3 (intercepción)**, implementada; F0 a F2 están probadas en una Galaxy S23 y F3
espera su prueba manual (ver "Probar la intercepción"). Se crean materias, se importan PDFs, se
procesan en segundo plano (texto + OCR) y sus tarjetas se scrollean en Inicio en dos estilos:
Hilo (una tarjeta por publicación) y Carrusel (una sección por publicación, con historias por
materia), con "lo sé", guardar y el hilo completo de cada sección. Con el servicio de
accesibilidad activado (y su aviso aceptado), abrir Instagram muestra el Carrusel de apuntes y
abrir X, el Hilo; para entrar a la red se gana un pase leyendo tarjetas, o se elige "Solo
recordatorio" o "Reemplazo total". Ver [PLAN.md](PLAN.md).

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
├── feed/                orden del feed, publicaciones y lectura en foco (Kotlin puro, con pruebas)
├── intercepcion/        servicio de accesibilidad, detector, coordinador y pantalla interceptada
│   ├── reglas/          clasificación, reglas, freno, motor, pase y estados (Kotlin puro, con pruebas)
│   └── ui/              pantalla interceptada, aviso y consentimiento, tarjeta de Inicio, Ajustes > Redes
└── ui/                  MainActivity, navegación, biblioteca, feed, pantallas y tema
app/src/debug/           manifiesto solo de debug (la red de prueba)
app/src/test/            pruebas locales (JUnit) de dominio/, feed/, intercepcion/ y del rearmado
app/schemas/             esquemas de la base (Room), para las migraciones
```

Si se cambia el armado de tarjetas (`dominio/`), hay que subir `ArmadorTarjetas.VERSION`: al
abrir la app, los PDFs ya procesados se rearman con el texto guardado y conservan el progreso de
las tarjetas que no cambiaron (PLAN.md, sección 3.4).

`PoliticaIntercepcionTest` revisa el XML del servicio de accesibilidad, el manifiesto y el código
del servicio: si falla, antes de cambiarla hay que revisar el aviso, la declaración de Play
Console y el video (PLAN.md, sección 10).

---

## Probar la intercepción

### Preparación (PowerShell)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell pm list packages | Select-String "calculator|instagram|twitter"
& $adb logcat -c
& $adb logcat -s Intercepcion:D ActivityTaskManager:I StrictMode:D
```

La última línea va en otra terminal: en debug, el log `Intercepcion` anota cada paquete que pasa
al frente y lo que decidió el motor (en release no se anota nada), y `StrictMode` anota cada
pantalla que Android no deja abrir desde segundo plano.

### El flujo normal

Inicio (o Ajustes > Redes) → "Ver cómo funciona" → el aviso → **Aceptar y activar**. Se abre
Accesibilidad de Android: en One UI, "Aplicaciones instaladas" → ApunteScroll → activar el
interruptor (Android muestra dos diálogos). Al volver, Ajustes > Redes dice "Funcionando". Para
apagarlo: Ajustes > Redes > "Apagar y retirar el permiso".

### Activar por adb (solo para pruebas repetidas)

Esto saltea el aviso, así que de paso prueba el estado "activado sin aviso" (la app abre el
aviso sola y, sin aceptarlo, el servicio no hace nada). La lista de servicios se **amplía**: si
se la reemplaza, se apaga el servicio del Bloqueador.

```powershell
$antes = (& $adb shell settings get secure enabled_accessibility_services).Trim()
$propio = "com.apuntescroll/com.apuntescroll.intercepcion.ServicioIntercepcion"
$nuevo = if ($antes -and $antes -ne "null") { "$($antes):$propio" } else { $propio }
& $adb shell settings put secure enabled_accessibility_services "$nuevo"
& $adb shell settings put secure accessibility_enabled 1
# Para volver como estaba:
& $adb shell settings put secure enabled_accessibility_services "$antes"
```

### Solo en debug

- **Red de prueba**: la calculadora de Samsung (`com.sec.android.app.popupcalculator`) y la de
  Google se interceptan con el estilo Hilo, para probar sin sesión en Instagram o en el
  emulador. Arrancan **apagadas**: se prenden en Ajustes > Redes.
- **Pase de 1 minuto**: en "Minutos que dura" aparece la opción 1, para probar el vencimiento.

### Comandos útiles

```powershell
& $adb shell dumpsys accessibility | Select-String apuntescroll -Context 0,6
& $adb shell dumpsys activity activities | Select-String "interceptado|instagram|twitter|Task\{"
& $adb shell dumpsys activity recents | Select-String apuntescroll
& $adb shell dumpsys package com.apuntescroll | Select-String "ADVANCED_PROTECTION"
& $adb shell monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1
& $adb shell monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1
& $adb logcat -d -s ActivityTaskManager:I | Select-String "Displayed com.apuntescroll"
& $adb logcat -d | Select-String "Background activity launch blocked|Abort background activity|BlockedBackgroundActivityLaunch"
& $adb shell dumpsys audio | Select-String "mode"
& $adb shell settings get global boot_count
& $adb shell input keyevent KEYCODE_SLEEP; & $adb shell input keyevent KEYCODE_WAKEUP
& $adb shell settings put system accelerometer_rotation 0; & $adb shell settings put system user_rotation 1
& $adb shell cmd uimode night yes
& $adb shell settings put system font_scale 2.0
$proc = (& $adb shell pidof com.apuntescroll).Trim(); & $adb shell run-as com.apuntescroll kill -9 $proc
& $adb shell am force-stop com.apuntescroll
& $adb reboot
```

(`dumpsys accessibility` muestra si el servicio está vinculado y sus capacidades: no tiene que
aparecer "retrieve window content". La tarea interceptada no tiene que aparecer en `recents`.
`ADVANCED_PROTECTION` tiene que decir `granted=true`. `$pid` es una variable reservada de
PowerShell: por eso `$proc`.)

**Versión release** (escenario 37). `assembleRelease` genera un APK sin firmar hasta P1, que trae
la clave de subida; para probarlo, se firma con la clave de debug de esta PC:

```powershell
.\gradlew.bat assembleRelease
$bt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0"
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out app\build\outputs\apk\release\app-release-prueba.apk app\build\outputs\apk\release\app-release-unsigned.apk
& $adb install -r app\build\outputs\apk\release\app-release-prueba.apk
& $adb logcat -s Intercepcion:D
```

### Escenarios

Anotar el resultado de cada uno en PLAN.md, sección 14.

| # | Escenario | Se espera |
|---|---|---|
| 1 | Consentimiento completo: tarjeta de Inicio → aviso → "Aceptar y activar" → One UI → activar → volver | Anotar el camino exacto en One UI 8.5, si resalta el servicio y las palabras de los dos diálogos de Android (para `aviso_android`). El estado pasa a "Funcionando". |
| 2 | "Ahora no", la flecha y el gesto atrás sobre el aviso; 3 rechazos | No se guarda el consentimiento. La tarjeta se oculta y, con 3 rechazos, deja de ofrecerse; queda el acceso "Mostrar mis apuntes al abrir Instagram o X" en Inicio y la sección de Ajustes. |
| 3 | Activado por adb sin consentimiento → abrir la app | El aviso se abre solo, una vez. Instagram no intercepta. "Ahora no" → `dumpsys accessibility` muestra el servicio apagado. |
| 4 | Instalación nueva sin PDFs | La tarjeta del aviso se ve arriba del estado vacío de Inicio. |
| 5 | Instagram desde el launcher, Recientes, una notificación y un enlace de instagram.com en WhatsApp | Carrusel en menos de 1 s ("Displayed"), sin destello ni "blocked". `affinity=com.apuntescroll.interceptado`, fuera de Recientes. |
| 6 | X con un video en curso | Hilo y "Abriste X". X no pasa a PiP. |
| 7 | Atrás, ✕ y "Volver al inicio" | Siempre el launcher. Atrás dentro del hilo vuelve al feed. |
| 8 | Inicio o Recientes con el feed abierto y volver a Instagram | El feed se cerró. Al volver a Instagram, intercepta de nuevo. |
| 9 | Pase con N=5 y M=1 (debug): pasar el dedo rápido, leer unos 5 s, "Lo sé" después de 1,5 s, ♥ de un carrusel | Pasar el dedo no suma. Leer y "Lo sé" después de verla, sí. El ♥ suma solo las diapositivas vistas. |
| 10 | "Entrar" | Toast con la hora. Instagram vuelve a la pantalla en que estaba (un DM abierto). Se puede salir y volver sin que lo frene. |
| 11 | Esperar a que el pase venza **scrolleando Reels** | Toast del último minuto y después "Se terminó tu pase de redes.". Anotar la demora. |
| 12 | Pase compartido, con X en Pase y con X en Reemplazo | En Pase entra sin feed. En Reemplazo, intercepta. |
| 13 | Recordatorio: "Ir igual" a los 5 s; teclado en un DM, cortina, permiso de cámara, compartir; WhatsApp 10 s y 40 s; apagar la pantalla | Sin recordatorio hasta los 40 s o el apagado. Desde ahí, con recordatorio. |
| 14 | Reemplazo total | No se puede entrar. "Cambiar modo" abre Ajustes > Redes. |
| 15 | Sin PDFs, procesando y todo sabido | Los textos de la puerta. "Entrar" activo en Pase. |
| 16 | Rotar en el feed y en la cuenta de 5 s | Se conservan la posición, la cuenta y el progreso. No se cuenta dos veces. |
| 16 bis | Rotar con el hilo abierto en la pantalla interceptada | Sigue en el hilo (no vuelve al feed). |
| 17 | Feed de Instagram abierto: una notificación de X, y una de Instagram | X: cambia a Hilo. Instagram: vuelve a aparecer (revisión). |
| 18 | Videollamada y llamada entrante de Instagram (con otra cuenta); llamada de WhatsApp y después abrir Instagram | No intercepta. Anotar el modo de `dumpsys audio`. Si intercepta, ajustar y corregir el aviso (`aviso_no_hace`). |
| 19 | Fijar Instagram en pantalla | No intercepta y no aparece el aviso del sistema. |
| 20 | Bloquear dentro de Instagram con el pase vencido y desbloquear | Aparece al desbloquear. Anotar si fue por evento o por la revisión. Comprobar que `USER_PRESENT` llega. |
| 21 | Feed abierto → apagar la pantalla → desbloquear | ¿Sigue el feed? (`noHistory` mientras duerme). El progreso queda. |
| 22 | Pantalla dividida y vista emergente con Instagram | Sin bucle ni parpadeo, con la franja de aviso. Anotar dónde se abre. |
| 23 | Matar el proceso (`run-as kill`) con un pase activo | El servicio se reconecta. El pase sigue y vence en vivo. |
| 24 | `am force-stop` | "Se apagó" en Inicio y Ajustes. "Activar de nuevo" funciona. |
| 25 | Reiniciar con un pase activo | El pase terminó. El servicio se conecta después del primer desbloqueo. |
| 26 | **Calibración**: con el log de debug, recorrer Instagram (feed, Reels, historias, comentarios, DMs, perfil, compartir, cámara) y X (timeline, detalle, video, perfil); además el teclado de Samsung, los paneles Edge, la captura, Samsung Pass, Bixby y la hoja de compartir de Samsung | Anotar cada cuánto llegan eventos dentro de cada red y los paquetes de ruido de One UI (sumarlos a `TRANSITORIOS`). Decide `VENCER_EN_VIVO`. Medir el arranque en frío y el tiempo hasta la primera tarjeta ("Displayed"). |
| 27 | Cambiar la hora a mano (± 1 día) con un pase activo | Lo que queda no cambia. |
| 28 | Bloqueador activo con Instagram en su lista | Anotar quién gana. Aparece la tarjeta "Otra app tapó tus apuntes" (o la del bucle). |
| 29 | "Apagar y retirar el permiso" | `disableSelf` lo saca de `settings get secure enabled_accessibility_services`. El estado pasa a "Apagada". |
| 30 | Ajustes de Android > Accesibilidad > ApunteScroll > "Ajustes" | Abre Ajustes > Redes. Anotar cómo muestra One UI el resumen y la introducción. |
| 31 | APK copiado y abierto desde Mis archivos: antes `& $adb uninstall com.apuntescroll` (borra los datos de la app; si no, se actualiza sobre la instalación de adb) y `& $adb push app\build\outputs\apk\debug\app-debug.apk /sdcard/Download/` | ¿El interruptor sale gris? Aparece la pista de "ajustes restringidos". |
| 32 | Al día siguiente | La notificación de Android de las 24 h. Anotar el texto. |
| 33 | TalkBack en la pantalla interceptada y en el aviso; letra 2.0; modo oscuro | Se anuncia el panel. El progreso se lee sin repetirse. "¡Listo!" se anuncia una vez. Los botones bajan y no hay destello. |
| 34 | `adb install -r` de una versión nueva con el servicio activo | Sigue activo y el pase sigue. Si cambió `ArmadorTarjetas.VERSION`, aparece "Preparando tus tarjetas…". |
| 35 | Emulador API 37 con la calculadora de prueba: activar la Protección avanzada (si hay imagen) | El servicio se apaga y aparece el texto de la Protección avanzada. Sin "blocked" en logcat. |
| 36 | Emulador API 30: Inicio dentro de la red de prueba y volver por Recientes | La demora de unos 5 s no se cuenta como fallo. |
| 37 | Versión release (ver arriba) con `logcat -s Intercepcion` | No aparece ningún paquete. |

### Límites conocidos

- En **pantalla dividida** o en una **vista emergente**, la red sigue a la vista en su parte.
- Un video de X que ya estaba en **PiP** sigue flotando.
- **Compartir hacia Instagram** desde la galería también intercepta.
- Durante cualquier **llamada por internet** (WhatsApp, Meet…) no se intercepta.
- Algunas pantallas de Android 14+ no avisan a la accesibilidad: al vencer el pase, el feed
  puede aparecer encima de otra app (✕ lleva al inicio).
- El pase **termina al reiniciar** el teléfono.
- En **Xiaomi, Redmi o POCO** hace falta el permiso "Mostrar ventanas emergentes mientras se
  ejecuta en segundo plano" (la app lo avisa).
- Quien instala el APK a mano (no desde Google Play) en Android 13 o más tiene que "Permitir
  ajustes restringidos" antes de poder activar el servicio.
