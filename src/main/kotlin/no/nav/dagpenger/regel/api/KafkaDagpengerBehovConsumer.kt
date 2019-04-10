package no.nav.dagpenger.regel.api

import mu.KotlinLogging
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.models.GrunnlagFaktum
import no.nav.dagpenger.regel.api.models.GrunnlagResultat
import no.nav.dagpenger.regel.api.models.GrunnlagSubsumsjon
import no.nav.dagpenger.regel.api.models.InntektResponse
import no.nav.dagpenger.regel.api.models.InntektResponseGrunnlag
import no.nav.dagpenger.regel.api.models.InntektsPeriode
import no.nav.dagpenger.regel.api.models.MinsteinntektFaktum
import no.nav.dagpenger.regel.api.models.MinsteinntektResultat
import no.nav.dagpenger.regel.api.models.MinsteinntektSubsumsjon
import no.nav.dagpenger.regel.api.models.PeriodeFaktum
import no.nav.dagpenger.regel.api.models.PeriodeResultat
import no.nav.dagpenger.regel.api.models.PeriodeSubsumsjon
import no.nav.dagpenger.regel.api.models.SatsFaktum
import no.nav.dagpenger.regel.api.models.SatsResultat
import no.nav.dagpenger.regel.api.models.SatsSubsumsjon
import no.nav.dagpenger.regel.api.monitoring.HealthCheck
import no.nav.dagpenger.regel.api.monitoring.HealthStatus
import no.nav.dagpenger.streams.Topics.DAGPENGER_BEHOV_PACKET_EVENT
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

