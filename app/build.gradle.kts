plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firebase يُفعَّل فقط عند وجود app/google-services.json (حتى لا يفشل البناء بدونه)
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.klencod.dardasha"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.klencod.dardasha"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.22"
    }

    // توقيع ثابت: يُستخدم مفتاح release إن وُجد في أسرار GitHub، وإلا يرجع لمفتاح debug
    val ksPath: String? = System.getenv("KEYSTORE_FILE")
    val hasKs = ksPath != null && file(ksPath).exists()
    signingConfigs {
        if (hasKs) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasKs) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5") // يحل خطأ lint: InvalidFragmentVersionForActivityResult
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    // جسر واتساب (whatsmeow) يُبنى بـ gomobile في GitHub Actions إلى app/libs/wabridge.aar
    implementation(fileTree("libs") { include("*.aar") })

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

// يحاول بناء جسر واتساب (wabridge.aar) تلقائياً إن لم يوجد. فشله لا يوقف البناء: التطبيق يعمل بدون ميزة الربط.
val buildWaBridge by tasks.registering(Exec::class) {
    onlyIf { !file("libs/wabridge.aar").exists() && file("../wabridge/build.sh").exists() }
    workingDir = file("../wabridge")
    commandLine("bash", "build.sh")
    isIgnoreExitValue = true
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(buildWaBridge) }
