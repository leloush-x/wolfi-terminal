import java.util.Properties
import org.gradle.api.GradleException

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
            // Use the task-time validation below. AGP eagerly configures this
            // signing config for debug and test tasks as well.
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
tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) || name.contains("BundleRelease", ignoreCase = true)) {
        doFirst {
        val isGithubAction = System.getenv("GITHUB_ACTIONS") == "true"
        val propertiesFile = File(
            if (isGithubAction) "/tmp/signing.properties"
            else System.getenv("SIGNING_PROPERTIES") ?: rootProject.file("signing.properties").path
        )
        if (!propertiesFile.isFile || propertiesFile.length() == 0L) {
            throw GradleException(
            "Release signing properties are required at ${propertiesFile.absolutePath}"
            )
        }
        val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
        val storeFile = if (isGithubAction) File("/tmp/xed.keystore") else properties.getProperty("storeFile")?.let(::File)
        if (!(
            !properties.getProperty("keyAlias").isNullOrBlank() &&
                !properties.getProperty("keyPassword").isNullOrBlank() &&
                !properties.getProperty("storePassword").isNullOrBlank() && storeFile?.isFile == true
        )) {
            throw GradleException(
                "Release signing properties must include keyAlias, keyPassword, storePassword, and an existing keystore"
            )
        }
        }
    }
}

dependencies {
    implementation(project(":core:main"))
}
