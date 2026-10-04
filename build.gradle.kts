plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.aroussi"
version = "1.1.1"

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
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242")
            untilBuild.set("262.*")
        }
        changeNotes.set("""
            <h3>1.1.1</h3>
            <ul>
                <li>Extend IDE compatibility to build 262.*.</li>
            </ul>
            <h3>1.1.0</h3>
            <ul>
                <li>New <b>Whisper tool window</b> on the right sidebar — embeds the full settings UI with Apply and Reset buttons. No need to open Settings every time.</li>
                <li>Redesigned settings UI: card-style backend picker (Local / OpenAI / Groq) with Ready/Setup status badge per card.</li>
                <li>Local backend: install whisper.cpp + model directly from Settings (no separate action), live status, custom-binary override.</li>
                <li>Cloud backends: masked API key fields with inline <i>Test</i> button to verify the key against the provider.</li>
                <li>Audio section: detected sox/ffmpeg paths shown inline; install hint when neither is present.</li>
                <li>Language picker suggests common languages and still accepts custom language codes.</li>
                <li>Hotkey reminder shown directly in Settings.</li>
            </ul>
            <h3>1.0.0</h3>
            <ul>
                <li>Initial release - voice-to-text dictation via whisper.cpp / OpenAI / Groq.</li>
            </ul>
        """.trimIndent())
    }

    publishing {
        token.set(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN"))
        // channels.set(listOf("default"))   // use "beta" or "eap" for pre-release channels
    }

    pluginVerification {
        ides {
            ide("IC", "2024.2")
            ide("IC", "2024.3")
            ide("IC", "2025.1")
        }
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
