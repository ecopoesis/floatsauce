import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        val generatedDir = File(buildDirFile, "generated/openapi/src/commonMain/kotlin/org/miker/floatsauce/models")
        if (generatedDir.exists()) {
            generatedDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    var content = file.readText()
                    var modified = false
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
            kotlin.srcDir("build/generated/openapi/src/commonMain/kotlin")
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val appleMain by getting
    }
}

