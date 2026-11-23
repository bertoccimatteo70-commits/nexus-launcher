package com.nexus.core.common

import com.nexus.core.model.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiTest {
    private val forecastJson = """
        {
          "latitude": 45.4642, "longitude": 9.19,
          "current": {
            "time": "2026-11-23T10:00",
            "temperature_2m": 9.4,
            "apparent_temperature": 7.9,
            "weather_code": 3
          },
          "daily": {
            "time": ["2026-11-23", "2026-11-24", "2026-11-25"],
            "weather_code": [3, 61, 0],
            "temperature_2m_max": [12.1, 10.0, 13.4],
            "temperature_2m_min": [4.2, 5.0, 3.1]
          }
        }
    """.trimIndent()

    @Test
    fun `parses current weather and forecast`() {
        val parsed = WeatherApi.parseForecast(forecastJson, "Milano", 1_000L).getOrThrow()
        assertEquals("Milano", parsed.report.city)
        assertEquals(9.4, parsed.report.temperatureC, 0.001)
        assertEquals(WeatherKind.CLOUDY, parsed.report.kind)
        assertEquals(12.1, parsed.report.highC, 0.001)
        assertEquals(3, parsed.forecast.days.size)
        assertEquals(WeatherKind.RAIN, parsed.forecast.days[1].kind)
        assertTrue(!parsed.report.fromCache)
    }

    @Test
    fun `uses current values when daily data is missing`() {
        val minimal = """{"current":{"temperature_2m":18.0,"weather_code":0}}"""
        val parsed = WeatherApi.parseForecast(minimal, "Roma", 10L).getOrThrow()
        assertEquals(18.0, parsed.report.highC, 0.001)
        assertEquals(WeatherKind.CLEAR, parsed.report.kind)
        assertTrue(parsed.forecast.days.isEmpty())
    }

    @Test
    fun `invalid payloads fail with a message`() {
        assertTrue(WeatherApi.parseForecast("{}", "Milano", 0L).isFailure)
        assertTrue(WeatherApi.parseForecast("not-json", "Milano", 0L).isFailure)
    }

    @Test
    fun `wmo codes map to weather kinds`() {
        assertEquals(WeatherKind.CLEAR, WeatherApi.kindFromWmo(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherApi.kindFromWmo(2))
        assertEquals(WeatherKind.FOG, WeatherApi.kindFromWmo(45))
        assertEquals(WeatherKind.DRIZZLE, WeatherApi.kindFromWmo(53))
        assertEquals(WeatherKind.RAIN, WeatherApi.kindFromWmo(82))
        assertEquals(WeatherKind.SNOW, WeatherApi.kindFromWmo(75))
        assertEquals(WeatherKind.THUNDER, WeatherApi.kindFromWmo(96))
        assertEquals(WeatherKind.UNKNOWN, WeatherApi.kindFromWmo(1234))
    }

    @Test
    fun `geocoding parses places and handles empty results`() {
        val json = """
            {"results":[{"name":"Milano","admin1":"Lombardia","country":"Italia","latitude":45.46,"longitude":9.19}]}
        """.trimIndent()
        val places = WeatherApi.parseGeocoding(json)
        assertEquals(1, places.size)
        assertEquals("Milano, Lombardia, Italia", places.single().displayName)
        assertTrue(WeatherApi.parseGeocoding("""{"results":[]}""").isEmpty())
        assertTrue(WeatherApi.parseGeocoding("bad").isEmpty())
    }

    @Test
    fun `forecast url contains coordinates`() {
        val url = WeatherApi.forecastUrl(45.4642, 9.19)
        assertTrue(url.contains("latitude=45.4642"))
        assertTrue(url.contains("timezone=auto"))
    }
}
