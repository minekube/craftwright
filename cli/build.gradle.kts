plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":daemon"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(project(":driver-api"))
    testImplementation(project(":testkit"))
    testImplementation("io.ktor:ktor-server-core-jvm:3.5.0")
    testImplementation("io.ktor:ktor-server-cio-jvm:3.5.0")
}

application {
    applicationName = "craftless"
    mainClass.set("com.minekube.craftless.cli.MainKt")
}
