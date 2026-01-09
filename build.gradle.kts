plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

val downloadApiSpec by tasks.registering {
    group = "openapi"
    description = "Downloads the OpenAPI specification"

    val url = "https://jamamp.github.io/FloatplaneAPIDocs/floatplane-openapi-specification-trimmed.json"
    val outputDir = layout.projectDirectory.dir("openApi")
    val outputFile = outputDir.file("floatplane-openapi-specification-trimmed.json")

    inputs.property("url", url)
    outputs.file(outputFile)

    doLast {
        if (!outputDir.asFile.exists()) {
            outputDir.asFile.mkdirs()
        }
        java.net.URL(url).openStream().use { input ->
            outputFile.asFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
