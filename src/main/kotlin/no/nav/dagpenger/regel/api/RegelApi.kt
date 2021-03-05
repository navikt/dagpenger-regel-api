package no.nav.dagpenger.regel.api

import com.fasterxml.jackson.databind.JsonMappingException
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.Locations
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.pipeline.PipelineContext
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.ktor.auth.apiKeyAuth
import no.nav.dagpenger.regel.api.auth.AuthApiKeyVerifier
import no.nav.dagpenger.regel.api.auth.azureAdJWT
import no.nav.dagpenger.regel.api.db.BehovNotFoundException
import no.nav.dagpenger.regel.api.db.PostgresBruktSubsumsjonStore
import no.nav.dagpenger.regel.api.db.PostgresSubsumsjonStore
import no.nav.dagpenger.regel.api.db.SubsumsjonNotFoundException
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.db.dataSourceFrom
import no.nav.dagpenger.regel.api.db.migrate
import no.nav.dagpenger.regel.api.models.IllegalUlidException
import no.nav.dagpenger.regel.api.monitoring.HealthCheck
import no.nav.dagpenger.regel.api.routing.behov
import no.nav.dagpenger.regel.api.routing.lovverk
import no.nav.dagpenger.regel.api.routing.metrics
import no.nav.dagpenger.regel.api.routing.naischecks
import no.nav.dagpenger.regel.api.routing.subsumsjon
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import no.nav.dagpenger.regel.api.streams.DagpengerBehovProducer
import no.nav.dagpenger.regel.api.streams.KafkaDagpengerBehovProducer
import no.nav.dagpenger.regel.api.streams.KafkaSubsumsjonBruktConsumer
import no.nav.dagpenger.regel.api.streams.KafkaSubsumsjonConsumer
import no.nav.dagpenger.regel.api.streams.SubsumsjonPond
import no.nav.dagpenger.regel.api.streams.producerConfig
import no.nav.dagpenger.regel.api.streams.subsumsjonPacketStrategies
import org.slf4j.event.Level
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

private val MAINLOGGER = KotlinLogging.logger {}

fun main() {
    val config = Configuration()

    migrate(config)
    val dataSource = dataSourceFrom(config)
    val subsumsjonStore = PostgresSubsumsjonStore(dataSource)
    val bruktSubsumsjonStore = PostgresBruktSubsumsjonStore(dataSource)
    val vaktmester = Vaktmester(dataSource = dataSource, subsumsjonStore = subsumsjonStore)

    fixedRateTimer(
        name = "vaktmester",
        initialDelay = TimeUnit.MINUTES.toMillis(10),
        period = TimeUnit.HOURS.toMillis(12),
        action = {
            MAINLOGGER.info { "Vaktmesteren rydder IKKE" }
            // vaktmester.rydd()
            MAINLOGGER.info { "Vaktmesteren er ferdig... for denne gang" }
        }
    )

    val kafkaConsumer =
        KafkaSubsumsjonConsumer(config, SubsumsjonPond(subsumsjonPacketStrategies(subsumsjonStore), config)).also {
            it.start()
        }

    val bruktSubsumsjonConsumer = KafkaSubsumsjonBruktConsumer.apply {
        create(
            config = config,
            bruktSubsumsjonStore = bruktSubsumsjonStore,
            vaktmester = vaktmester
        )
        listen()
    }
    val kafkaProducer = KafkaDagpengerBehovProducer(
        producerConfig(
            config.application.id,
            config.kafka.brokers,
            config.kafka.credential()
        ),
        config.behovTopic

    )

    val app = embeddedServer(Netty, port = config.application.httpPort) {
        api(
            subsumsjonStore,
            kafkaProducer,
            config.auth.authApiKeyVerifier,
            listOf(
                subsumsjonStore as HealthCheck,
                bruktSubsumsjonStore as HealthCheck,
                kafkaConsumer as HealthCheck,
                kafkaProducer as HealthCheck,
                bruktSubsumsjonConsumer as HealthCheck
            ),
            config
        )
    }.also {
        it.start(wait = false)
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            kafkaConsumer.stop()
            bruktSubsumsjonConsumer.cancel()
            app.stop(10000, 60000)
        }
    )
}

internal fun Application.api(
    subsumsjonStore: SubsumsjonStore,
    kafkaProducer: DagpengerBehovProducer,
    apiAuthApiKeyVerifier: AuthApiKeyVerifier,
    healthChecks: List<HealthCheck>,
    config: Configuration
) {
    install(DefaultHeaders)

    install(Authentication) {
        apiKeyAuth(name = "X-API-KEY") {
            apiKeyName = "X-API-KEY"
            validate { creds -> apiAuthApiKeyVerifier.verify(creds) }
        }

        jwt(name = "jwt") {
            azureAdJWT(
                providerUrl = config.auth.azureAppWellKnownUrl,
                realm = config.application.id,
                clientId = config.auth.azureAppClientId
            )
        }
    }

    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            !call.request.path().startsWith("/isAlive") &&
                !call.request.path().startsWith("/isReady") &&
                !call.request.path().startsWith("/metrics")
        }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(jacksonObjectMapper))
    }

    install(Locations)

    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)
    }

    install(StatusPages) {
        exception<BadRequestException> { cause ->
            badRequest(cause)
        }

        exception<JsonMappingException> { cause ->
            badRequest(cause)
        }

        exception<BehovNotFoundException> { cause ->
            notFound(cause)
        }
        exception<SubsumsjonNotFoundException> { cause ->
            notFound(cause)
        }
        exception<IllegalUlidException> { cause ->
            badRequest(cause)
        }
    }

    routing {
        naischecks(healthChecks)
        metrics()

        authenticate("X-API-KEY") {
            subsumsjon(subsumsjonStore)
            lovverk(subsumsjonStore, kafkaProducer)
            behov(subsumsjonStore, kafkaProducer)
        }

        authenticate("jwt") {
            route("/secured") {
                get {
                    call.respond(HttpStatusCode.OK, "Ok")
                }
            }
        }
    }
}

private suspend fun <T : Throwable> PipelineContext<Unit, ApplicationCall>.errorHandler(
    cause: T,
    httpStatusCode: HttpStatusCode
): Unit = withContext(Dispatchers.IO) {
    call.respond(httpStatusCode)
    throw cause
}

private suspend fun <T : Throwable> PipelineContext<Unit, ApplicationCall>.badRequest(
    cause: T
) = errorHandler(cause, HttpStatusCode.BadRequest)

private suspend fun <T : Throwable> PipelineContext<Unit, ApplicationCall>.notFound(
    cause: T
) = errorHandler(cause, HttpStatusCode.NotFound)

class BadRequestException : RuntimeException()
