package com.leekleak.iperfintegration

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
sealed interface IntervalResult {
    val protocol: String
    val socket: Int
    val bytesTransferred: Long
    val intervalStartTime: Long
    val intervalEndTime: Long
    val intervalDuration: Double
    val omitted: Boolean
}

@Serializable
data class TcpIntervalResult(
    override val protocol: String,
    override val socket: Int,
    @SerialName("bytes_transferred") override val bytesTransferred: Long,
    @SerialName("interval_start_time") override val intervalStartTime: Long,
    @SerialName("interval_end_time") override val intervalEndTime: Long,
    @SerialName("interval_duration") override val intervalDuration: Double,
    override val omitted: Boolean
) : IntervalResult

@Serializable
data class UdpIntervalResult(
    override val protocol: String,
    override val socket: Int,
    @SerialName("bytes_transferred") override val bytesTransferred: Long,
    @SerialName("interval_start_time") override val intervalStartTime: Long,
    @SerialName("interval_end_time") override val intervalEndTime: Long,
    @SerialName("interval_duration") override val intervalDuration: Double,
    @SerialName("interval_packet_count") val intervalPacketCount: Long,
    @SerialName("interval_outoforder_packets") val intervalOutOfOrderPackets: Long,
    @SerialName("interval_cnt_error") val intervalCntError: Long,
    @SerialName("packet_count") val packetCount: Long,
    val jitter: Double,
    @SerialName("outoforder_packets") val outOfOrderPackets: Long,
    @SerialName("cnt_error") val cntError: Long,
    override val omitted: Boolean
) : IntervalResult

object IntervalResultSerializer : JsonContentPolymorphicSerializer<IntervalResult>(IntervalResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<IntervalResult> {
        return when (val protocol = element.jsonObject["protocol"]?.jsonPrimitive?.content) {
            "udp" -> UdpIntervalResult.serializer()
            "tcp" -> TcpIntervalResult.serializer()
            else -> error("Unknown protocol: $protocol")
        }
    }
}