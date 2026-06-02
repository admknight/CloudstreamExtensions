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
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
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
        compileSdkVersion(34)
        buildFeatures.buildConfig = true
        defaultConfig {
            minSdk = 21
            targetSdk = 34
            
            buildConfigField("String", "TMDB_API", "\"1865f43a0549ca50d341dd9ab8b29f49\"")
            buildConfigField("String", "TMDB_API_KEY", "\"1865f43a0549ca50d341dd9ab8b29f49\"")
            buildConfigField("String", "TMDB_KEY", "\"1865f43a0549ca50d341dd9ab8b29f49\"")
            buildConfigField("String", "TMDBIMAGEBASEURL", "\"https://image.tmdb.org/t/p/w500\"")
            buildConfigField("String", "SIMKL_CLIENT_ID", "\"\"")
            buildConfigField("String", "SIMKL_API", "\"\"")
            buildConfigField("String", "ANICHI_API", "\"https://api.allmanga.to/graphql\"")
            buildConfigField("String", "ANICHI_APP", "\"https://allmanga.to\"")
            buildConfigField("String", "ANICHI_ENDPOINT", "\"https://api.allmanga.to\"")
            buildConfigField("String", "ZSHOW_API", "\"https://zshow.me\"")
            buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"https://third.superstream.me\"")
            buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"https://fourth.superstream.me\"")
            buildConfigField("String", "NuvFeb", "\"https://feb.superstream.me\"")
            buildConfigField("String", "KissKh", "\"https://kisskh.me/api/DramaList/Episode/\"")
            buildConfigField("String", "KisskhSub", "\"https://kisskh.me/api/Sub/\"")
            buildConfigField("String", "SuperToken", "\"\"")
            buildConfigField("String", "Su_sports", "\"\"")
            buildConfigField("String", "JapanIPTV", "\"\"")
            buildConfigField("String", "PirateIPTV", "\"\"")
            buildConfigField("String", "SonyIPTV", "\"\"")
            buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"\"")
            buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"\"")
            buildConfigField("String", "YFXENC", "\"\"")
            buildConfigField("String", "YFXDEC", "\"\"")
            buildConfigField("String", "TMDB_KEY", "\"1865f43a0549ca50d341dd9ab8b29f49\"")
            buildConfigField("String", "CC_COOKIE", "\"\"")
            buildConfigField("String", "FanCode_API", "\"\"")
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
        }
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
        implementation("androidx.annotation:annotation:1.7.0")
        implementation("org.mozilla:rhino:1.7.15")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
    }
}

tasks.register("buildAll") {
    group = "cloudstream"
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.matching { it.name == "make" })
    }
}