class KafkaDagpengerBehovConsumer(
    private val config: Configuration,
    private val store: SubsumsjonStore
) : HealthCheck {

    private lateinit var streams: KafkaStreams
    fun start() {
        LOGGER.info { "Starting up $APPLICATION_NAME kafca consumer" }
        streams = KafkaStreams(buildTopology(), this.getConfig())
        streams.setUncaughtExceptionHandler { _, _ -> System.exit(0) }
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

    internal fun buildTopology(): Topology {
        val builder = StreamsBuilder()

        val stream = builder.stream(
            DAGPENGER_BEHOV_PACKET_EVENT.name,
            Consumed.with(Serdes.StringSerde(), Serdes.serdeFrom(JsonSerializer(), JsonDeserializer()))
        )

        stream
            .peek { _, value -> LOGGER.info("Consuming behov with id $value") }
            .foreach { _, behov -> storeResult(behov) }

        return builder.build()
    }

    private fun getConfig() = streamConfig(
        appId = APPLICATION_NAME,
        bootStapServerUrl = config.kafka.brokers,
        credential = config.kafka.credential()
    )

    private fun hasNeededMinsteinntektResultat(behov: SubsumsjonsBehov): Boolean {
        return behov.minsteinntektResultat != null && store.behovStatus(behov.behovId, Regel.MINSTEINNTEKT) == Status.Pending
    }

    private fun hasNeededPeriodeResultat(behov: SubsumsjonsBehov): Boolean {
        return behov.periodeResultat != null && store.behovStatus(behov.behovId, Regel.PERIODE) == Status.Pending
    }

    private fun hasNeededGrunnlagResultat(behov: SubsumsjonsBehov): Boolean {
        return behov.grunnlagResultat != null && store.behovStatus(behov.behovId, Regel.GRUNNLAG) == Status.Pending
    }

    private fun hasNeededSatsResultat(behov: SubsumsjonsBehov): Boolean {
        return behov.satsResultat != null && store.behovStatus(behov.behovId, Regel.SATS) == Status.Pending
    }

    private fun storeResult(behov: SubsumsjonsBehov) {
        try {
            when {
                hasNeededMinsteinntektResultat(behov) -> storeMinsteinntektSubsumsjon(behov)
                hasNeededPeriodeResultat(behov) -> storePeriodeSubsumsjon(behov)
                hasNeededGrunnlagResultat(behov) -> storeGrunnlagSubsumsjon(behov)
                hasNeededSatsResultat(behov) -> storeSatsSubsumsjon(behov)
                else -> LOGGER.info("Ignoring behov with id ${behov.behovId}")
            }
        } catch (e: RuntimeException) {
            //todo I am ashamed of myself
            LOGGER.error { "Error for behov: ${behov.behovId}. Error: $e" }
        }
    }

    private fun storeMinsteinntektSubsumsjon(behov: SubsumsjonsBehov) = mapToMinsteinntektSubsumsjon(behov).also {
        store.insertSubsumsjon(it)
    }

    private fun storePeriodeSubsumsjon(behov: SubsumsjonsBehov) = mapToPeriodeSubsumsjon(behov).also {
        store.insertSubsumsjon(it)
    }

    private fun storeGrunnlagSubsumsjon(behov: SubsumsjonsBehov) = mapToGrunnlagSubsumsjon(behov).also {
        store.insertSubsumsjon(it)
    }

    private fun storeSatsSubsumsjon(behov: SubsumsjonsBehov) = mapToSatsSubsumsjon(behov).also {
        store.insertSubsumsjon(it)
    }

    private fun mapToMinsteinntektSubsumsjon(behov: SubsumsjonsBehov): MinsteinntektSubsumsjon {
        val minsteinntektResultat = behov.minsteinntektResultat!!
        val inntekt = behov.inntektV1
        val inntektsperioder = behov.minsteinntektInntektsPerioder ?: getEmptyInntektsPerioder()
        return MinsteinntektSubsumsjon(
            minsteinntektResultat.subsumsjonsId,
            behov.behovId,
            Regel.MINSTEINNTEKT,
            LocalDateTime.now(),
            LocalDateTime.now(),
            MinsteinntektFaktum(
                behov.aktørId,
                behov.vedtakId,
                behov.beregningsDato,
                inntekt?.inntektsId ?: "12345", // fixme
                behov.harAvtjentVerneplikt),
            MinsteinntektResultat(minsteinntektResultat.oppfyllerMinsteinntekt),
            inntektsperioder // fixme
        )
    }

    private fun mapToPeriodeSubsumsjon(behov: SubsumsjonsBehov): PeriodeSubsumsjon {
        val periodeResultat = behov.periodeResultat!!
        val inntekt = behov.inntektV1
        return PeriodeSubsumsjon(
            periodeResultat.subsumsjonsId,
            behov.behovId,
            Regel.PERIODE,
            LocalDateTime.now(),
            LocalDateTime.now(),
            PeriodeFaktum(
                behov.aktørId,
                behov.vedtakId,
                behov.beregningsDato,
                inntekt?.inntektsId ?: "12345", // fixme
                behov.harAvtjentVerneplikt),
            PeriodeResultat(periodeResultat.periodeAntallUker)
        )
    }

    private fun mapToGrunnlagSubsumsjon(behov: SubsumsjonsBehov): GrunnlagSubsumsjon {
        val grunnlagResultat = behov.grunnlagResultat!!
        val inntekt = behov.inntektV1
        val inntektsperioder = behov.grunnlagInntektsPerioder
        return GrunnlagSubsumsjon(
            grunnlagResultat.subsumsjonsId,
            behov.behovId,
            Regel.GRUNNLAG,
            LocalDateTime.now(),
            LocalDateTime.now(),
            GrunnlagFaktum(
                behov.aktørId,
                behov.vedtakId,
                behov.beregningsDato,
                inntekt?.inntektsId,
                behov.harAvtjentVerneplikt,
                manueltGrunnlag = behov.manueltGrunnlag),
            GrunnlagResultat(grunnlagResultat.avkortet, grunnlagResultat.uavkortet, grunnlagResultat.beregningsregel, grunnlagResultat.harAvkortet),
            inntektsperioder
        )
    }

    // TODO ADAPT TO PACKET
    private fun getEmptyInntektsPerioder(): Set<InntektResponse> = setOf(
        InntektResponse(
            inntekt = BigDecimal.ZERO,
            periode = 1,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2018, 2), YearMonth.of(2019, 1)),
            inneholderFangstOgFisk = false,
            andel = BigDecimal.ZERO
        ),
        InntektResponse(
            inntekt = BigDecimal.ZERO,
            periode = 2,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2017, 2), YearMonth.of(2018, 1)),
            inneholderFangstOgFisk = false,
            andel = BigDecimal.ZERO
        ),
        InntektResponse(
            inntekt = BigDecimal.ZERO,
            periode = 3,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2016, 2), YearMonth.of(2017, 1)),
            inneholderFangstOgFisk = false,
            andel = BigDecimal.ZERO
        )
    )

    // TODO ADAPT TO PACKET
    private fun getEmptyInntektsPerioderGrunnlag(): Set<InntektResponseGrunnlag> = setOf(
        InntektResponseGrunnlag(
            inntekt = BigDecimal.ZERO,
            periode = 1,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2018, 2), YearMonth.of(2019, 1)),
            inneholderFangstOgFisk = false
        ),
        InntektResponseGrunnlag(
            inntekt = BigDecimal.ZERO,
            periode = 2,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2017, 2), YearMonth.of(2018, 1)),
            inneholderFangstOgFisk = false
        ),
        InntektResponseGrunnlag(
            inntekt = BigDecimal.ZERO,
            periode = 3,
            inntektsPeriode = InntektsPeriode(YearMonth.of(2016, 2), YearMonth.of(2017, 1)),
            inneholderFangstOgFisk = false
        )
    )

    private fun mapToSatsSubsumsjon(behov: SubsumsjonsBehov): SatsSubsumsjon {
        val satsResultat = behov.satsResultat!!
        return SatsSubsumsjon(
            satsResultat.subsumsjonsId,
            behov.behovId,
            Regel.SATS,
            LocalDateTime.now(),
            LocalDateTime.now(),
            SatsFaktum(behov.aktørId, behov.vedtakId, behov.beregningsDato, behov.manueltGrunnlag, behov.antallBarn),
            SatsResultat(satsResultat.dagsats, satsResultat.ukesats, satsResultat.benyttet90ProsentRegel)
        )
    }
}