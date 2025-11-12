plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.farmmonitor.agriai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.farmmonitor.agriai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add this to enable multiDex if needed
        multiDexEnabled = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
    }
}

dependencies {
    // Core Android
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // Firebase BOM (keeps versions in sync)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase Products
    implementation("com.google.firebase:firebase-database")    // Realtime Database
    implementation("com.google.firebase:firebase-auth")        // Authentication
    implementation("com.google.firebase:firebase-firestore")   // Firestore
    implementation("com.google.firebase:firebase-analytics")   // Analytics

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.firebase.perf)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}