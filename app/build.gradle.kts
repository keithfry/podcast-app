import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.frybynite.podlore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.frybynite.podlore"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GROQ_API_KEY", "\"${localProperties["GROQ_API_KEY"] ?: ""}\"")
        buildConfigField("String", "HF_TOKEN", "\"${localProperties["HF_TOKEN"] ?: ""}\"")
        buildConfigField("String", "MODAL_TTS_URL", "\"${localProperties["MODAL_TTS_URL"] ?: ""}\"")
        buildConfigField("String", "CAST_APP_ID", "\"${localProperties["CAST_APP_ID"] ?: ""}\"")
        buildConfigField("String", "PODCAST_INDEX_KEY", "\"${localProperties["PODCAST_INDEX_KEY"] ?: ""}\"")
        buildConfigField("String", "PODCAST_INDEX_SECRET", "\"${localProperties["PODCAST_INDEX_SECRET"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.jvmArgs("-Dnet.bytebuddy.experimental=true") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.cast)
    implementation(libs.mediarouter)
    implementation(libs.play.services.cast.framework)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.browser)
    implementation(libs.appcompat)
    implementation(libs.mediapipe.tasks.genai)
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2") // AiCore path (kept, not in active pipeline)
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation(libs.jsoup)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kxml2)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
