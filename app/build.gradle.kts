plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.aipodcast"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aipodcast"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Guardian API key configuration
        buildConfigField("String", "GUARDIAN_API_KEY", "\"32889b00-8204-457c-86a0-2b9dd79a5c1d\"")
        // OpenAI API key configuration
        buildConfigField("String", "OPENAI_API_KEY", "\"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\"")
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
    
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Android core dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.11.0")
    
    // Chrome Custom Tabs
    implementation("androidx.browser:browser:1.7.0")
    
    // HTTP and JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    
    // Media and ExoPlayer dependencies
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.mediarouter:mediarouter:1.6.0")
    
    // ExoPlayer dependencies
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-dash:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-rtsp:2.19.1")
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")
    
    // OpenAI API dependencies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.androidx.activity)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}