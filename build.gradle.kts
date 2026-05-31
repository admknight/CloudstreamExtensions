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
        classpath("com.android.tools.build:gradle:8.7.3")
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

// Aggregator tasks defined at root
val buildAll = tasks.register("buildAll") { group = "cloudstream" }
val generatePluginsJson = tasks.register("generatePluginsJson") { group = "cloudstream" }

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "admknight/CloudstreamExtensions")
    }

    android {
        namespace = "com.admknight.${project.name.lowercase().replace("[^a-zA-Z0-9]".toRegex(), "")}"
        val localPropertiesFile = rootProject.file("local.properties")
        if (!localPropertiesFile.exists()) { localPropertiesFile.writeText("sdk.dir=/home/runner/android-sdk") }

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
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
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
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }

    // Link subproject tasks to root aggregator tasks correctly
    afterEvaluate {
        if (plugins.hasPlugin("com.lagradost.cloudstream3.gradle")) {
            buildAll.configure { dependsOn(tasks.named("make")) }
            generatePluginsJson.configure { dependsOn(tasks.named("makePluginsJson")) }
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
