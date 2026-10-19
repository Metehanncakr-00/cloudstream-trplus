version = 1

dependencies {
    add("cloudstream", "com.lagradost.cloudstream3:cloudstream:pre-release")
}

cloudstream {
    authors = listOf("CloudStreamTR")
    language = "tr"
    description = "TR+ ONE: Üç gerçek sağlayıcıyla bağımsız birleşik katalog prototipi"
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Anime", "AnimeMovie")
}

// This is a standalone .cs3. Source files are compiled into ONE's own DEX, not loaded
// from separately installed extensions. Do not copy provider Plugin registrations.
val providerSources = tasks.register<Copy>("copyOneProviderSources") {
    listOf("HDFilmCehennemi", "SezonlukDizi", "AnimeciX").forEach { module ->
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
    sourceSets.getByName("main") {
        java.srcDir(providerSources.map { it.destinationDir })
    }
}
