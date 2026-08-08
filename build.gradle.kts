plugins {
    java
}

group = "tw.flashing"
version = "2.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("CityGenerator")
}
