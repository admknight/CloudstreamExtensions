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
    // ... rest of config ...
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