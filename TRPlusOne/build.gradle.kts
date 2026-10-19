version = 4

dependencies {
    add("cloudstream", "com.lagradost.cloudstream3:cloudstream:pre-release")
}

cloudstream {
    authors = listOf("CloudStreamTR")
    language = "tr"
    description = "TR+ ONE v4: Çoklu sağlayıcı arama birleştirme, DiziYou oynatma onarımı ve tanılama ekranı"
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Anime", "AnimeMovie")
}

// This is a standalone .cs3. Source files are compiled into ONE's own DEX, not loaded
// from separately installed extensions. Do not copy provider Plugin registrations.
val providerSources = tasks.register<Copy>("copyOneProviderSources") {
    listOf(
        "HDFilmCehennemi",
        "SezonlukDizi",
        "AnimeciX",
        "FilmMakinesi",
        "SinemaCX",
        "FullHDFilmizlesene",
        "Dizilla",
        "DiziYou",
        "Animeler"
    ).forEach { module ->
        from(project(":$module").fileTree("src/main/kotlin") {
            exclude("**/*Plugin.kt")
        })
    }
    into(layout.buildDirectory.dir("generated/oneProviderSources"))
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn(providerSources) }

android {
    namespace = "com.cloudstream.tr.one"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("main") {
        java.srcDir(providerSources.map { it.destinationDir })
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
