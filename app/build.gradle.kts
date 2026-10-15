import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: CI passes these as environment variables; locally they can live
// in an untracked keystore.properties at the repo root. Without them the release
// APK is built unsigned (see README → "Signing").
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.reader().use(::load)
}
fun signingValue(key: String): String? =
    System.getenv("CHANNELSNAP_$key") ?: keystoreProps.getProperty(key)

android {
    namespace = "dev.chalicechore.channelsnap"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.chalicechore.channelsnap"
        minSdk = 26
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        val storeFilePath = signingValue("STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("STORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

    dependenciesInfo {
        // Sideloaded from GitHub: no Play-only dependency metadata blob.
        includeInApk = false
        includeInBundle = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "ChannelSnap-${output.versionName.get()}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
