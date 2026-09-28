plugins {
    java
}

group = "me.merelyme"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("."))
        resources.include("plugin.yml")
    }
}

tasks.jar {
    archiveFileName.set("MerelyOrderEnchants.jar")
}
