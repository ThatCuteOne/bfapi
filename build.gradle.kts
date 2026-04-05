import java.net.URI
import java.util.Properties
import java.time.Instant
import groovy.json.JsonSlurper
plugins {
    java
    application
}

group = "dev.vuis"
version = "1.0-SNAPSHOT"

val blockfrontModVersion: String by project
val blockfrontLibVersion: String by project
val extractedDir = layout.buildDirectory.dir("extracted")

repositories {
    maven("https://api.modrinth.com/maven")
    mavenCentral()
}

configurations {
    create("outerJar")
}

dependencies {
    "outerJar"("maven.modrinth:blockfront:${blockfrontModVersion}")
    implementation(files("build/extracted/blockfront-library.jar"))
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-api:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")

    implementation("io.netty:netty-all:4.2.9.Final")
    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("it.unimi.dsi:fastutil:8.5.18")
    implementation("net.raphimc:MinecraftAuth:5.0.0")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("setVersionData") {
    doLast {
        val url = URI("http://cloud.blockfrontmc.com:8001/api/v2/?type=version").toURL()
        val responseText = url.readText()
        val jsonSlurper = JsonSlurper()
        val jsonResponse = jsonSlurper.parseText(responseText) as Map<*, *>

        val newestVersionHash = jsonResponse["hash"].toString()
        val newestBlockfrontVersion = jsonResponse["version"].toString()

        val versionPropsFile = file("src/main/resources/version.properties")
        versionPropsFile.parentFile.mkdirs()
        val props = Properties()
        props.setProperty("blockfront.version", newestBlockfrontVersion)
        props.setProperty("blockfront.version.hash", newestVersionHash)
        props.setProperty("blockfront.compile.version", blockfrontModVersion)
        props.setProperty("blockfront.compile.lib_version", blockfrontLibVersion)
        props.setProperty("build.time",Instant.now().toString())
        versionPropsFile.writer().use { writer ->
            props.store(writer, "Build version information")
        }

    }
}

application {
    mainClass = "dev.vuis.bfapi.main.ApiMain"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.register<Copy>("extractInnerJar") {
    dependsOn(configurations["outerJar"])
    
    val outerJar = configurations["outerJar"].resolve().firstOrNull()
    if (outerJar != null) {
        from(zipTree(outerJar))
        include("META-INF/jarjar/com.boehmod.blockfront.BlockFrontLibrary-${blockfrontLibVersion}.jar")
        into(layout.buildDirectory.dir("extracted"))
        eachFile {
            path = name
        }
        rename(".*", "blockfront-library.jar")
    }
    outputs.dir(layout.buildDirectory.dir("extracted"))
}

tasks.compileJava {
    dependsOn("extractInnerJar")
    dependsOn("setVersionData")
}

tasks.clean {
    delete(layout.buildDirectory.dir("extracted"))
}
