package com.example.routetracker.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransitCatalogBuilderTest {
    private val pragueZone: ZoneId = ZoneId.of("Europe/Prague")

    @Test
    fun `groups stop platforms under a single station`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = listOf(
                GtfsStopRecord(
                    stopId = "U529Z1P",
                    stopName = "Palmovka",
                    platformCode = "1",
                    parentStation = null,
                    aswNode = 100,
                ),
                GtfsStopRecord(
                    stopId = "U529Z2P",
                    stopName = "Palmovka",
                    platformCode = "2",
                    parentStation = null,
                    aswNode = 100,
                ),
            ),
            routes = emptyList(),
        )

        val station = catalog.stations.single()
        assertEquals("Palmovka", station.stationName)
        assertEquals(2, station.platforms.size)
        assertEquals(listOf("U529Z1P", "U529Z2P"), station.stopIds)
        assertEquals("Platform 1", station.platforms[0].label)
        assertEquals(listOf("U529Z2P"), station.resolveSelection("2").stopIds)
    }

    @Test
    fun `station search ignores case accents and whitespace`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = listOf(
                GtfsStopRecord(
                    stopId = "U463Z1P",
                    stopName = "Nádraží Vršovice",
                    platformCode = "1",
                    parentStation = null,
                    aswNode = 200,
                ),
            ),
            routes = emptyList(),
        )

        val result = catalog.searchStations("  nadrazi   vrsovice  ").single()
        assertEquals("Nádraží Vršovice", result.stationName)
    }

    @Test
    fun `line search matches both short and long names`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = emptyList(),
            routes = listOf(
                GtfsRouteRecord(
                    shortName = "7",
                    longName = "Radlická - Nádraží Vršovice",
                    routeType = 0,
                ),
                GtfsRouteRecord(
                    shortName = "22",
                    longName = "Bílá Hora - Nádraží Hostivař",
                    routeType = 0,
                ),
            ),
        )

        assertEquals("7", catalog.searchLines("7").single().shortName)
        val byName = catalog.searchLines("hostivar")
        assertNotNull(byName.firstOrNull { it.shortName == "22" })
    }

    @Test
    fun `search uses hierarchy root and node fallback when station records are nameless`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = listOf(
                GtfsStopRecord(
                    stopId = "U529S1",
                    stopName = null,
                    platformCode = null,
                    parentStation = null,
                    aswNode = 529,
                ),
                GtfsStopRecord(
                    stopId = "U529Z101P",
                    stopName = null,
                    platformCode = "1",
                    parentStation = "U529S1",
                    aswNode = 529,
                ),
                GtfsStopRecord(
                    stopId = "U529K1B1",
                    stopName = "Kolej 1 - Start",
                    platformCode = null,
                    parentStation = "U529Z101P",
                    aswNode = 529,
                ),
                GtfsStopRecord(
                    stopId = "U529Z102P",
                    stopName = null,
                    platformCode = "2",
                    parentStation = "U529S1",
                    aswNode = 529,
                ),
                GtfsStopRecord(
                    stopId = "U529T1",
                    stopName = "Palmovka",
                    platformCode = null,
                    parentStation = null,
                    aswNode = 529,
                ),
            ),
            routes = emptyList(),
        )

        val palmovka = catalog.searchStations("pa").firstOrNull()
        assertNotNull(palmovka)
        assertEquals("Palmovka", palmovka!!.stationName)
        assertEquals("parent:U529S1", palmovka.stationKey)
        assertEquals(listOf("1", "2"), palmovka.platforms.map { it.platformKey })
    }

    @Test
    fun `same-name station search prefers broader result and shows platform summary`() {
        val catalog = TransitCatalog(
            fetchedAt = pragueTime(),
            stations = listOf(
                StationOption.create(
                    stationKey = "metro",
                    stationName = "Želivského",
                    stopIds = listOf("U921Z101P", "U921Z102P"),
                    platforms = listOf(
                        PlatformOption("1", "Platform 1", listOf("U921Z101P")),
                        PlatformOption("2", "Platform 2", listOf("U921Z102P")),
                    ),
                ),
                StationOption.create(
                    stationKey = "surface",
                    stationName = "Želivského",
                    stopIds = (1..10).map { "U921Z${it}P" },
                    platforms = listOf(
                        PlatformOption("A", "Platform A", listOf("U921Z1P")),
                        PlatformOption("B", "Platform B", listOf("U921Z2P")),
                        PlatformOption("C", "Platform C", listOf("U921Z3P")),
                        PlatformOption("D", "Platform D", listOf("U921Z4P")),
                    ),
                ),
            ),
            lines = emptyList(),
        )

        val results = catalog.searchStations("zelivskeho")
        assertEquals("surface", results.first().stationKey)
        assertEquals("Platforms A, B, C +1", results.first().searchSubtitle)
        assertEquals("Platforms 1, 2", results[1].searchSubtitle)
    }

    @Test
    fun `builder merges nearby same-name station groups into one result`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = listOf(
                GtfsStopRecord(
                    stopId = "U921S1",
                    stopName = "Želivského",
                    platformCode = null,
                    parentStation = null,
                    aswNode = 921,
                    latitude = 50.078286,
                    longitude = 14.475499,
                ),
                GtfsStopRecord(
                    stopId = "U921Z101P",
                    stopName = null,
                    platformCode = "1",
                    parentStation = "U921S1",
                    aswNode = 921,
                    latitude = 50.078210,
                    longitude = 14.475420,
                ),
                GtfsStopRecord(
                    stopId = "U921Z102P",
                    stopName = null,
                    platformCode = "2",
                    parentStation = "U921S1",
                    aswNode = 921,
                    latitude = 50.078220,
                    longitude = 14.475430,
                ),
                GtfsStopRecord(
                    stopId = "U921Z1P",
                    stopName = "Želivského",
                    platformCode = "A",
                    parentStation = null,
                    aswNode = null,
                    latitude = 50.078350,
                    longitude = 14.475600,
                ),
                GtfsStopRecord(
                    stopId = "U921Z2P",
                    stopName = "Želivského",
                    platformCode = "B",
                    parentStation = null,
                    aswNode = null,
                    latitude = 50.078360,
                    longitude = 14.475620,
                ),
            ),
            routes = emptyList(),
        )

        val zelivskeho = catalog.searchStations("zelivskeho").single()
        assertEquals(listOf("1", "2", "A", "B"), zelivskeho.platforms.map { it.platformKey })
        assertEquals(5, zelivskeho.stopIds.size)
    }

    @Test
    fun `builder keeps far-away same-name stations separate`() {
        val catalog = TransitCatalogBuilder.build(
            fetchedAt = pragueTime(),
            stops = listOf(
                GtfsStopRecord(
                    stopId = "STOP_A",
                    stopName = "Nádraží",
                    platformCode = "1",
                    parentStation = null,
                    aswNode = null,
                    latitude = 50.1000,
                    longitude = 14.4000,
                ),
                GtfsStopRecord(
                    stopId = "STOP_B",
                    stopName = "Nádraží",
                    platformCode = "2",
                    parentStation = null,
                    aswNode = null,
                    latitude = 50.2000,
                    longitude = 14.6000,
                ),
            ),
            routes = emptyList(),
        )

        assertEquals(2, catalog.searchStations("nadrazi").size)
    }

    private fun pragueTime(): ZonedDateTime {
        return ZonedDateTime.of(2026, 3, 15, 10, 0, 0, 0, pragueZone)
    }
}
