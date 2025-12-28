# Immaculate

Immaculate is a gradle plugin, inspired by [spotless](https://github.com/diffplug/spotless/), for formatting your code. It aims to make use of gradle systems whenever possible.

## Getting Started

Apply the plugin in your `build.gradle`:

```gradle
plugins {
    id 'dev.lukebemish.immaculate' version '<version>'
}
```

The plugin is configured via the `immaculate` extension. This extension defines a series of `workflows`, which target a collection of files and are made up of a number of steps. For example:

```gradle
immaculate {
    workflows.register('java') {
        java() // includes all *.java files from each source set in this workflow
        google() // adds a step which runs google-java-format over your code
        trailingNewline() // adds a step which enforces that files must end with a trailing newline
    }
}
```

## Tasks

Checks are executed through the `immaculateCheck` task, which is depended on by `check`. To apply formatting changes, you may run `immaculateApply`, which modifies any files necessary to make formatting match what the formatters output; the original text is stored in `build/immaculate/<workflow>ImmaculateApply`, and is kept around for one execution of the task. Both check and apply tasks are incremental, so will only run over files which have changed since their last execution.

Per-workflow tasks are also created:
- `<workflow>ImmaculateCheck`: Check formatting for a specific workflow
- `<workflow>ImmaculateApply`: Apply formatting fixes for a specific workflow

## Configuration

### Workflows

Workflows define which files to format and what steps to apply. Each workflow has its own configuration:

```gradle
immaculate {
    workflows.register('myWorkflow') {
        // File selection methods
        java()           // All *.java files from source sets
        java { ... }     // Java files with custom PatternFilterable
        sources()        // All source files from source sets
        sources { ... }  // Source files with custom PatternFilterable

        // Workflow properties
        toggleOff = '// @formatter:off'  // Comment to disable formatting
        toggleOn = '// @formatter:on'    // Comment to re-enable formatting

        // Steps (see below)
    }
}
```

### Formatting Steps

#### Java Formatters

**Google Java Format**

[Project Homepage](https://github.com/google/google-java-format)

For versions, see [Maven Central](https://central.sonatype.com/artifact/com.google.googlejavaformat/google-java-format).

```gradle
google()  // Uses a built-in default version of google-java-format

google {
    version '1.23.0'  // Specify custom version
    args.addAll('--aosp')  // Add program arguments
    jvmArgs.addAll('-Xmx1g')  // Add JVM arguments
    javaLauncher = myCustomLauncher  // Custom Java launcher
}

// Specialized variants
googleRemoveUnusedImports()  // Only remove unused imports
googleSortImports()          // Only sort imports
googleFixImports()           // Remove unused and sort imports
```

**Palantir Java Format**

```gradle
palantir {
    version '2.50.0' // Specify custom version
    args.addAll('--skip-javadoc-formatting')
    jvmArgs.addAll('-Xmx1g')
}
```

**Eclipse JDT Formatter**

```gradle
eclipse {
    version '3.38.0'  // Default: 3.38.0
    config = file('eclipse-formatter.xml')  // Eclipse formatter config file
    args.addAll(...)
    jvmArgs.addAll(...)
}
```

#### Import Ordering

```gradle
importOrder {
    config = file('import-order.properties')  // Required config file
}
```

The import order config file format:
```properties
# Comments start with #
0=java.
1=javax.
2=
3=org.
4=com.
```

#### Simple Formatting Steps

```gradle
trailingNewline()      // Ensure files end with a newline
noTrailingSpaces()     // Remove trailing whitespace from lines
noTabs()               // Convert tabs to 4 spaces
noTabs(2)              // Convert tabs to 2 spaces
```

#### Custom Steps

**Custom line-by-line transformation:**
```gradle
linewise('myStep') { line ->
    return line.toUpperCase()
}
```

**Custom whole-file transformation:**
```gradle
custom('myStep') { fileContent ->
    return fileContent.replace('foo', 'bar')
}
```

**Generic step registration:**
```gradle
step('stepName', MyFormattingStepClass) {
    // Configure the step
}
```

See [dev.lukebemish.immaculate.steps.AbstractFormattingStep](./src/main/java/dev/lukebemish/immaculate/steps/AbstractFormattingStep.java) for a
base class for custom steps.

## Complete Example

```gradle
plugins {
    id 'java'
    id 'dev.lukebemish.immaculate' version '<version>'
}

immaculate {
    workflows.register('java') {
        java()

        // Import management
        importOrder {
            config = file('config/import-order.properties')
        }

        // Main formatter
        google()

        // Cleanup steps
        noTrailingSpaces()
        trailingNewline()

        toggleOff = '// @formatter:off'
        toggleOn = '// @formatter:on'
    }

    workflows.register('other') {
        sources {
            include '**/*.gradle'
            exclude '**/build/**'
        }

        custom('lowercase') { content ->
            content.toLowerCase()
        }
    }
}
```
