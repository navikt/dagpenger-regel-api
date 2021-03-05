package no.nav.dagpenger.regel.api.models

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.inntekt.v1.Inntekt
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import java.time.YearMonth

internal enum class PeriodeNumber(val number: Int) {
    ONE(1), TWO(2), THREE(3)
}

data class InntektsPeriode(
    val førsteMåned: YearMonth, // todo: rename and create test that verifies the name
    val sisteMåned: YearMonth // todo: rename and create test that verifies the name
) {
    companion object Mapper {

        fun fromPacket(packet: Packet): InntektsPeriode? =
            packet.getNullableObjectValue(PacketKeys.BRUKT_INNTEKTSPERIODE) { json: Any ->
                jacksonObjectMapper.convertValue(json, InntektsPeriode::class.java)
            }

        fun toJson(inntektsPeriode: InntektsPeriode): Any {
            return jacksonObjectMapper.convertValue(
                inntektsPeriode,
                object : TypeReference<Map<String, String>>() {}
            )
        }
    }

    fun toJson(): Any = toJson(this)
}

internal fun inntektFrom(packet: Packet): Inntekt? = packet.getNullableObjectValue(PacketKeys.INNTEKT) { json ->
    jacksonObjectMapper.convertValue(json, Inntekt::class.java)
}

internal fun Inntekt.harAvvik(): Boolean = this.inntektsListe.any { it.harAvvik ?: false }
