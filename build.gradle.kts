plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "dk.codestack"
version = providers.environmentVariable("VERSION").getOrElse("1.0.0")

labyMod {
    defaultPackageName = "dk.codestack.seventv"

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    // Set to true to log in with a real Minecraft account in the dev client.
                    // devLogin = true
                }
            }
        }
    }

    addonInfo {
        namespace = "seventv"
        displayName = "Emotify"
        author = "Code Stack"
        description = "Type :emoteName: in chat and see 7TV emotes rendered inline. Global + popular + custom emote sets, NSFW filter, emote picker (default key: V). Emotes and artwork belong to 7TV and their creators."
        minecraftVersion = "*"
        version = rootProject.version.toString()
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version

    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
