import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

buildscript {
    repositories {
        jcenter()
    }
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    jcenter()
    maven("http://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "dp-regel-api"
    mainClassName = "no.nav.dagpenger.regel.api.RegelApiKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations {
    this.all {
        exclude(group = "ch.qos.logback")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Dagpenger.Streams)
    implementation(Dagpenger.Events)

    implementation(Jackson.core)
    implementation(Jackson.kotlin)
    implementation(Jackson.jsr310)
    implementation(Kafka.streams)
    implementation(Kafka.clients)
    implementation(Kafka.streams)

    implementation(Ktor.server)
    implementation(Ktor.serverNetty)
    implementation(Ktor.auth)
    implementation(Ktor.authJwt)
    implementation(Ktor.locations)
    implementation(Ktor.micrometerMetrics)
    implementation(Ktor.library("jackson"))
    implementation(Ktor.library("client-core"))
    implementation(Ktor.library("client-cio"))
    implementation(Dagpenger.Biblioteker.ktorUtils)
    implementation(Micrometer.prometheusRegistry)

    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)
    implementation(Log4j2.Logstash.logstashLayout)
    implementation(Kotlin.Logging.kotlinLogging)

    implementation(Slf4j.api)

    implementation(Ulid.ulid)

    implementation("no.finn.unleash:unleash-client-java:3.2.9")

    implementation(Dagpenger.Streams)
    implementation(Dagpenger.Events)

    implementation(Database.Flyway)
    implementation(Database.HikariCP)
    implementation(Database.Postgres)
    implementation(Database.Kotlinquery)
    implementation(Konfig.konfig)
    implementation(Database.VaultJdbc) {
        exclude(module = "slf4j-simple")
        exclude(module = "slf4j-api")
    }

    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(Prometheus.log4j2)

    runtimeOnly(Vault.javaDriver)

    testImplementation(kotlin("test-junit5"))
    testImplementation(Ktor.ktorTest)
    testImplementation(Junit5.api)
    testImplementation(KoTest.assertions)
    testImplementation(KoTest.property)
    testImplementation(KoTest.runner)
    testImplementation(TestContainers.postgresql)
    testImplementation(TestContainers.kafka)
    testImplementation(Kafka.streamTestUtils)
    testImplementation("no.nav.security:mock-oauth2-server:0.3.1")

    testImplementation(Mockk.mockk)

    testRuntimeOnly(Junit5.engine)
    testRuntimeOnly(Junit5.vintageEngine)
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint(Ktlint.version)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI")
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.0.1"
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
}
