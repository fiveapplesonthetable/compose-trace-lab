plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.demo.uitracing"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.demo.uitracing"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { animationsDisabled = true }
}

dependencies {
    val composeTraceFromSource = providers.gradleProperty("composeTraceFromSource").isPresent
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    if (composeTraceFromSource) {
        implementation(project(":compose:runtime:runtime"))
        implementation(project(":compose:ui:ui"))
        implementation(project(":compose:animation:animation-core"))
        implementation(project(":compose:foundation:foundation"))
        implementation(project(":compose:ui:ui-tracing-perfetto"))
    } else {
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.foundation:foundation")
    }
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Optional artifacts built from the Perfetto and AndroidX CLs in the supplied guide.
    // Place perfetto-datasource.aar and ui-tracing-perfetto.aar in app/libs to enable capture.
    if (file("libs/perfetto-datasource.aar").exists()) implementation(mapOf("name" to "perfetto-datasource", "ext" to "aar"))
    if (!composeTraceFromSource && providers.gradleProperty("enableUiHierarchyAar").isPresent && file("libs/ui-tracing-perfetto.aar").exists()) {
        implementation(mapOf("name" to "ui-tracing-perfetto", "ext" to "aar"))
    }
}
