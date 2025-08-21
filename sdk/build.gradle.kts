plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
}

android {
    namespace = "zwdroid.mcp.sdk"
    compileSdk = 36

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
    buildFeatures {
        aidl = true
    }
}

dependencies {
    // MCP SDK
    implementation(libs.mcp.kotlin.sdk)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.findProperty("GROUP_ID")?.toString() ?: "com.github.AnswerZhao"
                artifactId = project.findProperty("ARTIFACT_ID")?.toString() ?: "android-mcp-sdk"
                version = project.findProperty("VERSION_NAME")?.toString() ?: "0.0.1"
                
                pom {
                    name.set("Android MCP SDK")
                    description.set("Model Context Protocol implementation for Android using Binder IPC")
                    url.set("https://github.com/AnswerZhao/android-mcp-sdk")
                    
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set(project.findProperty("DEVELOPER_ID")?.toString() ?: "AnswerZhao")
                            name.set(project.findProperty("DEVELOPER_NAME")?.toString() ?: "AnswerZhao")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/AnswerZhao/android-mcp-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com:AnswerZhao/android-mcp-sdk.git")
                        url.set("https://github.com/AnswerZhao/android-mcp-sdk")
                    }
                }
            }
        }
    }
}