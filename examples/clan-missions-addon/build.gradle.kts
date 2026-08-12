import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20-Beta2"
}

val pnClansVersion = providers.gradleProperty("pnClansVersion").getOrElse("1.2.0-java25")
val defaultJavaTarget = if (pnClansVersion.contains("-java21")) "21" else "25"
val javaTarget = providers.gradleProperty("javaTarget").getOrElse(defaultJavaTarget)
    .also { require(it == "21" || it == "25") { "javaTarget must be 21 or 25" } }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("../../build/libs/pnClans-$pnClansVersion-all.jar"))
}

kotlin {
    jvmToolchain(javaTarget.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaTarget))
    }
}
