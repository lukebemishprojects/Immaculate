# Immaculate

Immaculate is a gradle plugin, inspired by [spotless](https://github.com/diffplug/spotless/), for formatting your code. It aims to make use of gradle systems whenever possible.

The plugin is configured via the `immaculate` extension. This extension defines a series of `workflows`, which target a collection of files and are made up of a number of steps. For example:

```gradle
plugins {
    id 'dev.lukebemish.immaculate' version '<version>'
}

immaculate {
    workflows.register('java') {
        java() // includes all *.java files from each source set in this workflow
        google() // adds a step which runs google-java-format over your code
        trailingNewline() // adds a step which enforces that files must end with a trailing newline
    }
}
```

Checks are executed through the `immaculateCheck` task, which is depended on by `check`. To apply formatting changes, you may run `immaculateApply`, which modifies any files necessary to make formatting match what the formatters output; the original text is stored in `build/immaculate/<workflow>ImmaculateApply`, and is kept around for one execution of the task. Both check and apply tasks are incremental, so will only run over files which have changed since their last execution.
