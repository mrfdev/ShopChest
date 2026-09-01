package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class VerifyShopChestReleaseMetadata : DefaultTask() {

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val buildNumber: Property<Int>

    @get:Input
    abstract val gitCommitCount: Property<Int>

    @get:Input
    abstract val javaVersion: Property<String>

    @get:Input
    abstract val paperVersion: Property<String>

    @get:Input
    abstract val paperBuild: Property<Int>

    @get:Input
    abstract val paperChannel: Property<String>

    @get:Input
    abstract val paperApiVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val readmeFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val todoFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val installationFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docsManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val paperConventionFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginBuildFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginDescriptorFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val embeddedMetadataFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val paperScriptConfigFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val paperScriptStateFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverLauncherFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val releaseBuild = buildNumber.get()
        val commits = gitCommitCount.get()
        requireThat(releaseBuild == commits || releaseBuild == commits + 1) {
            "Release build $releaseBuild must match Git commit count $commits "
                .plus("or its single pending release increment ${commits + 1}.")
        }

        val version = pluginVersion.get()
        val javaTarget = javaVersion.get()
        val paperTarget = paperVersion.get()
        val stableBuild = paperBuild.get()
        val channel = paperChannel.get()
        val apiVersion = paperApiVersion.get()
        val readme = readmeFile.get().asFile
        val todo = todoFile.get().asFile
        val installation = installationFile.get().asFile
        val docsManifest = docsManifestFile.get().asFile

        requireText(readme, "Paper $paperTarget build $stableBuild stable")
        requireText(readme, "| Plugin version | $version |")
        requireText(
            readme,
            "1MB-ShopChest-v$version-<build>-j$javaTarget-$paperTarget.jar")
        requireText(installation, "Paper $paperTarget build $stableBuild stable")
        requireText(
            installation,
            "1MB-ShopChest-v$version-<build>-j$javaTarget-$paperTarget.jar")
        requireText(
            docsManifest,
            "paper_target: \"$paperTarget build $stableBuild stable\"")
        requireText(todo, "Paper $paperTarget build $stableBuild stable")
        requireText(
            paperConventionFile.get().asFile,
            "shopchestPaperApiVersion")
        requireText(
            pluginBuildFile.get().asFile,
            "testImplementation(\"io.papermc.paper:paper-api:\$compiledPaperApiVersion\")")
        requireText(
            pluginDescriptorFile.get().asFile,
            "api-version: '\${paperVersion}'")
        requireText(
            embeddedMetadataFile.get().asFile,
            "paper-api-version=\${paperApiVersion}")

        if (version.endsWith("-SNAPSHOT")) {
            requireText(readme, "| Release status | Beta snapshot, untested |")
            requireText(installation, "`$version` is an untested beta rollback")
        }

        val releaseSurface = listOf(readme, todo, installation, docsManifest)
            .joinToString("\n") { it.readText() }
        listOf(
            "1MB-ShopChest-v1.15.2",
            "Paper 26.2 build 71 beta",
            "26.2.build.71-beta",
            "jdk-25.0.2",
            "jdk-26.0.1",
        ).forEach { stale ->
            requireThat(!releaseSurface.contains(stale)) {
                "Release metadata still contains stale value: $stale"
            }
        }

        if (paperScriptConfigFile.isPresent) {
            val config = paperScriptConfigFile.get().asFile
            val state = paperScriptStateFile.get().asFile
            val launcher = serverLauncherFile.get().asFile
            requireText(config, "\"default_channel\": \"$channel\"")
            requireText(config, "\"check_latest_channel_only\": \"$channel\"")
            requireText(config, "\"allow_same_version_build_upgrade\": true")
            requireText(config, "\"keep_server_jars\": 2")
            requireText(config, "\"reconcile_server_jars_after_stage\": true")
            requireThat(!config.readText().contains("\"download_filename_pattern\"")) {
                "Maintained PaperScript config still contains deprecated download_filename_pattern."
            }
            requireText(state, "\"current_build\": $stableBuild")
            requireText(state, "\"current_channel\": \"$channel\"")
            requireText(launcher, "_javaBin")
            requireText(launcher, "_minJavaVersion")
            requireThat(!launcher.readText().contains("jdk-25." + "0.2")) {
                "Maintained server launcher still contains the stale JDK 25.0.2 path."
            }
            requireThat(!launcher.readText().contains("jdk-26." + "0.1")) {
                "Maintained server launcher still contains the stale JDK 26.0.1 path."
            }
        }

        requireThat(apiVersion == "$paperTarget.build.$stableBuild-${channel.lowercase()}") {
            "Paper API $apiVersion does not match $paperTarget build $stableBuild $channel."
        }
    }

    private fun requireText(file: File, expected: String) {
        requireThat(file.readText().contains(expected)) {
            "${file.name} is missing release value: $expected"
        }
    }

    private fun requireThat(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw GradleException(message())
        }
    }
}
