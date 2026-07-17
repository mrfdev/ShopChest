plugins {
    id("buildlogic.java-paper-conventions")
    id("com.gradleup.shadow") version "9.0.0-rc1"
}

val pluginVersion = "1.15.1"
val javaVersion = "25"
val paperVersion = "26.2"
val buildNumber = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().padStart(3, '0')

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
    archiveFileName.set("1MB-ShopChest-v${pluginVersion}-${buildNumber}-j${javaVersion}-${paperVersion}.jar")
    dependencies {
        include(dependency("org.codemc.worldguardwrapper:worldguardwrapper"))
        include(dependency("com.zaxxer:HikariCP"))
    }
    relocate("org.codemc.worldguardwrapper", "de.epiceric.shopchest.dependencies.worldguardwrapper")
    relocate("com.zaxxer.hikari", "de.epiceric.shopchest.dependencies.hikari")
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
    testImplementation("io.papermc.paper:paper-api:26.2.build.60-beta")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
}

project.base.archivesName.set(rootProject.name)
group = "de.epiceric"
version = pluginVersion

tasks.processResources {
    expand(
        mapOf(
            "version" to version,
            "buildNumber" to buildNumber,
            "javaVersion" to javaVersion,
            "paperVersion" to paperVersion,
        )
    )
}
