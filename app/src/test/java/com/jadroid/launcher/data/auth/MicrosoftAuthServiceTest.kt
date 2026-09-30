package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two things that broke sign-in:
 *
 *  1. endpoint selection – the legacy Minecraft id must never be sent to the Azure AD endpoint
 *     (that answers `400 AADSTS700016`), and Azure app ids must not go to login.live.com;
 *  2. `authorization_pending` arrives as an HTTP **400**, so it has to be read from the response body
 *     instead of being reported as a hard failure.
 */
class MicrosoftAuthServiceTest {

    private val service = MicrosoftAuthService(HttpClient("Jadroid-test"))

    private val azureAppId = "c6b5a1e2-1f4b-4f0e-9a1c-8d3a5e6f7b21"

    @Test
    fun `azure app ids use the azure device-code endpoints`() {
        assertTrue(MicrosoftAuthService.isAzureApplicationId(azureAppId))
        assertEquals(MicrosoftAuthEndpoint.AZURE, service.endpointFor(azureAppId))
    }

    @Test
    fun `the legacy minecraft id uses the live sdk endpoints`() {
        assertFalse(MicrosoftAuthService.isAzureApplicationId("00000000402b5328"))
        assertEquals(MicrosoftAuthEndpoint.LEGACY_LIVE, service.endpointFor("00000000402b5328"))
    }

    @Test
    fun `blank client ids fall back to the legacy flow instead of crashing`() {
        assertEquals(MicrosoftAuthEndpoint.LEGACY_LIVE, service.endpointFor("   "))
    }

    @Test
    fun `authorization_pending maps to PENDING although it arrives as http 400`() {
        val body = """{"error":"authorization_pending","error_description":"The provided request has not yet been authorized by the user."}"""
        assertEquals(PollStatus.PENDING, service.parseTokenPayload(body)?.status)
    }

    @Test
    fun `slow_down maps to SLOW_DOWN`() {
        assertEquals(PollStatus.SLOW_DOWN, service.parseTokenPayload("""{"error":"slow_down"}""")?.status)
    }

    @Test
    fun `declined maps to DECLINED`() {
        val body = """{"error":"authorization_declined","error_description":"declined"}"""
        assertEquals(PollStatus.DECLINED, service.parseTokenPayload(body)?.status)
    }

    @Test
    fun `successful payload carries the msa token and refresh token`() {
        val body = """{"access_token":"msa-token","refresh_token":"refresh","expires_in":3600}"""
        val result = service.parseTokenPayload(body)
        assertEquals(PollStatus.SUCCESS, result?.status)
        assertEquals("msa-token", result?.accessToken)
        assertEquals("refresh", result?.refreshToken)
        assertEquals(3600L, result?.expiresInSeconds)
    }

    @Test
    fun `aadsts700016 is explained with an actionable hint`() {
        val message = service.describeAuthError(
            "unauthorized_client",
            "AADSTS700016: Application with identifier '00000000402b5328' was not found in the " +
                "directory 'Microsoft Accounts'."
        )
        assertTrue(message.contains("AADSTS700016"))
        assertTrue(message.contains("Azure"))
    }

    @Test
    fun `non json bodies are ignored instead of crashing`() {
        assertNull(service.parseTokenPayload("<html><body>Sign in to your account</body></html>"))
        assertEquals("Sign in to your account", service.snippet("<html>\n  <body>Sign in to your account</body></html>"))
    }
}
