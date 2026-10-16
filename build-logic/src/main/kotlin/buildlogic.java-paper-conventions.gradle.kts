plugins {
    id("buildlogic.java-library-conventions")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    val paperApiVersion = providers.gradleProperty("shopchestPaperApiVersion").get()
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion") {
        version { strictly(paperApiVersion) }
    }
}
