plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.apuntescroll"

    // Se compila contra la API 37.1 porque las versiones actuales de Compose, core, lifecycle y
    // navigation lo exigen. No cambia cómo se comporta la app: eso lo fija targetSdk.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.apuntescroll"
        minSdk = 26
        // Google Play exige API 36 desde el 31/8/2026. Se fija a mano porque AGP 9, si falta,
        // usa el compileSdk.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // targetSdk 36 es a propósito (lo que pide Google Play hoy). Subir a 37 antes del
        // requisito de 2027 y sacar esta línea.
        disable += "OldTargetApi"
    }
}

room {
    // Room guarda acá el esquema de cada versión de la base, para las migraciones.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.13.0")

    // Base de datos
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Procesamiento de PDFs en segundo plano (PLAN.md, sección 3)
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        // bcpkix trae un X509TrustManager que acepta cualquier certificado, y Google Play
        // rechaza las apps que lo incluyen. PdfBox solo lo usa para PDFs cifrados con
        // certificado (muy raros en apuntes), y los rechaza antes de tocar esas clases: quedan
        // en ERROR con un mensaje claro (ProcesadorDocumentos.esCifradoConCertificado).
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
    }
    // OCR: variante de Google Play services (modelo descargado por el sistema, APK más chico).
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
