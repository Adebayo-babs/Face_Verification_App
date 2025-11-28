plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.neurotecsdklibrary"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    kotlinOptions {
        jvmTarget = "11"
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "member-search-index.zip",
                "jquery-ui.overrides.css",
                "jquery/jquery-ui.min.css",
                "index-all.html",
                "**", "META-INF/**", "element-list", "legal/ASSEMBLY_EXCEPTION", "**/*.html", "**/*.png", "**/*.js", "**/*.css", "**/*.md"
            )

            jniLibs {
                useLegacyPackaging = true
                pickFirsts += listOf(
                    "**/libjnidispatch.so",
                    "lib/**/libNCore.so"
                )
            }
        }
    }


//    packaging {
//        resources.excludes.add("**")
//    }

}

dependencies {

    api(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.jar", "*.aar")
    )))

    implementation(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(project(":common"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3:1.2.1")

    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation(libs.androidx.room.runtime.v250)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    ksp(libs.androidx.room.compiler.v250)

//    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}