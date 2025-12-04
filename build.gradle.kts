plugins {
    id("fabric-loom") version "1.10.3"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
loom {
    accessWidenerPath = file("src/main/resources/pathmind.accesswidener")
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

val baritoneApiJar: File by extra {
    val candidates = listOfNotNull(
        System.getenv("BARITONE_API_JAR"),
        project.findProperty("baritoneApiPath") as? String,
        "libs/baritone-api-fabric-1.15.0.jar",
        "run/mods/baritone-api-fabric-1.15.0.jar",
        "/Users/holdenthomas/Documents/baritone/dist/baritone-api-fabric-1.15.0.jar"
    ).map { file(it) }

    candidates.firstOrNull { it.exists() }
        ?: throw org.gradle.api.GradleException(
            "Baritone API jar not found. Set BARITONE_API_JAR, provide -PbaritoneApiPath, or place the jar in libs/."
        )
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // Baritone API dependency
    val baritoneApi = files(baritoneApiJar)
    modImplementation(baritoneApi)

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.processResources {
    val properties = mapOf(
        "version" to version,
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("loader_version"),
        "fabric_api_version" to project.property("fabric_api_version")
    )
    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

val targetJavaVersionInt = JavaVersion.toVersion(targetJavaVersion)
tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    if (targetJavaVersionInt >= JavaVersion.VERSION_1_9) {
        options.release.set(targetJavaVersion)
    }
}

java {
    withSourcesJar()

    if (JavaVersion.current() < JavaVersion.toVersion(targetJavaVersion)) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }

    // Bundle the Baritone API jar as a nested library
    from(baritoneApiJar) {
        into("META-INF/jars")
        rename { "baritone-api-fabric-1.15.0.jar" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
