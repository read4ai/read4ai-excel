plugins {
    kotlin("jvm") version "2.3.20"
    `java-library`
    `maven-publish`
}

val poiVersion: String by project
val twelvemonkeysVersion: String by project
val kotlinLoggingVersion: String by project
val jacksonVersion: String by project
val kotestVersion: String by project

group = "ai.read4ai"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
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
        }
    }
}
