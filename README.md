# Immaculate

Immaculate is a Gradle plugin, inspired by [spotless](https://github.com/diffplug/spotless/), for formatting your code. It aims to make use of Gradle systems whenever possible and be compatible with modern Gradle features.

## Setup

To use Immaculate, apply the plugin to your Gradle project.

```gradle
plugins {
    id 'dev.lukebemish.immaculate' version '<version>'
}
```

The latest version can be found on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/dev.lukebemish.immaculate).

## Workflows

Immaculate's DSL allows you to define a series of formatting workflows, each of which runs over a collection of files
running certain formatting steps. Workflows define which files they format.

```gradle
immaculate {
    workflows.register('java') {
        java() // Includes all *.java files from each source set in this workflow
        sources() // Includes all files from each source set
        java {
            include '**/package-info.java' // The included files can be further filtered using Ant-style patterns
        }
        files.from('some-file.txt') // You can also manually declare files to include
    }
}
```

Workflows also define certain shared properties for formatting.

```gradle
// Allows definition of blocks where Immaculate's formatting is ignored.
toggleOff = @formatter:off'
toggleOn = '@formatter:on'

// The location that the file path of files with formatting errors will be reported from
// This is useful if you are consuming Immaculate's output to annotate GitHub action runs or the like
reportIssuesRootPath = file('src/main/java')
```

## Steps

Steps implement `FormattingStep` and define a `FileFormatter`, which is a reusable tool to format files during a workflow
execution. Steps are added by name and type.

```gradle
step("stepName", StepType.class) {
    // Configure the step here
}
```

For the built-in step types, Immaculate provides certain helpers. For instance, the following are equivalent:

```gradle
google()
step("google", GoogleJavaFormatStep.class) {}
```

### Built-in Steps

#### `CustomStep` and `LinewiseStep`

These steps transform single lines or the entire file content.

```gradle
linewise("myStep") { line ->
    line.capitalize() // Capitalizes each line
}

custom("myStep") { contents ->
    contents.replaceAll("\\r?\\n", "") // Removes all newlines from the file
}
```

If steps throw exceptions, these will be reported alongside the failing file. Additionally, if the step
returns `null`, the file/line will be considered unmodified.

```gradle
linewise("disallowStarImports") { line ->
    if (line.trim().endsWith(".*;")) {
        throw new RuntimeException("Found a wildcard import")
    }
}
```

Certain common linewise/custom steps are provided.

```gradle
trailingNewline() // Ensures the file ends with a trailing newline
noTrailingSpaces() // Removes trailing spaces from each line
noTabs() // Replaces leading tabs with 4 spaces
noTabs(2) // Replaces leading tabs with 2 spaces
```

#### `ImportOrderStep`

```gradle
importOrder {
    config = file("import-order.txt") // Define the file to source import order from
}
```

The import order file takes a `index=value` format, with lines starting with `#` being ignored. For instance:

```
1=javax
# This line is ignored
2=
0=java
```

This will sort first `java` imports, then `javax`, then all others, with an empty line between the groups.

### External Formatters

Immaculate can run certain external formatters over your code. These formatters are located via standard Gradle dependencies.
You can customize the version of the formatter resolved, or even the entire dependency used.

```gradle
version = "x.y.z" // Use a custom version
formatter = dependencies.module("group:name:x.y.z") // Use a custom implementation of the formatter altogether
args.add("--some-flag") // Add custom arguments the formatter will be invoked with
jvmArgs.add("-Xmx1G") // Add custom JVM arguments when invoking the formatter
```

The forked JVM will be shared within a step for every file.

#### `GoogleJavaFormatStep`

Uses [google-java-format](https://github.com/google/google-java-format).

```gradle
google()
google {
    removeUnusedImports() // Only remove unused imports
    sortImports() // Only sort imports
    fixImports() // Only sort imports and remove unused imports
    aosp() // Use AOSP style rather than Google style
}
```

#### `EclipseJdtFormatStep`

```gradle
eclipse {
    config = file("formatter-config.xml") // Use a custom Eclipse formatter configuration file
}
```

#### `PalantirJavaFormatStep`

Uses [palantir-java-format](https://github.com/palantir/palantir-java-format).

```gradle
palantir()
palantir {}
```

## Tasks

Each workflow creates two tasks:
- `<workflow>ImmaculateCheck`, which runs each step and fails if any step outputs differ from the input files, reporting the failure
- `<workflow>ImmaculateApply`, which runs each step and modifies files in-place to match the output of the steps

When running `<workflow>ImmaculateApply`, the original text is stored in `build/immaculate/<workflow>ImmaculateApply`, and is kept around for one execution of the task. Both check and apply tasks are incremental, so will only run over files which have changed since their last execution.

In addition, `immaculateCheck` and `immaculateApply` tasks are created which depend on all workflow check/apply tasks, and the
built-in `check` tasks depends on `immaculateCheck`.
