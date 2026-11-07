plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.postoffice.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.postoffice.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val quickjsHostNative: Configuration by configurations.creating

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.dokar3:quickjs-kt:1.0.15")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    quickjsHostNative("io.github.dokar3:quickjs-kt-jvm:1.0.15") { isTransitive = false }
}

// JVM unit tests can't use the Android .so files, so extract the desktop QuickJS
// library from the JVM artifact and point quickjs-kt's loader at it.
val hostNativeDir = layout.buildDirectory.dir("quickjs-host-native")
val hostLib: Pair<String, String> = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").contains("aarch64")
    when {
        os.contains("win") -> "windows_x64" to "libquickjs.dll"
        os.contains("mac") -> (if (arm) "macos_aarch64" else "macos_x64") to "libquickjs.dylib"
        else -> (if (arm) "linux_aarch64" else "linux_x64") to "libquickjs.so"
    }
}
val extractQuickJsHostNative by tasks.registering(Copy::class) {
    from({ zipTree(quickjsHostNative.singleFile) }) { include("jni/${hostLib.first}/${hostLib.second}") }
    into(hostNativeDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(extractQuickJsHostNative)
    systemProperty("com.dokar.quickjs.library.path", hostNativeDir.get().dir("jni/${hostLib.first}").asFile.absolutePath)
    systemProperty("com.dokar.quickjs.library.name", hostLib.second)
}
