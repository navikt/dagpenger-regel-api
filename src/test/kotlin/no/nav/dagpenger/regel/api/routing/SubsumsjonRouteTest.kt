package no.nav.dagpenger.regel.api.routing

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import no.nav.dagpenger.events.Problem
import no.nav.dagpenger.regel.api.db.SubsumsjonNotFoundException
import no.nav.dagpenger.regel.api.db.SubsumsjonStore
import no.nav.dagpenger.regel.api.models.Faktum
import no.nav.dagpenger.regel.api.models.Subsumsjon
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SubsumsjonRouteTest {

    @Test
    fun `401 on unauthorized requests`() {
        withTestApplication(MockApi()) {
            handleRequest(HttpMethod.Get, "subsumsjon/id").response.status() shouldBe HttpStatusCode.Unauthorized
            handleRequest(HttpMethod.Get, "subsumsjon/id") { addHeader("X-API-KEY", "notvalid") }
                .response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Returns subsumsjon if found`() {
        val subsumsjon = Subsumsjon(
            "id",
            "behovId",
            Faktum("aktorid", 1, LocalDate.now()),
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
            Problem(title = "problem")
        )

        val storeMock = mockk<SubsumsjonStore>(relaxed = false).apply {
            every { this@apply.getSubsumsjon("subsumsjonsid") } returns subsumsjon
        }

        withTestApplication(MockApi(
            subsumsjonStore = storeMock
        )) {

            handleAuthenticatedRequest(HttpMethod.Get, "/subsumsjon/subsumsjonsid")
                .apply {
                    response.status() shouldBe HttpStatusCode.OK
                    response.content shouldNotBe null
                    response.content?.let {
                        Subsumsjon.fromJson(it) shouldBe subsumsjon
                    }
                }
        }

        verifyAll {
            storeMock.getSubsumsjon("subsumsjonsid")
        }
    }

    @Test
    fun `Throws exception if not found`() {
        val storeMock = mockk<SubsumsjonStore>(relaxed = false)

        every { storeMock.getSubsumsjon("notfound") } throws SubsumsjonNotFoundException("Not found")

        withTestApplication(MockApi(
            subsumsjonStore = storeMock
        )) {
            shouldThrow<SubsumsjonNotFoundException> {
                handleAuthenticatedRequest(HttpMethod.Get, "/subsumsjon/notfound")
            }
        }

        verifyAll {
            storeMock.getSubsumsjon("notfound")
        }
    }
}