package no.nav.dagpenger.regel.api.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Claim
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.auth.jwt.JWTAuthenticationProvider
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import java.net.URL
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

internal fun JWTAuthenticationProvider.Configuration.azureAdJWT(
    providerUrl: String,
    realm: String,
    clientId: String
) {

    val meta = meta(providerUrl)
    this.verifier(jwkProvider(meta.jwksUri), meta.issuer)
    this.realm = realm
    validate { credentials: JWTCredential ->
        try {
            requireNotNull(credentials.payload.audience) {
                "Auth: Missing audience in token"
            }
            require(credentials.payload.audience.contains(clientId)) {
                "Auth: Valid audience not found in claims"
            }

            val roles: Claim? = credentials.payload.claims["roles"]

            requireNotNull(roles) {
                "Auth: Roles not found in claims"
            }

            require(
                roles.asList(String::class.java).contains("access_as_application")
            ) {
                "Auth: Valid role not found in claims"
            }

            JWTPrincipal(credentials.payload)
        } catch (e: Throwable) {
            LOGGER.error("Unauthorized", e)
            null
        }
    }
}

private val httpClient = HttpClient(CIO) {
    engine {
        System.getenv("HTTP_PROXY")?.let {
            this.proxy = ProxyBuilder.http(it)
        }
    }
}

internal data class AzureAdOpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String
)

private fun meta(url: String): AzureAdOpenIdConfiguration {
    return runBlocking {
        httpClient.get<String>(url).let {
            jacksonObjectMapper.readValue(it, AzureAdOpenIdConfiguration::class.java)
        }
    }
}

private fun jwkProvider(url: String): JwkProvider {
    return JwkProviderBuilder(URL(url))
        .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
        .rateLimited(
            10,
            1,
            TimeUnit.MINUTES
        ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .build()
}
