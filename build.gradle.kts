import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20-Beta2"
    kotlin("plugin.serialization") version "2.4.20-Beta2"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

version = "1.3.0-beta.7"

val javaTarget = providers.gradleProperty("javaTarget").getOrElse("25")
    .also { require(it == "21" || it == "25") { "javaTarget must be 21 or 25" } }

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    //KGui
    maven("https://jitpack.io")

    //PlaceholderApi
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

val pnLibraryVersion = "0.1.0-dev.3"

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.20-Beta2")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.20-Beta2")
    // Downloaded by the server from plugin.yml `libraries`, so they stay out of the jar (SQLite alone carries ~14 MB of natives).
    compileOnly("com.charleskorn.kaml:kaml:0.67.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("com.charleskorn.kaml:kaml:0.67.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    // The shared runtime plugin is installed by the pnLibrary bootstrap shaded into the jar; only its API is needed to compile.
    implementation("io.github.pnfolder:pnlibrary-bootstrap-bukkit:$pnLibraryVersion")
    compileOnly("io.github.pnfolder:pnlibrary-api:$pnLibraryVersion")
    compileOnly("io.github.pnfolder:pnlibrary-bukkit-api:$pnLibraryVersion")

    implementation("com.github.retrooper:packetevents-spigot:2.13.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(javaTarget.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaTarget))
    }
}

tasks {
    shadowJar {
        archiveFileName.set("pnClans-${project.version}-paper-mc1.21.11-java${javaTarget}.jar")
        // Gson ships with every Bukkit-based server.
        exclude("com/google/gson/**")
        // The README targets Paper, which already ships Adventure.
        exclude("net/kyori/**")
        // PacketEvents must be isolated when bundled to avoid conflicts with
        // another plugin's embedded or standalone PacketEvents installation.
        relocate("com.github.retrooper", "ua.inventorytype.pnclans.libs")
        relocate("io.github.retrooper", "ua.inventorytype.pnclans.libs")
        relocate("org.pnfolder.pnlibrary.bootstrap", "ua.inventorytype.pnclans.libs.pnbootstrap")
        relocate("org.pnfolder.pnlibrary.bukkit.bootstrap", "ua.inventorytype.pnclans.libs.pnbootstrap.bukkit")
        relocate("org.pnfolder.pnlibrary.console", "ua.inventorytype.pnclans.libs.pnbootstrap.console")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
        pluginJars.from(shadowJar.flatMap { it.archiveFile })
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
