plugins {
    id("buildlogic.java-library-conventions")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    implementation(project(":nms:interface")) {
        exclude(module = "spigot-api")
    }
    paperweight.paperDevBundle("26.2.build.60-beta")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.withType<JavaCompile> {
    options.release = 25
}
