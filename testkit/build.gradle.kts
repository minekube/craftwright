dependencies {
    implementation(project(":protocol"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    testImplementation("io.ktor:ktor-client-mock-jvm:3.5.0")
}

tasks.register<JavaExec>("localMinecraftServerSmoke") {
    group = "verification"
    description = "Opt-in local Minecraft server smoke. Set CRAFTLESS_LOCAL_SERVER_SMOKE=1 to provision and start the server."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.minekube.craftless.testkit.LocalMinecraftServerSmokeKt")
}
