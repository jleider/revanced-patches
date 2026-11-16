group = "app.revanced"

patches {
    about {
        name = "ReVanced Patches"
        description = "Patches for ReVanced"
        source = "git@github.com:revanced/revanced-patches.git"
        author = "ReVanced"
        contact = "patches@revanced.app"
        website = "https://revanced.app"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.apksig)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters"
        )
    }
}

publishing {
    repositories {
        maven {
            name = "githubPackages"
            // Forks publish to their own GitHub Packages; ReVanced's CI still publishes to revanced/revanced-patches.
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "revanced/revanced-patches"}")
            credentials(PasswordCredentials::class)
        }
    }
}

apply(from = "strings-processing.gradle.kts")

// Signing needs ReVanced's GPG key. Builds without one (forks, local builds) skip signing instead of failing.
tasks.withType<Sign>().configureEach {
    onlyIf { System.getenv("HAS_GPG_KEY") != "false" }
}
