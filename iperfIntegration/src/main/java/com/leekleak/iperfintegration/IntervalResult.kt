package com.leekleak.iperfintegration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class StreamEvent(
    val event: String,
    val data: JsonObject
)

@Serializable
data class StartData(
    @SerialName("test_start") val testStart: TestStart
)

@Serializable
data class TestStart(
    val protocol: String
)

@Serializable
data class IntervalData(
    val streams: List<IntervalStream>
)

@Serializable
data class IntervalStream(
    val socket: Int,
    val start: Double,
    val end: Double,
    val seconds: Double,
    val bytes: Long,
    @SerialName("bits_per_second") val bitsPerSecond: Double,
    val omitted: Boolean,
    val sender: Boolean? = null,

    // UDP
    @SerialName("jitter_ms") val jitterMs: Double? = null,
    @SerialName("lost_packets") val lostPackets: Long? = null,
    @SerialName("lost_percent") val lostPercent: Double? = null,
    val packets: Long? = null,

    // TCP
    val retransmits: Long? = null,
    val rtt: Long? = null
)

sealed interface IntervalResult {
    val protocol: String
    val socket: Int
    val bytesTransferred: Long
    val intervalStartOffset: Double
    val intervalEndOffset: Double
    val intervalDuration: Double
    val bitsPerSecond: Double
    val omitted: Boolean
}

data class TcpIntervalResult(
    override val protocol: String = "tcp",
    override val socket: Int,
    override val bytesTransferred: Long,
    override val intervalStartOffset: Double,
    override val intervalEndOffset: Double,
    override val intervalDuration: Double,
    override val bitsPerSecond: Double,
    override val omitted: Boolean,
    val retransmits: Long,
    val rtt: Long?
) : IntervalResult

data class UdpIntervalResult(
    override val protocol: String = "udp",
    override val socket: Int,
    override val bytesTransferred: Long,
    override val intervalStartOffset: Double,
    override val intervalEndOffset: Double,
    override val intervalDuration: Double,
    override val bitsPerSecond: Double,
    override val omitted: Boolean,
    val packetCount: Long,
    val jitterMs: Double,
    val lostPackets: Long,
    val lostPercent: Double
) : IntervalResult

class IntervalStreamParser {
    private val json = Json { ignoreUnknownKeys = true }
    private var protocol: String = "tcp"

    fun parseLine(line: String): List<IntervalResult> {
        val envelope = json.decodeFromString(StreamEvent.serializer(), line)

        when (envelope.event) {
            "start" -> {
                val startData = json.decodeFromJsonElement(StartData.serializer(), envelope.data)
                protocol = startData.testStart.protocol.lowercase()
                return emptyList()
            }
            "interval" -> {
                val intervalData = json.decodeFromJsonElement(IntervalData.serializer(), envelope.data)
                return intervalData.streams.map { s -> toResult(s) }
            }
            else -> return emptyList()
        }
    }

    private fun toResult(s: IntervalStream): IntervalResult =
        if (protocol == "udp") {
            UdpIntervalResult(
                socket = s.socket,
                bytesTransferred = s.bytes,
                intervalStartOffset = s.start,
                intervalEndOffset = s.end,
                intervalDuration = s.seconds,
                bitsPerSecond = s.bitsPerSecond,
                omitted = s.omitted,
                packetCount = s.packets ?: 0,
                jitterMs = s.jitterMs ?: 0.0,
                lostPackets = s.lostPackets ?: 0,
                lostPercent = s.lostPercent ?: 0.0
            )
        } else {
            TcpIntervalResult(
                socket = s.socket,
                bytesTransferred = s.bytes,
                intervalStartOffset = s.start,
                intervalEndOffset = s.end,
                intervalDuration = s.seconds,
                bitsPerSecond = s.bitsPerSecond,
                omitted = s.omitted,
                retransmits = s.retransmits ?: 0,
                rtt = s.rtt
            )
        }
}