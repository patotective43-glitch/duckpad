import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La clave de subida vive fuera del repositorio. Sin el archivo, la versión de publicación se compila sin firmar.
val signingFile = rootProject.file(System.getenv("DUCKPAD_SIGNING") ?: "../../duckpad-firma/keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

android {
    // Los textos pesan poco: con todos los idiomas en el APK, el idioma por app de Android 13 y el modo demo
    // funcionan sin descargar nada de Play.
    bundle {
        language { enableSplit = false }
    }
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    namespace = "dev.patotective.duckpad"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.patotective.duckpad"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("upload") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
        // Igual que la de publicación pero con la clave de depuración: se instala encima de la de pruebas sin perder ajustes.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
