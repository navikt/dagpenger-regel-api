package no.nav.dagpenger.regel.api.streams

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.regel.api.APPLICATION_NAME
import no.nav.dagpenger.regel.api.Configuration
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.models.PacketKeys
import no.nav.dagpenger.regel.api.models.Status
import no.nav.dagpenger.regel.api.models.Subsumsjon.Mapper.subsumsjonFrom
import no.nav.dagpenger.regel.api.models.getBehovId
import no.nav.dagpenger.regel.api.monitoring.HealthCheck
import no.nav.dagpenger.regel.api.monitoring.HealthStatus
import no.nav.dagpenger.streams.Pond
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.Predicate
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

internal class KafkaSubsumsjonConsumer(
    private val config: Configuration,
    private val subsumsjonStore: SubsumsjonStore
) : HealthCheck {

    private val streams: KafkaStreams by lazy {
        KafkaStreams(SumsumsjonPond(subsumsjonStore, APPLICATION_NAME).buildTopology(), this.getConfig()).apply {
            setUncaughtExceptionHandler { _, _ -> System.exit(0) }
        }
    }

    fun start() {
        LOGGER.info { "Starting up $APPLICATION_NAME kafca consumer" }
        streams.start()
    }

    fun stop() {
        LOGGER.info { "Shutting down $APPLICATION_NAME kafka consumer" }
        streams.close(3, TimeUnit.SECONDS)
        streams.cleanUp()
    }

    override fun status(): HealthStatus {
        return when (streams.state()) {
            KafkaStreams.State.ERROR -> HealthStatus.DOWN
            KafkaStreams.State.PENDING_SHUTDOWN -> HealthStatus.DOWN
            else -> HealthStatus.UP
        }
    }

    private fun getConfig() = streamConfig(
        appId = APPLICATION_NAME,
        bootStapServerUrl = config.kafka.brokers,
        credential = config.kafka.credential()
    )
}

internal class SumsumsjonPond(private val subsumsjonStore: SubsumsjonStore, override val SERVICE_APP_ID: String) : Pond() {

    val isManuellGrunnlag = { packet: Packet -> packet.hasField(PacketKeys.MANUELT_GRUNNLAG) && hasGrunnlagAndSatsResult(packet) }

    val hasGrunnlagAndSatsResult = { packet: Packet -> packet.hasField(PacketKeys.GRUNNLAG_RESULTAT) && packet.hasField(PacketKeys.SATS_RESULTAT) }

    val hasCompleteResult = { packet: Packet -> hasGrunnlagAndSatsResult(packet) && packet.hasField(PacketKeys.MINSTEINNTEKT_RESULTAT) && packet.hasField(PacketKeys.PERIODE_RESULTAT) }

    override fun filterPredicates(): List<Predicate<String, Packet>> {
        return listOf(Predicate { _, packet: Packet ->
            isManuellGrunnlag(packet) || hasCompleteResult(packet)
        })
    }

    override fun onPacket(packet: Packet) {
        runCatching {
            when (subsumsjonStore.behovStatus(packet.getBehovId())) {
                Status.Pending -> subsumsjonStore.insertSubsumsjon(subsumsjonFrom(packet))
                else -> LOGGER.info { "Ignoring done subsumsjon. Packet: $packet" }
            }
        }.onFailure { t ->
            //todo add prometheus counter?
            LOGGER.error(t) { "Failure handling packet: $packet" }
        }
    }
}
