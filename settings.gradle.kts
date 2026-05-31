plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "CloudstreamExtensions"

// Only scan category folders to avoid including the root itself as a subproject
val categoryDirs = listOf("Anime", "Bollywood", "International", "LiveTV", "Tools")

categoryDirs.forEach { category ->
    val categoryDir = File(rootDir, category)
    if (categoryDir.exists() && categoryDir.isDirectory) {
        categoryDir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            if (File(sub, "build.gradle.kts").exists()) {
                val projectName = sub.name
                include(":$projectName")
                project(":$projectName").projectDir = sub
            }
        }
    }
}
