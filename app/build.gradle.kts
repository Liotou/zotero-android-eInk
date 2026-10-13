import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.github.triplet.play") version "4.0.0"
    id("realm-android")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

    //----Dependency plugins start----
    id("kotlinDependencies")
    id("androidXDependencies")
    id("composeDependencies")
    id("daggerAndHiltDependencies")
    id("networkDependencies")
    id("testDependencies")
    //----Dependency plugins end----

    id("com.google.dagger.hilt.android")
    kotlin("plugin.compose")
}

android {
    compileSdk = BuildConfig.compileSdkVersion
    namespace = "org.zotero.android"

    defaultConfig {
        applicationId = BuildConfig.appId
        minSdk = BuildConfig.minSdkVersion
        targetSdk = BuildConfig.targetSdk
        versionCode = BuildConfig.versionCode
        versionName = BuildConfig.version.name
        testInstrumentationRunner = Libs.androidJUnitRunner

        buildConfigField("String", "BASE_API_URL", "\"https://api.zotero.org\"")
        buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
        buildConfigField("String", "PSPDFKIT_KEY", "\"\"")
        buildConfigField("boolean", "OFFICIAL_UPDATE_CHECK_ENABLED", "true")
        manifestPlaceholders["enableCrashReporting"] = false

        //Making build version components to be accessible from within the app.
        buildConfigField("int", "BUILD_VERSION_MAJOR", "${BuildConfig.version.major}")
        buildConfigField("int", "BUILD_VERSION_MINOR", "${BuildConfig.version.minor}")
        buildConfigField("int", "BUILD_VERSION_PATCH", "${BuildConfig.version.patch}")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("zotero.release.keystore")

            if (rootProject.file("keystore-secrets.txt").exists()) {
                val secrets: List<String> = rootProject
                    .file("keystore-secrets.txt")
                    .readLines()
                keyAlias = secrets[0]
                storePassword = secrets[1]
                keyPassword = secrets[2]
            }
        }
        // Signing key of the personal E-Ink builds. CI provides a private keystore through
        // the EINK_KEYSTORE_* environment variables; without them the public debug.keystore
        // of the repository is used, so that successive builds can still update each other.
        create("eink") {
            val einkKeystore = System.getenv("EINK_KEYSTORE_FILE")
            if (!einkKeystore.isNullOrBlank() && file(einkKeystore).exists()) {
                storeFile = file(einkKeystore)
                storePassword = System.getenv("EINK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EINK_KEY_ALIAS")
                keyPassword = System.getenv("EINK_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    androidResources {
        noCompress += listOf("ttf", "mov", "avi", "json", "html", "csv", "obb")
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfigs
                .findByName("debug")
                ?.storeFile = rootProject.file("debug.keystore")

            buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
            manifestPlaceholders["enableCrashReporting"] = false
            extra.set("enableCrashlytics", false)

            //Prevent variables from being 'optimized out' during debug,
            //so it becomes possible to see their values in debugger
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll(listOf("-Xdebug"))
                }
            }

        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getAt("release")

            buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "true")
            manifestPlaceholders["enableCrashReporting"] = true
        }
        // Personal E-Ink build: optimized like release, but installable next to the official
        // app, signed with our own key and without Zotero's crash reporting or update banner.
        create("eink") {
            initWith(getByName("release"))
            applicationIdSuffix = ".eink"
            signingConfig = signingConfigs.getAt("eink")
            resValue("string", "app_name", """"Zotero E-Ink"""")

            buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
            buildConfigField("boolean", "OFFICIAL_UPDATE_CHECK_ENABLED", "false")
            manifestPlaceholders["enableCrashReporting"] = false
            extra.set("enableCrashlytics", false)
        }
    }
    setDefaultProductFlavors()
    productFlavors {
        dev {
            resValue("string", "app_name", """"Zotero Debug""")
            buildConfigField("String", "PSPDFKIT_KEY", readPspdfkitKey())
            applicationIdSuffix = ".debug"
        }
        internal {
            resValue("string", "app_name", """"Zotero""")
            buildConfigField("String", "PSPDFKIT_KEY", readPspdfkitKey())
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.pickFirsts.add("META-INF/kotlinx-coroutines-core.kotlin_module")
    }

    androidComponents {
        beforeVariants { it.ignoreUnusedVariants() }

        // CI passes a build-time based versionCode so that every new E-Ink build can be
        // installed as an update of the previous one, whatever branch it comes from.
        onVariants(selector().withBuildType("eink")) { variant ->
            val einkVersionCode = (findProperty("einkVersionCode") as String?)?.toInt()
                ?: BuildConfig.versionCode
            val einkBuildNumber = (findProperty("einkBuildNumber") as String?) ?: "local"
            variant.outputs.forEach { output ->
                output.versionCode.set(einkVersionCode)
                output.versionName.set("${BuildConfig.version.name}-eink.$einkBuildNumber")
            }
            // No tablet uses 32-bit x86: dropping it saves ~30 MB per download.
            // x86_64 is kept for the emulator smoke tests.
            variant.packaging.jniLibs.excludes.add("lib/x86/**")
        }
    }
}

play {
    track.set("internal")
    defaultToAppBundles.set(true)
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

dependencies {

    //Material design
    implementation(Libs.materialDesign)

    //Crash
    implementation(Libs.Firebase.Crashlytics.crashlytics)

    //PSPDFKIT
    implementation(Libs.nutrient)

    //GSON
    implementation(Libs.gson)

    //ExoPlayer
    implementation(Libs.ExoPlayer.exoPlayer)
    implementation(Libs.ExoPlayer.mediaUi)

    //Coil
    implementation(Libs.Coil.compose)

    //Commons
    implementation(Libs.Commons.text)

    //Other
    implementation(Libs.timber)
    implementation(Libs.jodaTime)
    implementation(Libs.eventBus)
    implementation(Libs.keyboardVisibility)
    implementation(Libs.codeScanner)

}

kapt {
    correctErrorTypes = true
}

hilt {
    enableAggregatingTask = false
}

fun readPspdfkitKey() : String {
    val file = rootProject
        .file("pspdfkit-key.txt")
    if (!file.exists()) {
        logger.warn("pspdfkit-key.txt file not found. Using PSPDFKit without a key")
        return "\"\""
    }
    val keys: List<String> = file
        .readLines()
    return "\"${keys[0]}\""
}