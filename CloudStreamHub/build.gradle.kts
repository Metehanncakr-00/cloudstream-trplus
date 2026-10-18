version = 42

dependencies {
    // Official host SDK: compile-only; host player/action classes stay in CloudStream.
    add("cloudstream", "com.lagradost.cloudstream3:cloudstream:pre-release")
}

cloudstream {
    authors     = listOf("CloudStreamTR", "Emre-Kahveci")
    language    = "tr"
    description = "CloudStreamHub Aggregator - TMDB Keşif & CloudStream Sağlayıcılarını Birleştiren Eklenti"
    status      = 1
    tvTypes     = listOf("Movie", "TvSeries", "Anime", "AnimeMovie", "Cartoon", "Documentary")
    iconUrl     = "https://raw.githubusercontent.com/Emre-Kahveci/CloudStreamHub/main/assets/icon.png"
}

// Project dependencies compile against provider modules but do not place their classes
// in CloudStreamHub's standalone .cs3. Compile the provider sources into the hub artifact.
val bundledProviderModules = listOf(
    "FilmMakinesi",
    "HDFilmCehennemi",
    "SinemaCX",
    "SezonlukDizi",
    "StarTV",
    "DiziIzle",
    "KultFilmler",
    "Dizilla",
    "DiziYou",
    "HDFilmDelisi",
    "JetFilmIzle",
    "DiziKorea",
    "YesilCamTv",
    "FullHDFilmizlesene",
    "AnimeciX",
    "Animeler",
    "BelgeselX",
    "DramaDizilerim",
    "WebDramaTurkey",
    "SetFilmIzle",
    "Dizigecesi",
    "RareFilmm",
    "BlenderOpenMovies"
)

val copyBundledProviderSources = tasks.register<Copy>("copyBundledProviderSources") {
    bundledProviderModules.forEach { moduleName ->
        into(moduleName) {
            from(project(":$moduleName").fileTree("src/main/kotlin") {
                exclude("**/*Plugin.kt")
            })
        }
    }
    into(layout.buildDirectory.dir("generated/hubProviderSources"))
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(copyBundledProviderSources)
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("main") {
            java.srcDir(copyBundledProviderSources.map { it.destinationDir })
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
