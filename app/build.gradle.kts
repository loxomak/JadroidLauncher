plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ------------------------------------------------------------------------------------------------
// Release signing
//
// The keystore is never committed. Pass it through environment variables (CI) or Gradle properties
// (local `~/.gradle/gradle.properties`):
//
//   JADROID_KEYSTORE_PATH       /absolute/path/to/jadroid-release.jks
//   JADROID_KEYSTORE_PASSWORD   ...
//   JADROID_KEY_ALIAS           jadroid
//   JADROID_KEY_PASSWORD        ...
//
// Without a keystore the release APK is signed with the debug key so `assembleRelease` still
// produces an installable artifact for testing. A *partially* configured keystore (for example a
// file without a password) fails the build instead of silently producing a debug-signed "release".
fun buildSecret(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(propertyName) as String?)?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = buildSecret("JADROID_KEYSTORE_PATH", "jadroid.keystorePath")
    ?.let { file(it) }
    ?.takeIf { it.isFile }
val releaseKeystorePassword = buildSecret("JADROID_KEYSTORE_PASSWORD", "jadroid.keystorePassword")
val releaseKeyAlias = buildSecret("JADROID_KEY_ALIAS", "jadroid.keyAlias")
val releaseKeyPassword = buildSecret("JADROID_KEY_PASSWORD", "jadroid.keyPassword")

val missingSigningValues = buildList {
    if (releaseKeystorePassword == null) add("JADROID_KEYSTORE_PASSWORD")
    if (releaseKeyAlias == null) add("JADROID_KEY_ALIAS")
    if (releaseKeyPassword == null) add("JADROID_KEY_PASSWORD")
}

// A keystore was given but the credentials are incomplete: stop with an actionable message.
if (releaseKeystoreFile != null && missingSigningValues.isNotEmpty()) {
    throw GradleException(
        "JADROID_KEYSTORE_PATH was set but these required values are missing: " +
            missingSigningValues.joinToString(", ") +
            ". Set all of them (environment variables or jadroid.* Gradle properties) so the " +
            "release APK can be signed."
    )
}

val useReleaseSigning = releaseKeystoreFile != null && missingSigningValues.isEmpty()

android {
    namespace = "com.jadroid.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jadroid.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.2"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Azure application (client) id used for the Microsoft device-code login.
        // Override with -Pjadroid.msClientId=<your-app-id> when building your own release.
        val msClientId = (project.findProperty("jadroid.msClientId") as String?) ?: "00000000402b5328"
        buildConfigField("String", "MS_CLIENT_ID", "\"$msClientId\"")
    }

    signingConfigs {
        if (useReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Kept off so a release APK cannot break reflection/relocation at runtime until it has
            // been smoke tested; flip to true once minified builds are verified on device.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = if (useReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "Jadroid: no release keystore configured (JADROID_KEYSTORE_PATH), " +
                        "signing the release APK with the debug key. Do not publish this APK."
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // android.util.Log is a stub in JVM unit tests: return defaults instead of throwing so the
        // pure logic (payload parsing, endpoint selection, ...) stays testable.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
