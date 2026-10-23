import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}


android {
    namespace = "com.rk.application"
    compileSdk = 37


    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    signingConfigs {
        create("release") {
            val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"
            
            val propertiesFilePath = if (isGITHUB_ACTION) {
                "/tmp/signing.properties"
            } else {
                System.getenv("SIGNING_PROPERTIES") ?: rootProject.file("signing.properties").path
            }
            
            val propertiesFile = File(propertiesFilePath)
            require(propertiesFile.isFile && propertiesFile.length() > 0) {
                "Release signing properties are required at $propertiesFilePath"
            }
            val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
            val alias = properties.getProperty("keyAlias")
            val keyPass = properties.getProperty("keyPassword")
            val storePass = properties.getProperty("storePassword")
            val configuredStore = if (isGITHUB_ACTION) File("/tmp/xed.keystore")
                else properties.getProperty("storeFile")?.let(::File)
            require(!alias.isNullOrBlank() && !keyPass.isNullOrBlank() && !storePass.isNullOrBlank() && configuredStore?.isFile == true) {
                "Release signing properties must include keyAlias, keyPassword, storePassword, and an existing keystore"
            }
            keyAlias = alias!!
            keyPassword = keyPass!!
            storePassword = storePass!!
            storeFile = configuredStore!!
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }
    
    
    buildTypes {
        release{
            isMinifyEnabled = true
            isCrunchPngs = false
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            resValue("string","app_name","Wolfi Terminal")
        }
        debug{
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string","app_name","WolfiTerminal-Debug")
        }
    }

    
    defaultConfig {
        applicationId = "com.wolfi.terminal"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.3.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }


    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:main"))
}
