plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")

    // Base de datos (se usa desde F1). Ya está configurada para comprobar que KSP funciona.
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
}
