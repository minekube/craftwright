import org.gradle.api.plugins.JavaPluginExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("dev.detekt") version "2.0.0-alpha.5" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.12" apply false
}

group = "com.minekube.craftless"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<DetektExtension>("detekt") {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.0.0")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
}

tasks.register("lint") {
    group = "verification"
    description = "Run Kotlin formatting and static-analysis checks"
    dependsOn(subprojects.map { it.tasks.named("compileKotlin") })
    dependsOn(subprojects.map { it.tasks.named("compileTestKotlin") })
    dependsOn(subprojects.map { it.tasks.named("ktlintCheck") })
    dependsOn(subprojects.map { it.tasks.named("detekt") })
}

tasks.register("lintFormat") {
    group = "formatting"
    description = "Auto-format Kotlin sources with ktlint"
    dependsOn(subprojects.map { it.tasks.named("ktlintFormat") })
}
