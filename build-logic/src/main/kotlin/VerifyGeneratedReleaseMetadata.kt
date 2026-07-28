package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

@CacheableTask
abstract class VerifyGeneratedReleaseMetadata : DefaultTask() {

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val buildNumber: Property<String>

    @get:Input
    abstract val javaVersion: Property<String>

    @get:Input
    abstract val paperVersion: Property<String>

    @get:Input
    abstract val paperBuild: Property<String>

    @get:Input
    abstract val paperChannel: Property<String>

    @get:Input
    abstract val paperApiVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedMetadataFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedDescriptorFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val metadata = Properties()
        generatedMetadataFile.get().asFile.inputStream().use { metadata.load(it) }
        val expected = mapOf(
            "version" to pluginVersion.get(),
            "build" to buildNumber.get(),
            "java-target" to javaVersion.get(),
            "paper-target" to paperVersion.get(),
            "paper-build" to paperBuild.get(),
            "paper-channel" to paperChannel.get(),
            "paper-api-version" to paperApiVersion.get(),
        )
        expected.forEach { (key, value) ->
            if (metadata.getProperty(key) != value) {
                throw GradleException(
                    "Generated $key=${metadata.getProperty(key)}; expected $value")
            }
        }
        if (!generatedDescriptorFile.get().asFile.readText()
                .contains("api-version: '${paperVersion.get()}'")) {
            throw GradleException(
                "Generated plugin.yml does not target Paper API ${paperVersion.get()}.")
        }
    }
}
