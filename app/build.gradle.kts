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
            if (propertiesFile.isFile && propertiesFile.length() > 0) {
                val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
                val alias = properties.getProperty("keyAlias")
                val keyPass = properties.getProperty("keyPassword")
                val storePass = properties.getProperty("storePassword")
                val configuredStore = if (isGITHUB_ACTION) File("/tmp/xed.keystore")
                    else properties.getProperty("storeFile")?.let(::File)
                if (!alias.isNullOrBlank() && !keyPass.isNullOrBlank() && !storePass.isNullOrBlank() && configuredStore?.isFile == true) {
                    keyAlias = alias
                    keyPassword = keyPass
                    storePassword = storePass
                    storeFile = configuredStore
                }
            }
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

// AGP configures every signing config for debug and unit-test tasks too. Defer
// the requirement until a release task is actually requested.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release", ignoreCase = true) }) {
        val isGithubAction = System.getenv("GITHUB_ACTIONS") == "true"
        val propertiesFile = File(
            if (isGithubAction) "/tmp/signing.properties"
            else System.getenv("SIGNING_PROPERTIES") ?: rootProject.file("signing.properties").path
        )
        require(propertiesFile.isFile && propertiesFile.length() > 0L) {
            "Release signing properties are required at ${propertiesFile.absolutePath}"
        }
        val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
        val storeFile = if (isGithubAction) File("/tmp/xed.keystore") else properties.getProperty("storeFile")?.let(::File)
        require(
            !properties.getProperty("keyAlias").isNullOrBlank() &&
                !properties.getProperty("keyPassword").isNullOrBlank() &&
                !properties.getProperty("storePassword").isNullOrBlank() && storeFile?.isFile == true
        ) {
            "Release signing properties must include keyAlias, keyPassword, storePassword, and an existing keystore"
        }
    }
}

dependencies {
    implementation(project(":core:main"))
}
