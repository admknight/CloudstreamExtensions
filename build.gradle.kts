import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // Downgrade AGP to 8.2.2 for better compatibility with the Cloudstream Gradle plugin
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()
fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    
    android {
        // Use a consistent namespace for all plugins to avoid manifest merge issues
        namespace = "com.admknight.${project.name.lowercase().replace("[^a-zA-Z0-9]".toRegex(), "")}"
        
        compileSdkVersion(34)
        defaultConfig {
            minSdk = 21
            targetSdk = 34
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
            }
        }
    }

    // Apply Cloudstream plugin AFTER basic android config
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "admknight/CloudstreamExtensions")
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations
        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.google.code.gson:gson:2.11.0")
        implementation("org.json:json:20240303")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        
        // Critical base libraries
        implementation("androidx.annotation:annotation:1.7.0")
        implementation("org.mozilla:rhino:1.7.15")
        implementation("androidx.appcompat:androidx.appcompat:1.6.1")
    }
}

// Global build commands
tasks.register("buildAll") {
    group = "cloudstream"
    // Only depend on 'make' tasks of actual plugin subprojects
    dependsOn(subprojects.map { it.tasks.matching { t -> t.name == "make" } })
}

// Fix for duplicate clean task from root and subprojects
tasks.named("clean") {
    doFirst {
        subprojects.forEach { 
            it.tasks.matching { t -> t.name == "clean" && t != this }.all { enabled = false }
        }
    }
}
