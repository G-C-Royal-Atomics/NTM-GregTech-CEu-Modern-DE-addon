plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.148"
}

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modGroup = property("mod_group_id") as String

version = modVersion
group = modGroup
base.archivesName = "ntmgt-neoforge-${property("minecraft_version")}"

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
}

neoForge {
    version = property("neo_version") as String

    parchment {
        minecraftVersion = property("parchment_minecraft_version") as String
        mappingsVersion = property("parchment_mappings_version") as String
    }

    runs {
        create("client") { client() }
        // Dev check before a jar goes into a real instance: joins run/saves/ntmgt-test, quits after 200 ticks.
        create("clientTest") {
            client()
            systemProperty("ntmgt.clienttest", "true")
            jvmArgument("-Xmx4g")
            programArguments.addAll("--quickPlaySingleplayer", "ntmgt-test")
        }
        create("server") { server(); programArgument("--nogui"); systemProperty("ntmgt.selftest", "true") }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        create(modId) { sourceSet(sourceSets.main.get()) }
    }
}

// GTCEu Modern 8.0 SNAPSHOT, NTM: Decentralized Edition and their deps are not on a public maven yet:
// the jars go into libs/ (see libs/README.md). Same jars are used for compiling and for runClient.
// Runtime-only deps for dev runs, not exposed to consumers of this mod.
val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }

val localMods = fileTree("libs") { include("*.jar") }
dependencies {
    compileOnly(localMods)
    localRuntime(localMods)
    // Optional: the rest of a real instance's mods (JEI, Sodium, ...), runtime only, to test against them.
    localRuntime(fileTree("extra-mods") { include("*.jar") })
}

val modProps = mapOf(
    "minecraft_version_range" to property("minecraft_version_range"),
    "neo_version_range" to property("neo_version_range"),
    "mod_id" to modId,
    "mod_name" to property("mod_name"),
    "mod_license" to property("mod_license"),
    "mod_version" to modVersion,
    "mod_authors" to property("mod_authors"),
    "mod_description" to property("mod_description"),
)
tasks.processResources {
    inputs.properties(modProps)
    filesMatching("META-INF/neoforge.mods.toml") { expand(modProps) }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
