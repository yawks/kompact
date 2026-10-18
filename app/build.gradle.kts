plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kompact"
    compileSdk = 37

    val keystorePath = System.getenv("KS_PATH")
    val keystorePassword = System.getenv("KS_PASS")
    val uploadKeyAlias = System.getenv("KS_ALIAS")
    val uploadKeyPassword = System.getenv("KEY_PASS")
    val hasUploadKey = listOf(keystorePath, keystorePassword, uploadKeyAlias, uploadKeyPassword)
        .all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.kompact"
        minSdk = 24
        targetSdk = 37
        versionCode = 31
        versionName = "2.0.9"
        // NDK r28+: allineamento a 16 kB delle librerie native (warning Play Console)
        ndkVersion = "28.2.13676358"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
            }
            storePassword = keystorePassword
            uploadKeyAlias?.let { keyAlias = it }
            uploadKeyPassword?.let { keyPassword = it }
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasUploadKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 16 kB: .so uncompressed e page-aligned nell'APK (extractNativeLibs=false)
            useLegacyPackaging = false
            // awxkee avif-coder and jxl-coder both ship native .so files;
            // pick the first occurrence to avoid duplicate-library build errors.
            pickFirsts += "**/*.so"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9 built-in Kotlin: configura il JVM target via l'estensione top-level kotlin{}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AGP 9.4 built-in Kotlin è 2.2.10 (legge metadata fino a 2.3.0).
    // Forza kotlin-stdlib a 2.3.21 per compatibilità — le librerie moderne
    // (Coil 3.6, Compose 1.12) pulled stdlib 2.4.x che il compilatore
    // built-in non riesce a leggere.
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:2.3.21")
        }
    }

    // --- AndroidX Core (1.19.1 richiede compileSdk 37) ---
    implementation("androidx.core:core-ktx:1.19.1")

    // --- Lifecycle ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // --- Activity ---
    implementation("androidx.activity:activity-compose:1.13.0")

    // --- Jetpack Compose (BOM 2026.09.00 → Compose 1.12.x, richiede AGP 9.1+ / compileSdk 37) ---
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Material Views ---
    implementation("com.google.android.material:material:1.13.0")
    // Nota: material 1.13.x è l'ultima stable; le alpha 1.14.x sono instabili

    // --- Coil 3 (image loading, latest 3.6.x) ---
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")

    // --- Document / EXIF / DataStore ---
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    // datastore 1.2.1: ricompila libdatastore_shared_counter.so con GNU_RELRO
    // allineato a 16 kB (in 1.2.0 il RELRO non è un suffisso del suo LOAD e il
    // suo end non è allineato a 16 kB → warning Play Console su dispositivi 16 kB)
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // Nota: datastore 1.2.1 è l'ultima stable; documentfile 1.1.0 è l'ultima release

    // --- Open-source native codecs (Apache 2.0) ---
    // avif-coder wraps libheif/libavif for HEIF/HEIC/AVIF encode+decode
    // jxl-coder wraps libjxl for JPEG XL encode+decode
    // Maven Central versions, compiled with Kotlin 2.3.x metadata.
    implementation("io.github.awxkee:avif-coder:3.0.0")
    implementation("io.github.awxkee:jxl-coder:2.6.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
