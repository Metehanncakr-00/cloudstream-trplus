import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }

    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:81b1d424d2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/Metehanncakr-00/cloudstream-trplus")
    }

    android {
        namespace = "com.cloudstream.tr.${project.name.lowercase()}"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        sourceSets {
            getByName("main") {
                java.srcDir(rootProject.file("core/src/main/kotlin"))
            }
            if (project.name == "AnimeciX") {
                getByName("test") {
                    java.srcDir(rootProject.file("core/src/test/kotlin"))
                }
            }
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

        tasks.withType<org.gradle.api.tasks.testing.Test> {
            timeout.set(java.time.Duration.ofMinutes(1))
            maxParallelForks = 1
            testLogging {
                events("passed", "skipped", "failed")
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        implementation("com.github.recloudstream.cloudstream:library:v4.8.0")
        implementation(kotlin("stdlib"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        implementation("com.github.teamnewpipe:NewPipeExtractor:v0.25.2")

        testImplementation("junit:junit:4.13.2")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
