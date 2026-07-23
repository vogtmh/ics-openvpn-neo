import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import javax.inject.Inject

/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    alias(libs.plugins.android.application)
    id("checkstyle")
}

android {
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    namespace = "com.mavodev.openvpnneo"
    compileSdk = 36

    // Also update runcoverity.sh
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.mavodev.openvpnneo"
        minSdk = 21
        targetSdk = 36
        versionCode = 12
        versionName = "1.5.764"
        externalNativeBuild {
            cmake {
                //arguments+= "-DCMAKE_VERBOSE_MAKEFILE=1"
            }
        }
    }


    //testOptions.unitTests.isIncludeAndroidResources = true

    externalNativeBuild {
        cmake {
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            // src/main/assets is included by convention; add the CMake-generated assets
            assets.directories.add("build/ovpnassets")

        }

        create("ui") {
        }

        getByName("debug") {
        }

        getByName("release") {
        }
    }

    signingConfigs {
        create("release") {
            // ~/.gradle/gradle.properties
            val keystoreFile: String? by project
            storeFile = keystoreFile?.let { file(it) }
            val keystorePassword: String? by project
            storePassword = keystorePassword
            val keystoreAliasPassword: String? by project
            keyPassword = keystoreAliasPassword
            val keystoreAlias: String? by project
            keyAlias = keystoreAlias
            enableV1Signing = true
            enableV2Signing = true
        }

    }

    lint {
        enable += setOf("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        checkOnly += setOf("ImpliedQuantity", "MissingQuantity")
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }


    flavorDimensions += listOf("implementation", "ovpnimpl")

    productFlavors {
        create("ui") {
            dimension = "implementation"
        }

        create("ovpn23")
        {
            dimension = "ovpnimpl"
            buildConfigField("boolean", "openvpn3", "true")
        }
    }

    buildTypes {
        getByName("release") {
            isDefault = true
            val hasReleaseKeystore = project.findProperty("keystoreFile")
                .let { it is String && it.isNotEmpty() }
            // Set by Android Studio's "Generate Signed Bundle / APK" wizard for a single build.
            val hasInjectedSigning = project.hasProperty("android.injected.signing.store.file")
            if (project.hasProperty("icsopenvpnDebugSign")) {
                logger.warn("property icsopenvpnDebugSign set, using debug signing for release")
                signingConfig = android.signingConfigs.getByName("debug")
            } else if (hasInjectedSigning) {
                // Leave signingConfig unset so AGP applies the key injected by the IDE wizard.
                logger.lifecycle("Using signing config injected by Android Studio")
            } else if (!hasReleaseKeystore) {
                logger.warn("keystoreFile not set (~/.gradle/gradle.properties), falling back to debug signing for release")
                signingConfig = android.signingConfigs.getByName("debug")
            } else {
                productFlavors["ovpn23"].signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

var swigcmd = "swig"
// Workaround for macOS(arm64) and macOS(intel) since it otherwise does not find swig and
// I cannot get the Exec task to respect the PATH environment :(
if (file("/opt/homebrew/bin/swig").exists())
    swigcmd = "/opt/homebrew/bin/swig"
else if (file("/usr/local/bin/swig").exists())
    swigcmd = "/usr/local/bin/swig"


/**
 * Runs SWIG to generate the net.openvpn.ovpn3 Java wrapper classes consumed by the
 * Kotlin/Java sources. The C++ wrapper SWIG also emits here is unused by the native
 * build (CMake runs SWIG separately for that); only the generated Java matters.
 */
abstract class GenerateOvpn3SwigTask : DefaultTask() {
    @get:Input
    abstract val swigCmd: Property<String>

    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val interfaceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val root = projectRoot.get().asFile
        val genDir = outputDir.get().dir("net/openvpn/ovpn3").asFile
        genDir.mkdirs()
        execOperations.exec {
            commandLine(
                swigCmd.get(), "-outdir", genDir.absolutePath, "-outcurrentdir", "-c++", "-java",
                "-package", "net.openvpn.ovpn3",
                "-I${root}/src/main/cpp/openvpn3/client", "-I${root}/src/main/cpp/openvpn3/",
                "-DOPENVPN_PLATFORM_ANDROID",
                // 503: C++ operator== in vendored openvpn3 headers cannot be wrapped for Java
                // (harmless; the operator is only used by the native C++ build).
                "-w503",
                "-o", "${genDir}/ovpncli_wrap.cxx", "-oh", "${genDir}/ovpncli_wrap.h",
                "${root}/src/main/cpp/openvpn3/client/ovpncli.i"
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        val swigTask = tasks.register<GenerateOvpn3SwigTask>("generateOpenVPN3Swig${capName}") {
            swigCmd.set(swigcmd)
            projectRoot.set(layout.projectDirectory)
            interfaceFile.set(layout.projectDirectory.file("src/main/cpp/openvpn3/client/ovpncli.i"))
        }
        variant.sources.java?.addGeneratedSourceDirectory(swigTask, GenerateOvpn3SwigTask::outputDir)
    }
}


dependencies {
    // https://maven.google.com/web/index.html
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)

    uiImplementation(libs.android.view.material)
    uiImplementation(libs.androidx.activity)
    uiImplementation(libs.androidx.activity.ktx)
    uiImplementation(libs.androidx.appcompat)
    uiImplementation(libs.androidx.cardview)
    uiImplementation(libs.androidx.viewpager2)
    uiImplementation(libs.androidx.constraintlayout)
    uiImplementation(libs.androidx.core.ktx)
    uiImplementation(libs.androidx.fragment.ktx)
    uiImplementation(libs.androidx.lifecycle.runtime.ktx)
    uiImplementation(libs.androidx.lifecycle.viewmodel.ktx)
    uiImplementation(libs.androidx.preference.ktx)
    uiImplementation(libs.androidx.recyclerview)
    uiImplementation(libs.androidx.security.crypto)
    uiImplementation(libs.androidx.webkit)
    uiImplementation(libs.kotlin)
    uiImplementation(libs.mpandroidchart)
    uiImplementation(libs.square.okhttp)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
}

fun DependencyHandler.uiImplementation(dependencyNotation: Any): Dependency? =
    add("uiImplementation", dependencyNotation)