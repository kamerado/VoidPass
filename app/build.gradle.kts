plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kamerado.voidpass"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kamerado.voidpass"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // TODO: CRITICAL for a password manager: prevent screenshots & recents previews.
    // We'll set FLAG_SECURE in code instead — more flexible.
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.5.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // Material Icons — the Icons.Default.Lock, Fingerprint etc.
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    // ViewModel integration for Compose — the viewModel() function
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // While you're here, make sure these are present too —
    // they're needed for collectAsState() and AnimatedContent:
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.animation:animation:1.7.6")
    implementation(libs.androidx.ui)
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.appcompat:appcompat:1.7.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}