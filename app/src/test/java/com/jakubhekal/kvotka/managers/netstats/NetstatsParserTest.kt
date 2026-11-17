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
        ""
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

    @Test
    fun usesXtStatsWhenDevStatsIsEmpty() {
        val dump = listOf(
            "Dev stats:",
            "  Pending bytes: ",
            "  History since boot:",
            "Xt stats:",
            "  Pending bytes: 0",
            "  History since boot:",
            "  ident=[{type=0, ratType=-2, subscriberId=230030..., ROAMING, metered=false, defaultNetwork=false, oemManaged=OEM_NONE, subId=1, transports={0}}] uid=-1 set=ALL tag=0x0",
            "    NetworkStatsHistory: bucketDuration=3600",
            "      st=1794906000 rb=41055 rp=118 tb=35411 tp=142 op=0"
        )

        val data = NetstatsParser.parseData(dump)
        assertEquals(setOf(1), data.subIds)
        assertEquals(1, data.buckets.size)
        assertEquals(3_600_000L, data.buckets[0].durationMillis)

        val usage = NetstatsParser.sumInRange(data.buckets, 1794906000000L, 1794909600000L)
        assertEquals(41055L to 35411L, usage[1])
    }

    @Test
    fun parsesUidBucketsPerSubId() {
        val dump = listOf(
            "Dev stats:",
            "Xt stats:",
            "UID stats:",
            "  ident=[{type=0, ratType=-2, subId=1, transports={0}}] uid=1000 set=DEFAULT tag=0x0",
            "    NetworkStatsHistory: bucketDuration=7200",
            "      st=1794906000 rb=100 rp=1 tb=10 tp=1 op=0",
            "  ident=[{type=0, ratType=-2, subId=1, transports={0}}] uid=1000 set=FOREGROUND tag=0x0",
            "    NetworkStatsHistory: bucketDuration=7200",
            "      st=1794906000 rb=200 rp=2 tb=20 tp=2 op=0",
            "  ident=[{type=0, ratType=-2, subId=2, transports={0}}] uid=1001 set=DEFAULT tag=0x0",
            "    NetworkStatsHistory: bucketDuration=7200",
            "      st=1794906000 rb=300 rp=3 tb=30 tp=3 op=0",
            "  ident=[{type=-1, ratType=COMBINED, subId=-1, transports={1}}] uid=1000 set=DEFAULT tag=0x0",
            "    NetworkStatsHistory: bucketDuration=7200",
            "      st=1794906000 rb=999 rp=9 tb=99 tp=9 op=0"
        )

        val data = NetstatsParser.parseData(dump)
        assertEquals(setOf(1, 2), data.subIds)
        assertTrue(data.buckets.isEmpty())

        val usage = NetstatsParser.sumByUidInRange(data.uidBuckets, 1794906000000L, 1794913200000L)
        assertEquals(300L to 30L, usage[1]?.get(1000))
        assertEquals(300L to 30L, usage[2]?.get(1001))
        assertEquals(null, usage[1]?.get(1001))
    }
}
