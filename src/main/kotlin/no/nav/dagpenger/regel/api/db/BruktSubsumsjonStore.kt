package no.nav.dagpenger.regel.api.db

import mu.KotlinLogging
import no.nav.dagpenger.regel.api.models.SubsumsjonId
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import java.time.ZonedDateTime

interface BruktSubsumsjonStore {
    fun insertSubsumsjonBrukt(internSubsumsjonBrukt: InternSubsumsjonBrukt): Int
    fun getSubsumsjonBrukt(subsumsjonId: SubsumsjonId): InternSubsumsjonBrukt?
    fun listSubsumsjonBrukt(): List<InternSubsumsjonBrukt>
    fun subsumsjonBruktFraBehandlingsId(behandlingsId: String): List<InternSubsumsjonBrukt>
    fun eksternTilInternSubsumsjon(eksternSubsumsjonBrukt: EksternSubsumsjonBrukt): InternSubsumsjonBrukt
}

data class EksternSubsumsjonBrukt(
    val id: String,
    val eksternId: Long,
    val arenaTs: ZonedDateTime,
    val ts: Long,
    val utfall: String? = null,
    val vedtakStatus: String? = null
) {
    companion object Mapper {
        private val LOGGER = KotlinLogging.logger { }
        fun fromJson(json: String): EksternSubsumsjonBrukt? {
            return runCatching { jacksonObjectMapper.readValue(json, EksternSubsumsjonBrukt::class.java) }.onFailure { e -> LOGGER.warn("Failed to convert string to object", e) }.getOrNull()
        }
    }

    fun toJson(): String {
        return jacksonObjectMapper.writeValueAsString(this)
    }
}

data class InternSubsumsjonBrukt(val id: String, val behandlingsId: String, val arenaTs: ZonedDateTime, val created: ZonedDateTime? = null) {
    companion object Mapper {
        private val LOGGER = KotlinLogging.logger { }
        fun fromJson(json: String): InternSubsumsjonBrukt? {
            return runCatching { jacksonObjectMapper.readValue(json, InternSubsumsjonBrukt::class.java) }.onFailure { e -> LOGGER.warn("Failed to convert string to object", e) }.getOrNull()
        }
    }

    fun toJson(): String {
        return jacksonObjectMapper.writeValueAsString(this)
    }
}

internal class SubsumsjonBruktNotFoundException(override val message: String) : RuntimeException(message)
