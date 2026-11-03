#!/usr/bin/env python3
import os
import subprocess

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LIBS_VERSIONS_TOML = """[versions]
agp = "8.7.3"
coreKtx = "1.15.0"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
lifecycleRuntimeKtx = "2.8.7"
lifecycleViewmodelCompose = "2.8.7"
lifecycleRuntimeCompose = "2.8.7"
activityCompose = "1.9.3"
kotlin = "2.1.0"
composeBom = "2024.09.00"
googleDevtoolsKsp = "2.1.0-1.0.29"
navigationCompose = "2.8.5"
roomRuntime = "2.7.0-alpha11"
roomKtx = "2.7.0-alpha11"
roomCompiler = "2.7.0-alpha11"
kotlinxCoroutinesTest = "1.9.0"
core = "1.6.1"
runner = "1.6.2"
coilCompose = "2.7.0"
retrofit = "2.11.0"
converterMoshi = "2.11.0"
kotlinxCoroutinesAndroid = "1.9.0"
kotlinxCoroutinesCore = "1.9.0"
accompanistPermissions = "0.36.0"
playServicesLocation = "21.3.0"
cameraCamera2 = "1.4.1"
cameraLifecycle = "1.4.1"
cameraView = "1.4.1"
cameraCore = "1.4.1"
loggingInterceptor = "4.12.0"
okhttp = "4.12.0"
moshiKotlin = "1.15.1"
moshiKotlinCodegen = "1.15.1"
datastorePreferences = "1.1.1"
robolectric = "4.14.1"
firebaseBom = "33.7.0"
secretsGradlePlugin = "2.0.1"
googleServices = "4.4.2"
credentials = "1.3.0"
googleid = "1.1.1"
media3 = "1.5.1"
workRuntimeKtx = "2.10.0"
onnxruntime = "1.19.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "roomRuntime" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "roomKtx" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "roomCompiler" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-core = { group = "androidx.test", name = "core", version.ref = "core" }
androidx-runner = { group = "androidx.test", name = "runner", version.ref = "runner" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coilCompose" }
coil-video = { group = "io.coil-kt", name = "coil-video", version.ref = "coilCompose" }
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntimeKtx" }
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutinesAndroid" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutinesCore" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "googleDevtoolsKsp" }
secrets = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secretsGradlePlugin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
"""

ROOT_BUILD_GRADLE = """plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
}
"""


def generate_keystore(filename, alias):
    path = os.path.join(ROOT_DIR, filename)
    if os.path.exists(path):
        return
    subprocess.run(
        [
            "keytool", "-genkeypair", "-v",
            "-keystore", path,
            "-storepass", "android",
            "-alias", alias,
            "-keypass", "android",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=VideoX AI,O=VideoX,C=US"
        ],
        check=True
    )


def main():
    generate_keystore("debug.keystore", "androiddebugkey")
    generate_keystore("my-upload-key.jks", "upload")

    env_path = os.path.join(ROOT_DIR, ".env")
    if not os.path.exists(env_path):
        with open(env_path, "w", encoding="utf-8") as f:
            f.write("\n")

    toml_path = os.path.join(ROOT_DIR, "gradle", "libs.versions.toml")
    with open(toml_path, "w", encoding="utf-8") as f:
        f.write(LIBS_VERSIONS_TOML)

    root_gradle_path = os.path.join(ROOT_DIR, "build.gradle.kts")
    with open(root_gradle_path, "w", encoding="utf-8") as f:
        f.write(ROOT_BUILD_GRADLE)

    app_gradle_path = os.path.join(ROOT_DIR, "app", "build.gradle.kts")
    with open(app_gradle_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = content.replace(
        "import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy",
        ""
    )
    content = content.replace(
        "alias(libs.plugins.android.application)",
        "alias(libs.plugins.android.application)\n  alias(libs.plugins.kotlin.android)"
    )
    content = content.replace("alias(libs.plugins.google.services)", "")
    content = content.replace(
        "compileSdk { version = release(36) { minorApiLevel = 1 } }",
        "compileSdk = 35"
    )
    content = content.replace("targetSdk = 36", "targetSdk = 35")
    content = content.replace(
        "compileOptions {\n    sourceCompatibility = JavaVersion.VERSION_11\n    targetCompatibility = JavaVersion.VERSION_11\n  }",
        "compileOptions {\n    sourceCompatibility = JavaVersion.VERSION_11\n    targetCompatibility = JavaVersion.VERSION_11\n  }\n  kotlinOptions {\n    jvmTarget = \"11\"\n  }"
    )
    content = content.replace(
        "googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }",
        ""
    )

    with open(app_gradle_path, "w", encoding="utf-8") as f:
        f.write(content)

    props_path = os.path.join(ROOT_DIR, "gradle.properties")
    if os.path.exists(props_path):
        with open(props_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        filtered = [
            line for line in lines
            if "kotlin.compiler.execution.strategy" not in line
            and "googleServices.missing.passthrough" not in line
            and "org.gradle.configuration-cache" not in line
        ]
        with open(props_path, "w", encoding="utf-8") as f:
            f.writelines(filtered)

    print("Successfully prepared GitHub Actions build environment.")


if __name__ == "__main__":
    main()
