# ApunteScroll

App Android que convierte tus PDFs de estudio en un feed para scrollear, con el estilo de
Instagram y X. Cuando abres una de esas redes, ApunteScroll la tapa y te muestra tus apuntes.

- Extrae el texto de los PDFs; las páginas escaneadas pasan por OCR en el teléfono.
- Corta el texto en tarjetas cortas, organizadas por materia y sección.
- "Me gusta" = "ya lo sé": repaso espaciado, preguntas en el feed, racha y estadísticas.
- Todo queda en el teléfono: sin cuentas ni servidor.

Estado: **F0 (base del proyecto)**. La app abre con su barra de navegación y la pantalla de
Ajustes; el resto llega en las fases siguientes. Ver [PLAN.md](PLAN.md).

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
├── datos/               ajustes (y desde F1, la base de datos Room)
└── ui/                  MainActivity, navegación, pantallas y tema
app/src/test/            pruebas locales (JUnit)
```

Más adelante se suman `pdf/`, `dominio/` e `intercepcion/` (PLAN.md, sección 9).
