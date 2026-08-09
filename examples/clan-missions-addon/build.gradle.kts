plugins {
    kotlin("jvm") version "2.4.20-Beta2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    val pnClansVersion = providers.gradleProperty("pnClansVersion").getOrElse("1.0.6")
    compileOnly(files("../../build/libs/pnClans-$pnClansVersion-all.jar"))
}

kotlin {
    jvmToolchain(25)
}
