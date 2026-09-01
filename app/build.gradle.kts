import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.rommapp.rommp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rommapp.rommp"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    base {
        archivesName = "rommp-${defaultConfig.versionName}"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
            // v3 carries the proof-of-rotation block, so the signing key can be
            // rotated later without orphaning installs. v1 is unnecessary below
            // no supported API level, since minSdk is 26.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        baseline = file("lint-baseline.xml")
        error += listOf(
            "MissingTranslation",
            "ExtraTranslation",
            "StringFormatMatches",
            "StringFormatInvalid",
            "ImpliedQuantity",
            "Untranslatable",
        )
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests {
            // Robolectric supplies the real android.net.Uri and MimeTypeMap. Stub
            // return values stay off so a test can never pass against a null default.
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.bundles.network)
    ksp(libs.moshi.kotlin)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Media3
    implementation(libs.bundles.media3)

    // QR generation
    implementation(libs.zxing.core)

    // Color extraction
    implementation(libs.androidx.palette)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
}

val verifyReleaseSigningConfig = tasks.register("verifyReleaseSigningConfig") {
    doLast {
        if (!keystorePropertiesFile.exists()) {
            throw GradleException(
                "keystore.properties is missing. A release build would fall back to the debug " +
                    "signing key, and an APK signed with that key cannot install over an " +
                    "existing release. Restore the file before building a release.",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseSigningConfig)
}
