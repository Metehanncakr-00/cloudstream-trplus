rootProject.name = "CloudStreamTR"

// Auto-discovery of provider modules
val ignoredDirs = listOf(
    "tools",
    "docs",
    "config",
    "legacy",
    ".github",
    "gradle",
    "reports",
    "site",
    "core"
)

val disabled = listOf(
    "CizgiMax",
    "DDizi",
    "DiziLife",
    "DiziMom",
    "DiziPal",
    "FilmHane",
    "FilmModu",
    "Sinewix",
    "TurkAnime"
)

File(rootDir, ".").eachDir { dir ->
    if (!ignoredDirs.contains(dir.name) && !disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
