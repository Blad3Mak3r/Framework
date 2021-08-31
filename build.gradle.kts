import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.language.jvm.tasks.ProcessResources
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.0.0"

    idea
}

group = "tv.blademaker"
val versionObj = Version(0, 15, 9)
version = versionObj.build()

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://jitpack.io")
}

val jdaVersion = "4.3.0_310"
val coroutinesVersion = "1.5.1"
val logbackVersion = "1.2.5"
val sentryVersion = "5.1.2"

dependencies {
    implementation(kotlin("stdlib", "1.5.30"))
    implementation(kotlin("reflect", "1.5.30"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")

    //Reflections
    implementation("org.reflections:reflections:0.9.12")

    //JDA
    implementation("net.dv8tion:JDA:$jdaVersion") { exclude(module = "opus-java") }
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.sentry:sentry:$sentryVersion")
    implementation("com.github.minndevelopment:jda-ktx:d460e2a")

    //HTTP Clients
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
}

tasks {
    named<ShadowJar>("shadowJar") {
        println("Building version ${project.version}")
        manifest {
            attributes["Main-Class"] = "dev.killjoy.Launcher"
        }
        archiveBaseName.set("Framework")
        archiveClassifier.set("")
        archiveVersion.set("")
        dependsOn("processResources")
    }

    named<ProcessResources>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        val tokens = mapOf(
            "project.version"       to project.version,
            "project.revision"      to (gitRevision() ?: "NO COMMIT"),
            "project.build_number"  to (getBuildCI() ?: "NO BUILD NUMBER")
        )

        from("src/main/resources") {
            include("app.properties")
            filter<ReplaceTokens>("tokens" to tokens)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
}

class Version(
    private val major: Int,
    private val minor: Int,
    private val patch: Int? = null
) {

    private fun getVersion(): String {
        return if (patch == null) "$major.$minor"
        else "$major.$minor.$patch"
    }

    fun build(): String = getVersion()
}

fun getBuildCI(): String? {
    return System.getenv("BUILD_NUMBER") ?: System.getProperty("BUILD_NUMBER") ?: null
}

fun gitRevision(): String? {
    return try {
        val gitVersion = org.apache.commons.io.output.ByteArrayOutputStream()
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = gitVersion
        }
        gitVersion.toString(Charsets.UTF_8).trim()
    } catch (e: java.lang.Exception) {
        return System.getenv("GITHUB_SHA")?.trim() ?: return null
    }
}