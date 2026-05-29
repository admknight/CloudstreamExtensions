plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "CloudstreamExtensions"

val searchDirs = listOf(".", "Anime", "Bollywood", "International", "LiveTV", "Tools")

searchDirs.forEach { parent ->
    val parentDir = File(rootDir, parent)
    if (parentDir.exists() && parentDir.isDirectory) {
        parentDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (File(dir, "build.gradle.kts").exists()) {
                val projectName = dir.name
                println("Including project: :$projectName from ${dir.absolutePath}")
                include(":$projectName")
                project(":$projectName").projectDir = dir
            }
        }
    }
}
