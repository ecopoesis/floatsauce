import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
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
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
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
/*
androidLibrary {
    namespace = "org.miker.floatsauce.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
  //  compileOptions {
  //      sourceCompatibility = JavaVersion.VERSION_11
  //      targetCompatibility = JavaVersion.VERSION_11
//}
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}*/
