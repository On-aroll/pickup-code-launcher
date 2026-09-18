import java.util.Properties

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties()
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.reader(Charsets.UTF_8).use(signingProperties::load)
}

plugins {
    id("com.android.application")
}

android {
    namespace = "cn.pickup.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.pickup.launcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 33
        versionName = "2.8.0"
    }

    if (signingPropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingProperties["storeFile"] as String)
                storePassword = signingProperties["storePassword"] as String
                keyAlias = signingProperties["keyAlias"] as String
                keyPassword = signingProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
