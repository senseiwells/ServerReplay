package me.senseiwells.replay.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.serialization.json.JsonElement
import me.senseiwells.replay.config.serialization.KJson2GsonSerializer
import java.io.Writer

object ReplayMetaUtils {
    val GSON: Gson = GsonBuilder().disableHtmlEscaping().serializeNulls()
        .registerTypeAdapter(JsonElement::class.java, KJson2GsonSerializer)
        .create()

    fun serialize(meta: Map<String, Any>, writer: Writer) {
        writer.use { GSON.toJson(meta, writer) }
    }
}