dependencies {
    implementation(project(":protocol"))
    implementation(project(":driver-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-cio-jvm:3.5.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(project(":driver-runtime"))
    testImplementation(project(":testkit"))
    testImplementation("io.ktor:ktor-client-core-jvm:3.5.0")
    testImplementation("io.ktor:ktor-client-cio-jvm:3.5.0")
}
