plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":daemon"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("io.ktor:ktor-client-core-jvm:3.5.0")
    testImplementation("io.ktor:ktor-client-cio-jvm:3.5.0")
}

application {
    mainClass.set("dev.minekube.craftwright.cli.MainKt")
}
