// AGP 9.5.2 trae Kotlin 2.2.10 de fábrica; esta línea lo sube a 2.4.21 (mecanismo de las notas
// de AGP 9.0). Mantenerla igual a la versión del plugin de Compose en settings.gradle.kts.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

// Todos los plugins se declaran acá para que compartan el mismo cargador de clases.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("com.google.devtools.ksp") apply false
    id("androidx.room") apply false
}
