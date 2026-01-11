import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    id("org.openapi.generator") version "7.10.0"
    kotlin("plugin.serialization") version "2.3.0"
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/openApi/floatplane-openapi-specification-trimmed.json")
    outputDir.set("$projectDir/build/generated/openapi")
    apiPackage.set("org.miker.floatsauce.api")
    modelPackage.set("org.miker.floatsauce.models")
    library.set("multiplatform")
    configOptions.set(mapOf(
        "enumPropertyNaming" to "UPPERCASE",
        "dateLibrary" to "kotlinx-datetime"
    ))
}

tasks.withType<org.openapitools.generator.gradle.plugin.tasks.GenerateTask> {
    val buildDir = layout.buildDirectory
    doLast {
        val buildDirFile = buildDir.get().asFile
        val generatedDir = File(buildDirFile, "generated/openapi/src/commonMain/kotlin")
        if (generatedDir.exists()) {
            generatedDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    var content = file.readText()
                    var modified = false

                    // Model fixes
                    if (file.path.contains("org/miker/floatsauce/models")) {
                        if (content.contains("kotlin.Any") && !content.contains("@Contextual kotlin.Any")) {
                            content = content.replace("kotlin.Any", "@Contextual kotlin.Any")
                            modified = true
                        }
                        if (content.contains(": Any") && !content.contains(": @Contextual Any")) {
                            content = content.replace(": Any", ": @Contextual Any")
                            modified = true
                        }
                        if (content.contains(") : kotlin.collections.HashMap<String, @Contextual kotlin.Any>()")) {
                            content = content.replace(") : kotlin.collections.HashMap<String, @Contextual kotlin.Any>()", ")")
                            modified = true
                        }
                    }

                    // ApiClient fixes
                    if (file.name == "ApiClient.kt") {
                        if (content.contains("private val authentications")) {
                            content = content.replace("private val authentications", "protected val authentications")
                            modified = true
                        }
                        if (content.contains("ApiKeyAuth(\"query\", \"sails.sid\")")) {
                            content = content.replace("ApiKeyAuth(\"query\", \"sails.sid\")", "ApiKeyAuth(\"cookie\", \"sails.sid\")")
                            modified = true
                        }
                        if (!content.contains("fun getAuthentication")) {
                            content = content.replace("    companion object",
                                "    fun getAuthentication(name: String): Authentication? {\n        return authentications[name]\n    }\n\n    companion object")
                            modified = true
                        }
                    }

                    // ApiKeyAuth fixes
                    if (file.name == "ApiKeyAuth.kt") {
                        if (content.contains("val paramName: String")) {
                            content = content.replace("val paramName: String", "var paramName: String")
                            modified = true
                        }
                        if (!content.contains("\"cookie\" -> headers[\"Cookie\"]")) {
                            content = content.replace("            \"header\" -> headers[paramName] = value",
                                "            \"header\" -> headers[paramName] = value\n            \"cookie\" -> headers[\"Cookie\"] = \"\$paramName=\$value\"")
                            modified = true
                        }
                    }

                    if (modified) {
                        file.writeText(content)
                    }
                }
            }
        }
    }
}

kotlin {
    androidLibrary {
        namespace = "org.miker.floatsauce.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    applyDefaultHierarchyTemplate()

    val appleTargets = listOf(
        tvosArm64(),
        tvosSimulatorArm64()
    )

    appleTargets.forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin"))
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                api(libs.kermit)
                api(libs.androidx.lifecycle.viewmodel)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val appleMain by getting
    }
}


tasks.register("generateSecrets") {
    val secretsFile = rootProject.file("auth_secrets.properties")
    val outputDir = layout.buildDirectory.dir("generated/sources/secrets/commonMain/kotlin")
    inputs.file(secretsFile).optional()
    outputs.dir(outputDir)

    doLast {
        val properties = Properties()
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { properties.load(it) }
        }
        val floatplaneCookie = properties.getProperty("floatplane_cookie", "")
        val sauceplusCookie = properties.getProperty("sauceplus_cookie", "")

        val outputFile = outputDir.get().file("org/miker/floatsauce/data/AuthSecrets.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("""
            package org.miker.floatsauce.data

            object AuthSecrets {
                const val FLOATPLANE_COOKIE = "$floatplaneCookie"
                const val SAUCEPLUS_COOKIE = "$sauceplusCookie"
            }
        """.trimIndent())
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/secrets/commonMain/kotlin"))
        }
    }
}

tasks.matching { it.name.startsWith("compile") }.configureEach {
    dependsOn("generateSecrets")
    dependsOn("openApiGenerate")
}
