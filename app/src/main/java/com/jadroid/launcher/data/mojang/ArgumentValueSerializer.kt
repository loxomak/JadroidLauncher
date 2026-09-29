package com.jadroid.launcher.data.mojang

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer

/**
 * An entry of the modern `arguments.game` / `arguments.jvm` arrays. Mojang mixes plain strings with
 * `{ "rules": [...], "value": "..." | ["...", "..."] }` objects, hence the custom serializer.
 */
@Serializable(with = ArgumentValueSerializer::class)
data class ArgumentValue(
    val values: List<String> = emptyList(),
    val rules: List<Rule> = emptyList()
)

object ArgumentValueSerializer : KSerializer<ArgumentValue> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.jadroid.launcher.data.mojang.ArgumentValue")

    override fun deserialize(decoder: Decoder): ArgumentValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ArgumentValue can only be decoded from JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ArgumentValue(values = listOf(element.content))
            is JsonObject -> {
                val rules = element["rules"]
                    ?.let { jsonDecoder.json.decodeFromJsonElement(ListSerializer(Rule.serializer()), it) }
                    ?: emptyList()
                val values = when (val value = element["value"]) {
                    null -> emptyList()
                    is JsonArray -> value.map { it.jsonPrimitive.content }
                    else -> listOf(value.jsonPrimitive.content)
                }
                ArgumentValue(values = values, rules = rules)
            }
            else -> ArgumentValue()
        }
    }

    override fun serialize(encoder: Encoder, value: ArgumentValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ArgumentValue can only be encoded to JSON")
        val element = JsonObject(
            mapOf(
                "rules" to jsonEncoder.json.encodeToJsonElement(ListSerializer(Rule.serializer()), value.rules),
                "value" to JsonArray(value.values.map { JsonPrimitive(it) })
            )
        )
        jsonEncoder.encodeJsonElement(element)
    }
}
