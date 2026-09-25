package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Verifies that public ShopChest documentation still describes the shipped contract. */
@CacheableTask
abstract class VerifyShopChestDocumentation : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val readmeFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginDescriptorFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val commandSourceDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val readme = readmeFile.get().asFile
        val docs = documentationDirectory.get().asFile
        val configurationGuide = docs.resolve("configuration.md")
        val permissionsGuide = docs.resolve("permissions.md")
        val manifest = docs.resolve("plugin-docs.yml")

        requireFile(configurationGuide)
        requireFile(permissionsGuide)
        requireFile(manifest)

        verifyConfigurationCoverage(configurationGuide)
        verifyPermissionCoverage(readme, permissionsGuide)
        verifyProfileRoute(readme, docs)
        verifyManifestReferences(manifest)
        verifyMarkdownLinks(readme, docs)
    }

    private fun verifyConfigurationCoverage(configurationGuide: File) {
        val guide = configurationGuide.readText()
        val missing = configurationLeafKeys(configurationFile.get().asFile)
            .filterNot { guide.contains("`$it`") }
        requireThat(missing.isEmpty()) {
            "configuration.md is missing shipped config keys: ${missing.joinToString()}"
        }
    }

    private fun verifyPermissionCoverage(readme: File, permissionsGuide: File) {
        val readmeText = readme.readText()
        val permissionsText = permissionsGuide.readText()
        val declared = declaredPermissions(pluginDescriptorFile.get().asFile)
        requireThat(declared.isNotEmpty()) {
            "plugin.yml does not declare any top-level shopchest permissions."
        }
        declared.forEach { (permission, defaultValue) ->
            val tablePrefix = "| `$permission` | `$defaultValue` |"
            requireThat(readmeText.contains(tablePrefix)) {
                "README.md is missing permission/default row: $tablePrefix"
            }
            requireThat(permissionsText.contains(tablePrefix)) {
                "permissions.md is missing permission/default row: $tablePrefix"
            }
        }
        verifyNoUndeclaredPermissionRows(readme, readmeText, declared.keys)
        verifyNoUndeclaredPermissionRows(
            permissionsGuide, permissionsText, declared.keys)
    }

    private fun verifyProfileRoute(readme: File, docs: File) {
        val publicDocs = listOf(
            readme,
            docs.resolve("commands.md"),
            docs.resolve("player-guide.md"),
        )
        publicDocs.forEach { file ->
            requireFile(file)
            requireThat(file.readText().contains("/shops profile shopowner ")) {
                "${file.name} is missing the canonical external profile route."
            }
        }

        val sources = commandSourceDirectory.get().asFile
        val handler = sources.resolve("StorefrontProfileCommandHandler.java")
        val completer = sources.resolve("ShopTabCompleter.java")
        val executor = sources.resolve("ShopCommandExecutor.java")
        listOf(handler, completer, executor).forEach(::requireFile)
        requireText(handler, "profile shopowner <player|uuid> [shops [page]]")
        requireText(completer, "\"shopowner\"")
        requireText(completer, "profileShopOwnerCompletions")
        requireText(executor, "new String[]{\"profile\", \"shopowner\", ownerId.toString()}")
    }

    private fun verifyManifestReferences(manifest: File) {
        val reference = Regex(
            """^\s*[A-Za-z0-9_-]+:\s*[\"']?([^\"'#]+?\.(?:md|json|csv))[\"']?\s*$""")
        val referencedFiles = mutableSetOf<File>()
        manifest.readLines().forEachIndexed { index, line ->
            val value = reference.matchEntire(line)?.groupValues?.get(1)?.trim()
                ?: return@forEachIndexed
            val target = manifest.parentFile.resolve(value).normalize()
            requireThat(target.exists()) {
                "${manifest.name}:${index + 1} references missing file $value"
            }
            referencedFiles.add(target.absoluteFile)
        }
        val unlistedDocs = manifest.parentFile.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .filterNot { referencedFiles.contains(it.absoluteFile.normalize()) }
            .map(File::getName)
            .sorted()
        requireThat(unlistedDocs.isEmpty()) {
            "plugin-docs.yml does not import top-level docs: ${unlistedDocs.joinToString()}"
        }

        requireText(
            manifest,
            "docs_url: https://docs.1moreblock.com/custom-server-plugins/shopchest/")
    }

    private fun verifyNoUndeclaredPermissionRows(
            file: File,
            contents: String,
            declared: Set<String>
    ) {
        val row = Regex("""(?m)^\| `(shopchest\.[^`]+)` \| `([^`]+)` \|""")
        val rows = row.findAll(contents).map { it.groupValues[1] }.toList()
        val duplicateRows = rows.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        requireThat(duplicateRows.isEmpty()) {
            "${file.name} repeats permission rows: ${duplicateRows.joinToString()}"
        }
        val undeclared = rows.toSet().minus(declared).sorted()
        requireThat(undeclared.isEmpty()) {
            "${file.name} documents undeclared static permissions: ${undeclared.joinToString()}"
        }
    }

    private fun verifyMarkdownLinks(readme: File, docs: File) {
        val markdownFiles = sequenceOf(readme) + docs.walkTopDown()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
        val inlineLink = Regex("""!?\[[^]\r\n]*]\(([^)\r\n]+)\)""")
        markdownFiles.forEach { source ->
            inlineLink.findAll(source.readText()).forEach { match ->
                val destination = markdownDestination(match.groupValues[1])
                if (destination.isEmpty()
                    || destination.startsWith("#")
                    || destination.startsWith("/")
                    || destination.matches(Regex("""^[A-Za-z][A-Za-z0-9+.-]*:.*"""))) {
                    return@forEach
                }
                val path = destination
                    .substringBefore('#')
                    .substringBefore('?')
                if (path.isEmpty()) {
                    return@forEach
                }
                val target = source.parentFile.resolve(path).normalize()
                requireThat(target.exists()) {
                    "${source.relativeToOrSelf(project.projectDir)} links to missing $destination"
                }
            }
        }
    }

    private fun configurationLeafKeys(configuration: File): List<String> {
        val mapping = Regex("""^( *)([A-Za-z0-9][A-Za-z0-9-]*):(.*)$""")
        val entries = configuration.readLines().mapNotNull { line ->
            val match = mapping.matchEntire(line) ?: return@mapNotNull null
            MappingEntry(
                match.groupValues[1].length,
                match.groupValues[2],
                match.groupValues[3].trim())
        }
        val stack = mutableListOf<MappingEntry>()
        val leaves = mutableListOf<String>()
        entries.forEachIndexed { index, entry ->
            while (stack.isNotEmpty() && stack.last().indent >= entry.indent) {
                stack.removeLast()
            }
            val path = (stack.map { it.key } + entry.key).joinToString(".")
            val next = entries.getOrNull(index + 1)
            val hasMappedChild = next != null && next.indent > entry.indent
            if (entry.value.isNotEmpty() || !hasMappedChild) {
                leaves.add(path)
            }
            stack.add(entry)
        }
        return leaves.distinct().sorted()
    }

    private fun declaredPermissions(descriptor: File): Map<String, String> {
        val permission = Regex("""^  (shopchest\.[A-Za-z0-9.*]+):\s*$""")
        val defaultValue = Regex("""^    default:\s*([^\s#]+).*$""")
        val lines = descriptor.readLines()
        val declarations = linkedMapOf<String, String>()
        lines.forEachIndexed { index, line ->
            val name = permission.matchEntire(line)?.groupValues?.get(1)
                ?: return@forEachIndexed
            val end = (index + 1 until lines.size)
                .firstOrNull { permission.matches(lines[it]) }
                ?: lines.size
            val value = (index + 1 until end)
                .mapNotNull { defaultValue.matchEntire(lines[it])?.groupValues?.get(1) }
                .firstOrNull()
            requireThat(value != null) {
                "plugin.yml permission $name does not declare a default."
            }
            declarations[name] = value!!
        }
        return declarations
    }

    private fun markdownDestination(raw: String): String {
        val value = raw.trim()
        if (value.startsWith('<')) {
            val closing = value.indexOf('>')
            return if (closing > 0) value.substring(1, closing) else value
        }
        return value.substringBefore(' ')
    }

    private fun requireFile(file: File) {
        requireThat(file.isFile) { "Required documentation input is missing: $file" }
    }

    private fun requireText(file: File, expected: String) {
        requireThat(file.readText().contains(expected)) {
            "${file.name} is missing documentation contract value: $expected"
        }
    }

    private fun requireThat(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw GradleException(message())
        }
    }

    private data class MappingEntry(
        val indent: Int,
        val key: String,
        val value: String,
    )
}
