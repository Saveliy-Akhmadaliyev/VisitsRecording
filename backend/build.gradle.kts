plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
}

group = "ru.plasmadynamics"
version = "0.1.0"

application {
    mainClass.set("ru.plasmadynamics.visits.ApplicationKt")
}

repositories {
    mavenCentral()
}

val exposedVersion = "0.55.0"

dependencies {
    // Ktor (версии берутся из BOM, который добавляет io.ktor.plugin)
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // База данных
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Прочее
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Тесты
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}
