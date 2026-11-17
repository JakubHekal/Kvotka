package com.jakubhekal.kvotka.managers.netstats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetstatsParserTest {

    private val modernDump = listOf(
        "Active interfaces:",
        "  iface=rmnet0",
        "",
        "Dev stats:",
        "  ident=[[type=MOBILE subType=0 carrierName=Test subId=1]]",
        "    bucketDuration=3600000",
        "    bucketStart=1700000000000 activeTime=1000 rxBytes=1000 txBytes=100",
        "    bucketStart=1700003600000 activeTime=1000 rxBytes=2000 txBytes=200",
        "  ident=[[type=MOBILE subType=0 carrierName=Test subId=2]]",
        "    bucketDuration=3600000",
        "    bucketStart=1700000000000 activeTime=1000 rxBytes=7000 txBytes=700",
        "  ident=[[type=WIFI]]",
        "    bucketDuration=3600000",
        "    bucketStart=1700000000000 activeTime=1000 rxBytes=90000 txBytes=9000",
        "",
        "Xt stats:",
        "  ident=[[type=MOBILE subId=1]]",
        "    bucketDuration=3600000",
        "    bucketStart=1700000000000 rxBytes=55555 txBytes=5555"
    )

    @Test
    fun parsesMobileBucketsPerSubIdInRange() {
        val buckets = NetstatsParser.parse(modernDump)

        assertEquals(3, buckets.size)
        assertTrue(buckets.all { it.subId == 1 || it.subId == 2 })

        val usage = NetstatsParser.sumInRange(buckets, 1700000000000, 1700007200000)
        assertEquals(3000L to 300L, usage[1])
        assertEquals(7000L to 700L, usage[2])
    }

    @Test
    fun scalesPartialBuckets() {
        val buckets = NetstatsParser.parse(modernDump)

        val firstHalf = NetstatsParser.sumInRange(buckets, 1700001800000, 1700003600000)
        assertEquals(500L to 50L, firstHalf[1])
        assertEquals(3500L to 350L, firstHalf[2])

        val outside = NetstatsParser.sumInRange(buckets, 1600000000000, 1700000000000)
        assertTrue(outside.isEmpty())
    }

    @Test
    fun skipsIdentitiesWithSeveralSubIds() {
        val dump = listOf(
            "Dev stats:",
            "  ident=[[type=MOBILE subId=1] [type=MOBILE subId=2]]",
            "    bucketDuration=3600000",
            "    bucketStart=1700000000000 rxBytes=1000 txBytes=100"
        )

        assertTrue(NetstatsParser.parse(dump).isEmpty())
    }

    @Test
    fun parsesLegacySecondsTimestamps() {
        val dump = listOf(
            "Dev stats:",
            "  ident=[{type=MOBILE,subType=0,subId=3}]",
            "    st=1700000000 rb=500 tb=50"
        )

        val buckets = NetstatsParser.parse(dump)
        assertEquals(1, buckets.size)
        assertEquals(1700000000000L, buckets[0].startMillis)

        val usage = NetstatsParser.sumInRange(buckets, 1700000000000, 1700003600000)
        assertEquals(500L to 50L, usage[3])
    }
}
