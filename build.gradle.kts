plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.aroussi"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242")
            untilBuild.set("261.*")
        }
        changeNotes.set("""
            <h3>0.1.0</h3>
            <ul>
                <li>Initial release — voice-to-text dictation via whisper.cpp / OpenAI / Groq</li>
            </ul>
        """.trimIndent())
    }

    publishing {
        token.set(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN"))
        // channels.set(listOf("default"))   // use "beta" or "eap" for pre-release channels
    }

    signing {
        certificateChainFile.set(file(providers.environmentVariable("CERTIFICATE_CHAIN_FILE").orElse("")))
        privateKeyFile.set(file(providers.environmentVariable("PRIVATE_KEY_FILE").orElse("")))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }
}
