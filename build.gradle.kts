plugins {
    kotlin("jvm") version "2.3.20"
    `java-library`
    `maven-publish`
    signing
}

val poiVersion: String by project
val twelvemonkeysVersion: String by project
val kotlinLoggingVersion: String by project
val jacksonVersion: String by project
val kotestVersion: String by project

group = "io.github.hyune-c"
version = "0.3.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Apache POI
    api("org.apache.poi:poi:$poiVersion")
    api("org.apache.poi:poi-ooxml:$poiVersion")
    implementation("org.apache.poi:poi-scratchpad:$poiVersion")

    // Jackson 3
    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Logging
    implementation("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")

    // TwelveMonkeys ImageIO (for extended image format support)
    implementation("com.twelvemonkeys.imageio:imageio-bmp:$twelvemonkeysVersion")
    implementation("com.twelvemonkeys.imageio:imageio-tiff:$twelvemonkeysVersion")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "read4ai-excel"

            pom {
                name.set("read4ai-excel")
                description.set("A structure-preserving Excel parser for merged cells, multi-table sheets, and structured JSON output.")
                url.set("https://github.com/read4ai/read4ai-excel")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("Hyune-c")
                        name.set("Hyune-c")
                        url.set("https://github.com/Hyune-c")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/read4ai/read4ai-excel.git")
                    developerConnection.set("scm:git:ssh://git@github.com/read4ai/read4ai-excel.git")
                    url.set("https://github.com/read4ai/read4ai-excel")
                }
            }
        }
    }

    repositories {
        maven {
            name = "release"
            val repositoryUrl = providers.gradleProperty("mavenRepositoryUrl")
                .orElse(providers.environmentVariable("MAVEN_REPOSITORY_URL"))
                .orNull
            url = uri(repositoryUrl ?: layout.buildDirectory.dir("maven-repo").get().asFile)

            val repositoryUsername = providers.gradleProperty("mavenRepositoryUsername")
                .orElse(providers.environmentVariable("MAVEN_REPOSITORY_USERNAME"))
                .orNull
            val repositoryPassword = providers.gradleProperty("mavenRepositoryPassword")
                .orElse(providers.environmentVariable("MAVEN_REPOSITORY_PASSWORD"))
                .orNull

            if (repositoryUrl != null && repositoryUsername != null && repositoryPassword != null) {
                credentials {
                    username = repositoryUsername
                    password = repositoryPassword
                }
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingInMemoryKey")
        .orElse(providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey"))
        .orNull
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")
        .orElse(providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword"))
        .orNull

    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
