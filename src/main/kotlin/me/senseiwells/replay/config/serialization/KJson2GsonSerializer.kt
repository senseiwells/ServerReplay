package me.senseiwells.replay.config.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.serialization.json.*
import java.lang.reflect.Type
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonElement as KJsonElement
import kotlinx.serialization.json.JsonNull as KJsonNull
import kotlinx.serialization.json.JsonObject as KJsonObject
import kotlinx.serialization.json.JsonPrimitive as KJsonPrimitive

object KJson2GsonSerializer: JsonSerializer<KJsonElement> {
    override fun serialize(
        element: KJsonElement,
        type: Type,
        context: JsonSerializationContext
    ): JsonElement {
        when (element) {
            is KJsonNull -> return JsonNull.INSTANCE
            is KJsonPrimitive -> {
                if (element.isString) {
                    return JsonPrimitive(element.content)
                }
                val bool = element.booleanOrNull
                if (bool != null) {
                    return JsonPrimitive(bool)
                }
                return JsonPrimitive(element.longOrNull ?: element.double)
            }
            is KJsonArray -> {
                val array = JsonArray()
                for (entry in element) {
                    array.add(context.serialize(entry))
                }
                return array
            }
            is KJsonObject -> {
                val json = JsonObject()
                for (entry in element.entries) {
                    json.add(entry.key, context.serialize(entry.value))
                }
                return json
            }
        }
    }
}