package no.nav.dagpenger.regel.api.db

import no.nav.dagpenger.regel.api.models.*
import java.time.ZoneId
import java.time.ZonedDateTime

interface SubsumsjonStore {

    fun opprettBehov(behov: Behov): InternBehov {
        val eksternId = EksternId(behov.vedtakId.toString(), Kontekst.VEDTAK)
        val behandlingsId = hentKoblingTilEkstern(eksternId)
        val internBehov = InternBehov.fromBehov(behov, behandlingsId)
        insertBehov(internBehov)
        return internBehov
    }

    fun insertBehov(behov: InternBehov): Int
    fun hentKoblingTilEkstern(eksternId: EksternId): BehandlingsId
    fun getBehov(behovId: UlidId): InternBehov
    fun behovStatus(behovId: UlidId): Status
    fun insertSubsumsjon(subsumsjon: Subsumsjon, created: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))): Int
    fun getSubsumsjon(behovId: UlidId): Subsumsjon
    fun getSubsumsjonByResult(subsumsjonId: UlidId): Subsumsjon
    fun delete(subsumsjon: Subsumsjon)
}

class IllegalSubsumsjonIdException(override val message: String) : RuntimeException(message)

internal class SubsumsjonNotFoundException(override val message: String) : RuntimeException(message)

internal class BehovNotFoundException(override val message: String) : RuntimeException(message)

internal class StoreException(override val message: String) : RuntimeException(message)
