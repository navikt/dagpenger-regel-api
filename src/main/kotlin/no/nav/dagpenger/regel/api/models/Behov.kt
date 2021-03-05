package no.nav.dagpenger.regel.api.models

import de.huxhorn.sulky.ulid.ULID
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import java.time.LocalDate

internal val ulidGenerator = ULID()

data class Behov(
    val regelkontekst: RegelKontekst,
    val aktørId: String,
    val beregningsDato: LocalDate,
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: InntektsPeriode? = null,
    val antallBarn: Int? = null,
    val manueltGrunnlag: Int? = null,
    val inntektsId: String? = null,
    val lærling: Boolean? = null
)

data class InternBehov(
    val behovId: BehovId = BehovId(ulidGenerator.nextULID()),
    val aktørId: String,
    val behandlingsId: BehandlingsId,
    val beregningsDato: LocalDate,
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: InntektsPeriode? = null,
    val antallBarn: Int? = null,
    val manueltGrunnlag: Int? = null,
    val inntektsId: String? = null,
    val lærling: Boolean? = null

) {
    fun toJson() = toJson(this)
    fun toPacket() = toPacket(this)

    companion object Mapper {

        fun toJson(internBehov: InternBehov): String = jacksonObjectMapper.writeValueAsString(internBehov)

        fun fromJson(json: String): InternBehov? = jacksonObjectMapper.readValue(json, InternBehov::class.java)

        fun toPacket(internBehov: InternBehov): Packet = Packet("{}").apply {
            this.putValue(PacketKeys.BEHOV_ID, internBehov.behovId.id)
            this.putValue(PacketKeys.AKTØR_ID, internBehov.aktørId)
            this.putValue(PacketKeys.VEDTAK_ID, internBehov.behandlingsId.regelKontekst.id) // todo: Når inntekt-klassifiserer ikke lenger eksploderer av manglende vedtakId ønsker vi å gå vekk fra denne og heller peke mot regelkontekstId
            this.putValue(PacketKeys.KONTEKST_ID, internBehov.behandlingsId.regelKontekst.id)
            this.putValue(PacketKeys.KONTEKST_TYPE, internBehov.behandlingsId.regelKontekst.type.name)
            this.putValue(PacketKeys.BEHANDLINGSID, internBehov.behandlingsId.id)
            this.putValue(PacketKeys.BEREGNINGS_DATO, internBehov.beregningsDato)

            internBehov.harAvtjentVerneplikt?.let { this.putValue(PacketKeys.HAR_AVTJENT_VERNE_PLIKT, it) }
            internBehov.oppfyllerKravTilFangstOgFisk?.let { this.putValue(PacketKeys.OPPFYLLER_KRAV_TIL_FANGST_OG_FISK, it) }
            internBehov.bruktInntektsPeriode?.let { this.putValue(PacketKeys.BRUKT_INNTEKTSPERIODE, it.toJson()) }
            internBehov.antallBarn?.let { this.putValue(PacketKeys.ANTALL_BARN, it) }
            internBehov.manueltGrunnlag?.let { this.putValue(PacketKeys.MANUELT_GRUNNLAG, it) }
            internBehov.inntektsId?.let { this.putValue(PacketKeys.INNTEKTS_ID, it) }
            internBehov.lærling?.let { this.putValue(PacketKeys.LÆRLING, it) }
        }

        fun fromBehov(behov: Behov, behandlingsId: BehandlingsId): InternBehov {
            return InternBehov(
                behandlingsId = behandlingsId,
                aktørId = behov.aktørId,
                harAvtjentVerneplikt = behov.harAvtjentVerneplikt,
                oppfyllerKravTilFangstOgFisk = behov.oppfyllerKravTilFangstOgFisk,
                manueltGrunnlag = behov.manueltGrunnlag,
                beregningsDato = behov.beregningsDato,
                bruktInntektsPeriode = behov.bruktInntektsPeriode,
                antallBarn = behov.antallBarn,
                inntektsId = behov.inntektsId,
                lærling = behov.lærling
            )
        }
    }
}

sealed class Status {
    data class Done(val behovId: BehovId) : Status() {
        companion object {
            override fun toString() = "Done"
        }
    }

    object Pending : Status() {
        override fun toString() = "Pending"
    }
}
