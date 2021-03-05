package no.nav.dagpenger.regel.api.models

import io.kotest.assertions.throwables.shouldThrow
import no.nav.dagpenger.regel.api.serder.jacksonObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UlidTest {
    @Test
    fun `Should reject non-ulids`() {
        shouldThrow<IllegalUlidException> { Ulid("not ulid") }
    }

    @Test
    fun `Should always have uppercase id`() {
        val uppercase = "01DSFVQY33P2A5K7GHNC96W3JJ"
        val lowercase = uppercase.toLowerCase()
        val upperUlid = Ulid(uppercase)
        val lowerUlid = Ulid(lowercase)
        assertEquals(uppercase, lowerUlid.id)
        assertEquals(uppercase, upperUlid.id)
    }

    @Test
    fun `Should not care about upper or lowercase when compared`() {
        val uppercase = "01DSFVQY33P2A5K7GHNC96W3JJ"
        val lowercase = uppercase.toLowerCase()
        val upperUlid = Ulid(uppercase)
        val lowerUlid = Ulid(lowercase)
        assertTrue { upperUlid.equals(lowerUlid) }
        assertTrue { lowerUlid.equals(upperUlid) }
    }

    @Test
    fun `Should care about being different when compared`() {
        val uppercase = "01DSFVQY33P2A5K7GHNC96W3JJ"
        val lowercase = "01DSJ0E6KA3VP2XW0S5XNQNE6A".toLowerCase()
        val upperUlid = Ulid(uppercase)
        val lowerUlid = Ulid(lowercase)
        assertFalse { upperUlid.equals(lowerUlid) }
        assertFalse { lowerUlid.equals(upperUlid) }
    }

    @Test
    fun `Should only show id when mapped to json`() {
        val jsonBehov = jacksonObjectMapper.writeValueAsString(BehovId("01DSFTA586H33ESMTYMY6QD4ZD"))
        val jsonSubsumsjon = jacksonObjectMapper.writeValueAsString(SubsumsjonId("01DSJ0SHHV49MJA6EJ8B7PSSXJ"))
        val behovShouldBe =
            """"01DSFTA586H33ESMTYMY6QD4ZD""""
        val subsumsjonShouldBe =
            """"01DSJ0SHHV49MJA6EJ8B7PSSXJ""""

        assertEquals(behovShouldBe, jsonBehov)
        assertEquals(subsumsjonShouldBe, jsonSubsumsjon)
    }

    @Test
    fun `Should differentiate between BehovId and SubsumsjonId`() {
        val ulid = "01DSJ1RJ6WQBETB7VHF3CDNB5H"
        val behov = BehovId(ulid)
        val subsumsjon = SubsumsjonId(ulid)
        assertFalse(behov.equals(subsumsjon))
    }
}
