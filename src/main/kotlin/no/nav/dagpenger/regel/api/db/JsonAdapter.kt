package no.nav.dagpenger.regel.api.db

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.events.Problem
import no.nav.dagpenger.regel.api.models.BehovId
import no.nav.dagpenger.regel.api.models.Faktum
import no.nav.dagpenger.regel.api.models.InntektsPeriode
import no.nav.dagpenger.regel.api.models.Kontekst
import no.nav.dagpenger.regel.api.models.RegelKontekst
import no.nav.dagpenger.regel.api.models.Subsumsjon
import no.nav.dagpenger.regel.api.models.SubsumsjonSerDerException
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal object JsonAdapter {

    fun fromJson(jsonString: String): Subsumsjon {
        try {
            return jacksonObjectMapper.readTree(jsonString).let {
                Subsumsjon(
                    behovId = BehovId(it["behovId"].asText()),
                    faktum = getFaktum(it),
                    grunnlagResultat = it.getOrNull("grunnlagResultat")?.asMap(),
                    minsteinntektResultat = it.getOrNull("minsteinntektResultat")?.asMap(),
                    satsResultat = it.getOrNull("satsResultat")?.asMap(),
                    periodeResultat = it.getOrNull("periodeResultat")?.asMap(),
                    problem = it.getOrNull("problem")?.asProblem()
                )
            }
        } catch (e: Exception) {
            throw SubsumsjonSerDerException("Unable to deserialize: $jsonString", e)
        }
    }

    private fun JsonNode.asLocalDate(): LocalDate = LocalDate.parse(this.asText(), DateTimeFormatter.ISO_LOCAL_DATE)

    private fun JsonNode.asYearMonth(): YearMonth = YearMonth.parse(this.asText())

    private fun JsonNode.asMap(): Map<String, Any> =
        jacksonObjectMapper.convertValue(this, object : TypeReference<Map<String, Any>>() {})

    private fun JsonNode.asProblem(): Problem =
        jacksonObjectMapper.convertValue(this, Problem::class.java)

    private fun JsonNode.asInntektsPeriode() = InntektsPeriode(
        førsteMåned = this["førsteMåned"].asYearMonth(),
        sisteMåned = this["sisteMåned"].asYearMonth(),
    )

    private fun getFaktum(json: JsonNode): Faktum {
        val faktum = json["faktum"]
        val regelkontekst = if (faktum.has("vedtakId")) {
            RegelKontekst(faktum["vedtakId"].asText(), Kontekst.VEDTAK)
        } else {
            json["regelkontekst"].let {
                RegelKontekst(it["id"].asText(), Kontekst.valueOf(it["type"].asText()))
            }
        }

        return Faktum(
            aktorId = faktum["aktorId"].asText(),
            regelkontekst = regelkontekst,
            vedtakId = regelkontekst.id.toInt(),
            beregningsdato = faktum["beregningsdato"].asLocalDate(),
            inntektsId = faktum.getOrNull("inntektsId")?.textValue(),
            inntektAvvik = faktum.getOrNull("inntektAvvik")?.asBoolean(),
            inntektManueltRedigert = faktum.getOrNull("inntektManueltRedigert")?.asBoolean(),
            harAvtjentVerneplikt = faktum.getOrNull("harAvtjentVerneplikt")?.asBoolean(),
            oppfyllerKravTilFangstOgFisk = faktum.getOrNull("oppfyllerKravTilFangstOgFisk")?.asBoolean(),
            antallBarn = faktum.getOrNull("antallBarn")?.asInt(),
            manueltGrunnlag = faktum.getOrNull("manueltGrunnlag")?.asInt(),
            lærling = faktum.getOrNull("lærling")?.asBoolean(),
            bruktInntektsPeriode = faktum.getOrNull("bruktInntektsPeriode")?.asInntektsPeriode()
        )
    }

    fun JsonNode.getOrNull(nullableField: String): JsonNode? {
        val nullableNode = this.get(nullableField)
        return if (nullableNode == null || nullableNode.isNull) null
        else nullableNode
    }
}
