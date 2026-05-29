import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
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
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "admknight/CloudstreamExtensions")
    }

    android {
        namespace = "com.admknight.${project.name.lowercase().replace("[^a-zA-Z0-9]".toRegex(), "")}"

        // Ensure local.properties exists for CI builds
        val localPropertiesFile = rootProject.file("local.properties")
        if (!localPropertiesFile.exists()) {
            localPropertiesFile.writeText("sdk.dir=/home/runner/android-sdk")
        }

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        cloudstream("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("org.json:json:20240303")
        // IMPORTANT: Do not bump Jackson above 2.13.1
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }
}

task("make") {
    group = "cloudstream"
    doLast {
        subprojects.forEach { sub ->
            if (sub.plugins.hasPlugin("com.lagradost.cloudstream3.gradle")) {
                sub.tasks.findByName("make")?.let { task ->
                    println("Building ${sub.name}...")
                    task.actions.forEach { it.execute(task) }
                }
            }
        }
    }
}

task("makePluginsJson") {
    group = "cloudstream"
    doLast {
        subprojects.forEach { sub ->
            if (sub.plugins.hasPlugin("com.lagradost.cloudstream3.gradle")) {
                sub.tasks.findByName("makePluginsJson")?.let { task ->
                    task.actions.forEach { it.execute(task) }
                }
            }
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}