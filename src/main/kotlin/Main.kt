import java.io.File
import java.io.OutputStreamWriter

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: gradle2dot directory [output.dot]")
        System.exit(1)
    }

    val directory = File(args[0]).normalize()
    val writer = if (args.size >= 2) {
        File(args[1]).normalize().writer()
    } else {
        System.out.writer()
    }

    writer.writeDependencies(processDirectory(directory))
}

fun OutputStreamWriter.writeDependencies(dependencies: List<Dependency>) {
    use { writer ->
        writer.appendln("digraph G {")
        dependencies
            .filter { it.variant?.toLowerCase() == "main" || it.variant.isNullOrBlank() }
            .forEach { dependency ->
                writer.appendln("    \"${dependency.parent}\" -> \"${dependency.name}\"")
            }
        writer.appendln("}")
    }
}

fun processDirectory(directory: File): List<Dependency> {
    val modules = File("$directory/settings.gradle")
        .readLines()
        .filter { line -> line.startsWith("include") }
        .map { line ->
            line.removePrefix("include").trim('"', '\'', ':', ' ')
        }

    return modules
        .map { module ->
            parseDependencies(directory, module)
        }
        .filter { it.isNotEmpty() }
        .flatten()
}

private val projectDependencyRegex = ".*(\\w*)(implementation|api|compile) project\\(['\"]+:([\\w-]+)['\"]\\).*"
    .toRegex(RegexOption.IGNORE_CASE)

data class Dependency(val parent: String, val variant: String?, val transitivity: String, val name: String)

fun parseDependencies(directory: File, module: String): List<Dependency> {
    val gradleFile = File("$directory/$module/build.gradle")
    var foundDependencies = false
    val dependencies = mutableListOf<Dependency>()

    gradleFile.readLines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (line.contains("dependencies")) foundDependencies = true

        if (foundDependencies) {
            val groups = projectDependencyRegex.matchEntire(line)?.groups
            if (groups != null) {
                val buildVariant = groups[1]?.value // e.g. "", "debug", "test"
                val transitivity = groups[2]?.value // e.g. "compile", "implementation", "api"
                        ?: error("Unable to parse transitivity: $gradleFile@$lineNumber: $line")
                val projectName = groups[3]?.value
                        ?: error("Unable to parse project name: $gradleFile@$lineNumber: $line")

                dependencies.add(Dependency(module, buildVariant, transitivity, projectName))
            }
        }
    }

    return dependencies.toList()
}