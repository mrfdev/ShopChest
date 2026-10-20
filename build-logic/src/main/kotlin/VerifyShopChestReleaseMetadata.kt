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
    abstract val committedBuildNumber: Property<Int>

    @get:Input
    abstract val javaVersion: Property<String>

    @get:Input
    abstract val runtimeJavaVersion: Property<String>

    @get:Input
    abstract val buildJdkVersion: Property<String>

    @get:Input
    abstract val runtimeJdkVersion: Property<String>

    @get:Input
    abstract val gradleJdkVersion: Property<String>

    @get:Input
    abstract val compilerJdkVersion: Property<String>

    @get:Input
    abstract val testJdkVersion: Property<String>

    @get:Input
    abstract val testJavaVersion: Property<String>

    @get:Input
    abstract val paperVersion: Property<String>

    @get:Input
    abstract val paperBuild: Property<Int>

    @get:Input
    abstract val paperChannel: Property<String>

    @get:Input
    abstract val paperApiVersion: Property<String>

    @get:Input
    abstract val paperSha256: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val agentInstructionsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverGuideFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseReadinessFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val troubleshootingFile: RegularFileProperty

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
        val committedBuild = committedBuildNumber.get()
        requireThat(releaseBuild > 0 && releaseBuild >= committedBuild) {
            "Release build $releaseBuild must not regress below committed build $committedBuild."
        }

        val version = pluginVersion.get()
        val javaTarget = javaVersion.get()
        val paperTarget = paperVersion.get()
        val targetBuild = paperBuild.get()
        val channel = paperChannel.get()
        val apiVersion = paperApiVersion.get()
        val channelLabel = channel.lowercase()
        val runtimeTarget = runtimeJavaVersion.get()
        val buildJdk = buildJdkVersion.get()
        val runtimeJdk = runtimeJdkVersion.get()
        val readme = readmeFile.get().asFile
        val todo = todoFile.get().asFile
        val installation = installationFile.get().asFile
        val docsManifest = docsManifestFile.get().asFile
        val agentInstructions = agentInstructionsFile.get().asFile
        val serverGuide = serverGuideFile.get().asFile
        val readiness = releaseReadinessFile.get().asFile
        val troubleshooting = troubleshootingFile.get().asFile

        requireThat(channel == "STABLE") { "Development must target the STABLE Paper channel." }
        requireThat(apiVersion == "$paperTarget.build.$targetBuild-${channel.lowercase()}") {
            "Paper API $apiVersion does not match $paperTarget build $targetBuild $channel."
        }
        requireThat(paperTarget == "26.3") { "Minecraft / plugin.yml API target must remain 26.3." }
        requireThat(javaTarget == "25" && runtimeTarget.toInt() >= javaTarget.toInt()) {
            "Retain Java 25 bytecode and a compatible server runtime."
        }
        requireThat(buildJdk.startsWith("$javaTarget.") && runtimeJdk.startsWith("$runtimeTarget.")) {
            "Exact JDK versions must match the compilation and server Java targets."
        }
        requireJdk(gradleJdkVersion.get(), buildJdk, "Gradle")
        requireJdk(compilerJdkVersion.get(), buildJdk, "Java compiler")
        val expectedTestJdk = when (testJavaVersion.get()) {
            javaTarget -> buildJdk
            runtimeTarget -> runtimeJdk
            else -> throw GradleException("Tests must use the build or server Java target.")
        }
        requireJdk(testJdkVersion.get(), expectedTestJdk, "Test JVM")
        requireThat(paperSha256.get().matches(Regex("[0-9a-f]{64}"))) {
            "Paper server must have a pinned SHA-256 from official Fill metadata."
        }

        requireText(readme, "Paper $paperTarget build $targetBuild $channelLabel")
        requireText(readme, "| Plugin version | $version |")
        requireText(
            readme,
            "1MB-ShopChest-v$version-<build>-j$javaTarget-$paperTarget.jar")
        requireText(installation, "Paper $paperTarget build $targetBuild $channelLabel")
        requireText(
            installation,
            "1MB-ShopChest-v$version-<build>-j$javaTarget-$paperTarget.jar")
        requireText(
            docsManifest,
            "paper_target: \"$paperTarget build $targetBuild $channelLabel\"")
        requireText(todo, "Paper $paperTarget build $targetBuild $channelLabel")
        requireText(readme, "`$apiVersion`")
        requireText(installation, "io.papermc.paper:paper-api:$apiVersion")
        requireText(agentInstructions, "Paper $paperTarget build $targetBuild $channel")
        requireText(agentInstructions, "Build $releaseBuild is reserved")
        requireText(serverGuide, "Paper $paperTarget build $targetBuild $channel")
        requireText(readiness, "disposable Paper $paperTarget build $targetBuild $channel test server")
        requireText(readme, "| Server runtime | Java $runtimeJdk")
        requireText(readme, "| Build toolchain | Gradle wrapper with JDK $buildJdk |")
        requireText(readme, "| Plugin bytecode | Java $javaTarget |")
        requireText(installation, "Java $runtimeJdk for the maintained Paper $paperTarget test runtime")
        requireText(agentInstructions, "Build and test with JDK $buildJdk")
        requireText(agentInstructions, "Run the maintained local server with JDK $runtimeJdk.")
        requireText(serverGuide, "Java $runtimeJdk at")
        requireText(todo, "disposable Paper $paperTarget build $targetBuild $channel server on Java $runtimeJdk")
        requireText(readiness, "Select JDK $buildJdk")
        requireText(readiness, "candidate jar on Java $runtimeJdk")
        requireText(troubleshooting, "verified server runtime is Java $runtimeJdk.")
        listOf(readme, installation).forEach { file ->
            requireText(file, "jdk-$buildJdk.jdk/Contents/Home")
            requireText(file, "jdk-$runtimeJdk.jdk/Contents/Home")
        }
        requireText(serverGuide, "jdk-$runtimeJdk.jdk/Contents/Home")
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
            requireText(readme, "| Release status | Pre-live beta snapshot |")
            requireText(installation, "`$version` is a pre-live beta checkpoint")
        }

        val releaseSurface = listOf(readme, todo, installation, docsManifest,
            agentInstructions, serverGuide, readiness, troubleshooting)
            .joinToString("\n") { it.readText() }
        listOf(
            "1MB-ShopChest-v1.15.2",
            "Paper 26.2 build 71 beta",
            "26.2.build.71-beta",
            "jdk-25.0.2",
            "jdk-26.0.1",
            "jdk-25.0.4.jdk",
            "jdk-26.0.2.jdk",
            "jdk-25.0.4.1.jdk",
            "jdk-27.jdk",
            "26.3.build.41-alpha",
            "26.3 build 41",
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
            // Staging a verified update must not claim the maintained server was started.
            requireText(state, "\"staged_version\": \"$paperTarget\"")
            requireText(state, "\"staged_build\": $targetBuild")
            requireText(state, "\"staged_channel\": \"$channel\"")
            requireText(state, "\"staged_sha256\": \"${paperSha256.get()}\"")
            listOf("jdk-25.0.4.jdk", "jdk-26.0.2.jdk", "jdk-25.0.4.1.jdk", "jdk-27.jdk").forEach { stale ->
                requireThat(!launcher.readText().contains(stale)) {
                    "Maintained server launcher still contains removed JDK path: $stale"
                }
            }
            requireText(launcher, "_javaBin")
            requireText(launcher, "_minJavaVersion=$runtimeTarget")
            requireText(launcher, "_minecraftVersion=\"$paperTarget\"")
            requireText(launcher, "jdk-$runtimeJdk.jdk/Contents/Home")
            requireThat(!launcher.readText().contains("jdk-25." + "0.2")) {
                "Maintained server launcher still contains the stale JDK 25.0.2 path."
            }
            requireThat(!launcher.readText().contains("jdk-26." + "0.1")) {
                "Maintained server launcher still contains the stale JDK 26.0.1 path."
            }
        }

    }

    private fun requireJdk(actual: String, expected: String, label: String) {
        requireThat(actual.substringBefore('+').substringBefore('-') == expected) {
            "$label must use JDK $expected; found $actual."
        }
    }

    private fun requireText(file: File, expected: String) {
        requireThat(file.readText().replace(Regex("\\s+"), " ").contains(expected)) {
            "${file.name} is missing release value: $expected"
        }
    }

    private fun requireThat(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw GradleException(message())
        }
    }
}
