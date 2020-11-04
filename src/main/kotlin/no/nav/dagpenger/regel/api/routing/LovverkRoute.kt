package no.nav.dagpenger.regel.api.routing

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.routing.route
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.models.Behov
import no.nav.dagpenger.regel.api.models.BehovId
import no.nav.dagpenger.regel.api.models.InternBehov
import no.nav.dagpenger.regel.api.models.Kontekst
import no.nav.dagpenger.regel.api.models.RegelKontekst
import no.nav.dagpenger.regel.api.models.Status
import no.nav.dagpenger.regel.api.models.Subsumsjon
import no.nav.dagpenger.regel.api.models.SubsumsjonId
import no.nav.dagpenger.regel.api.streams.DagpengerBehovProducer
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

internal fun Routing.lovverk(store: SubsumsjonStore, producer: DagpengerBehovProducer) {
    suspend fun Subsumsjon.måReberegnes(beregningsdato: LocalDate): Boolean {
        store.getBehov(this.behovId).let { internBehov ->
            val behov = store.opprettBehov(internBehov.tilBehov(beregningsdato))
            producer.produceEvent(behov)
            if (store.sjekkResultat(behov.behovId, this)) {
                return true
            }
        }
        return false
    }

    authenticate {
        route("/lovverk/vurdering") {
            post("/minsteinntekt") {
                call.receive<KreverNyBehandlingParametre>().apply {
                    val beregningsdato = beregningsdato
                    val subsumsjonIder = subsumsjonIder.map { SubsumsjonId(it) }
                    store.getSubsumsjonerByResults(subsumsjonIder)
                        .any { subsumsjon -> subsumsjon.måReberegnes(beregningsdato) }
                        .let { call.respond(KreverNyVurdering(it)) }
                }.also {
                    LOGGER.info("Vurder om minsteinntekt må reberegnes for subsumsjoner ${it.subsumsjonIder} beregningsdato ${it.beregningsdato}.")
                }
            }
        }
    }
}

private const val UBRUKT_VEDTAK_ID = -9999

private fun InternBehov.tilBehov(beregningsdato: LocalDate) =
    Behov(
        regelkontekst = RegelKontekst(this.behovId.id, Kontekst.REVURDERING), // TODO: Underøsk litt mer, GA mente ikke var i bruk
        aktørId = this.aktørId,
        vedtakId = UBRUKT_VEDTAK_ID, // Skal bli slettet så lenge vedtakId ikke er markert i bruk
        beregningsDato = beregningsdato,
        harAvtjentVerneplikt = this.harAvtjentVerneplikt,
        oppfyllerKravTilFangstOgFisk = this.oppfyllerKravTilFangstOgFisk,
        bruktInntektsPeriode = this.bruktInntektsPeriode,
        antallBarn = this.antallBarn,
        manueltGrunnlag = this.manueltGrunnlag,
        inntektsId = this.inntektsId,
        lærling = this.lærling
    )

suspend fun SubsumsjonStore.sjekkResultat(behovId: BehovId, subsumsjon: Subsumsjon): Boolean {
    repeat(15) {
        LOGGER.info("Sjekker resultat. Runde: $it. For behov: $behovId og subsumsjon: $subsumsjon")
        when (this.behovStatus(behovId)) {
            is Status.Done -> return !(this.getSubsumsjon(behovId) sammeMinsteinntektResultatSom subsumsjon)
            is Status.Pending -> delay(1000)
        }
    }
    throw BehovTimeoutException()
}

class BehovTimeoutException : RuntimeException("Timet ut ved henting av behov")

private data class KreverNyVurdering(val nyVurdering: Boolean)

data class KreverNyBehandlingParametre(val subsumsjonIder: List<String>, val beregningsdato: LocalDate)
