import buildlogic.VerifyGeneratedReleaseMetadata
import buildlogic.VerifyShopChestReleaseMetadata

plugins {
    id("buildlogic.java-paper-conventions")
    id("com.gradleup.shadow") version "9.0.0-rc1"
}

val releaseVersion = providers.gradleProperty("shopchestVersion").get()
val targetJavaVersion = providers.gradleProperty("shopchestJavaVersion").get()
val targetPaperVersion = providers.gradleProperty("shopchestPaperVersion").get()
val stablePaperBuild = providers.gradleProperty("shopchestPaperBuild").get()
val stablePaperChannel = providers.gradleProperty("shopchestPaperChannel").get()
val compiledPaperApiVersion = providers.gradleProperty("shopchestPaperApiVersion").get()
val releaseBuildNumber = providers.gradleProperty("shopchestBuild").get().padStart(3, '0')
val repositoryCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

repositories {
    mavenLocal {
        content {
            includeGroup("de.epiceric")
        }
    }
    // Jitpack (Vault, uSkyBlock, GriefPrevention, PlotSquared v4, Towny)
    maven {
        url = uri("https://jitpack.io")
    }
    // CodeMc repo (AuthMe, ASkyBlock, BentoBox, WorldGuardWrapper)
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    // EngineHub repo (WorldEdit, WorldGuard from WorldGuardWrapper)
    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }
    // Paper Repo (Adventure-MiniMessage from PlotSquared v6)
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

configurations.configureEach {
    exclude(group = "org.bukkit", module = "bukkit")
    exclude(group = "org.spigotmc", module = "spigot-api")
}

tasks.shadowJar {
    archiveFileName.set("1MB-ShopChest-v${releaseVersion}-${releaseBuildNumber}-j${targetJavaVersion}-${targetPaperVersion}.jar")
    dependencies {
        include(dependency("org.codemc.worldguardwrapper:worldguardwrapper"))
        include(dependency("com.zaxxer:HikariCP"))
    }
    relocate("org.codemc.worldguardwrapper", "de.epiceric.shopchest.dependencies.worldguardwrapper")
    relocate("com.zaxxer.hikari", "de.epiceric.shopchest.dependencies.hikari")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

dependencies {
    // Shaded api
    implementation("org.codemc.worldguardwrapper:worldguardwrapper:1.2.0-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.3.0")
    // Used api
    implementation("com.github.MilkBowl:VaultAPI:1.7")
    // Optionnal plugin compatibility
    compileOnly("com.github.Zrips:CMI-API:9.8.6.4")
    implementation("fr.xephi:authme:5.4.0")
    implementation("com.plotsquared:PlotSquared-Core:6.5.0")
    implementation("com.sk89q.worldedit:worldedit-core:7.3.0")
    implementation("com.github.rlf.uSkyBlock:uSkyBlock-API:v2.8.9")
    implementation("com.wasteofplastic:askyblock:3.0.9.4")
    implementation("com.github.TechFortress:GriefPrevention:16.17.1")
    implementation("world.bentobox:bentobox:1.17.2-SNAPSHOT")
    implementation("com.github.IntellectualSites.PlotSquared:Core:4.453")
    implementation("com.github.TownyAdvanced:Towny:0.97.5.0")
    // Add libs that does not belong to any valid maven repository
    // Using implementation makes shadow to include them in the final jar.
    // Local dependencies are not handled well and can't be excluded (see https://github.com/GradleUp/shadow/issues/142)
    compileOnly(files("../lib/AreaShop-2.6.0.jar", "../lib/IslandWorld-8.5.jar"))
    testImplementation("io.papermc.paper:paper-api:$compiledPaperApiVersion")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
}

project.base.archivesName.set(rootProject.name)
group = "de.epiceric"
version = releaseVersion

tasks.processResources {
    expand(
        mapOf(
            "version" to releaseVersion,
            "buildNumber" to releaseBuildNumber,
            "javaVersion" to targetJavaVersion,
            "paperVersion" to targetPaperVersion,
            "paperBuild" to stablePaperBuild,
            "paperChannel" to stablePaperChannel,
            "paperApiVersion" to compiledPaperApiVersion,
        )
    )
}

val verifyReleaseMetadata by tasks.registering(VerifyShopChestReleaseMetadata::class) {
    group = "verification"
    description = "Checks release, API, documentation, and maintained-server metadata for drift."

    this.pluginVersion.set(releaseVersion)
    this.buildNumber.set(releaseBuildNumber.toInt())
    this.gitCommitCount.set(repositoryCommitCount)
    this.javaVersion.set(targetJavaVersion)
    this.paperVersion.set(targetPaperVersion)
    this.paperBuild.set(stablePaperBuild.toInt())
    this.paperChannel.set(stablePaperChannel)
    this.paperApiVersion.set(compiledPaperApiVersion)
    readmeFile.set(rootProject.layout.projectDirectory.file("README.md"))
    todoFile.set(rootProject.layout.projectDirectory.file("TODO.md"))
    installationFile.set(
        rootProject.layout.projectDirectory.file("docs/installation.md"))
    docsManifestFile.set(
        rootProject.layout.projectDirectory.file("docs/plugin-docs.yml"))
    paperConventionFile.set(rootProject.layout.projectDirectory.file(
        "build-logic/src/main/kotlin/buildlogic.java-paper-conventions.gradle.kts"))
    pluginBuildFile.set(layout.projectDirectory.file("build.gradle.kts"))
    pluginDescriptorFile.set(
        layout.projectDirectory.file("src/main/resources/plugin.yml"))
    embeddedMetadataFile.set(
        layout.projectDirectory.file("src/main/resources/shopchest-build.properties"))

    val maintainedServer = rootProject.layout.projectDirectory.dir("servers/Paper-26.2")
    if (maintainedServer.asFile.isDirectory) {
        paperScriptConfigFile.set(maintainedServer.file("paperscript/config.json"))
        paperScriptStateFile.set(maintainedServer.file("paperscript/state.json"))
        serverLauncherFile.set(maintainedServer.file("1MB-minecraft.sh"))
    }
}

val verifyGeneratedReleaseMetadata by tasks.registering(VerifyGeneratedReleaseMetadata::class) {
    group = "verification"
    description = "Checks processed plugin metadata against the shared release properties."
    dependsOn(tasks.processResources)

    this.pluginVersion.set(releaseVersion)
    this.buildNumber.set(releaseBuildNumber)
    this.javaVersion.set(targetJavaVersion)
    this.paperVersion.set(targetPaperVersion)
    this.paperBuild.set(stablePaperBuild)
    this.paperChannel.set(stablePaperChannel)
    this.paperApiVersion.set(compiledPaperApiVersion)
    generatedMetadataFile.set(
        layout.buildDirectory.file("resources/main/shopchest-build.properties"))
    generatedDescriptorFile.set(layout.buildDirectory.file("resources/main/plugin.yml"))
}

tasks.named("check") {
    dependsOn(verifyReleaseMetadata, verifyGeneratedReleaseMetadata)
}
