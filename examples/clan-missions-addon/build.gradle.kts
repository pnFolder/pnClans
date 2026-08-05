plugins {
    kotlin("jvm") version "2.4.20-Beta2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly(files("../../build/libs/pnClans-1.0.0-all.jar"))
}

kotlin {
    jvmToolchain(25)
}
