package no.nav.dagpenger.regel.api.models

import com.fasterxml.jackson.annotation.JsonProperty

data class RegelKontekst(val id: String, val type: Kontekst)

enum class Kontekst {
    @JsonProperty("soknad")
    SOKNAD,
    @JsonProperty("veiledning")
    VEILEDNING,
    @JsonProperty("vedtak")
    VEDTAK,
    @JsonProperty("revurdering")
    REVURDERING,
    @JsonProperty("forskudd")
    FORSKUDD,
    @JsonProperty("corona")
    CORONA // Todo: fjern når forskudd er ute
}
