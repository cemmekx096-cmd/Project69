pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "aniyomi-extensions"

// Include your extensions here
include(":src:id:anichin")

// Optionally include lib modules if needed
// include(":lib:streamsb-extractor")
// include(":lib:doodstream-extractor")