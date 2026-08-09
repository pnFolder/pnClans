plugins {
    kotlin("jvm") version "2.4.20-Beta2"
    kotlin("plugin.serialization") version "2.4.20-Beta2"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

version = "1.0.6"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    //KGui
    maven("https://jitpack.io")

    //PlaceholderApi
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.charleskorn.kaml:kaml:0.67.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("com.github.retrooper:packetevents-spigot:2.13.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("com.github.maquqdev:KGui:v1.0.1")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        archiveClassifier.set("all")
        relocate("org.bstats", "ua.inventorytype.pnclans.libs.bstats")
        // PacketEvents must be isolated when bundled to avoid conflicts with
        // another plugin's embedded or standalone PacketEvents installation.
        relocate("com.github.retrooper", "ua.inventorytype.pnclans.libs")
        relocate("io.github.retrooper", "ua.inventorytype.pnclans.libs")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
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
