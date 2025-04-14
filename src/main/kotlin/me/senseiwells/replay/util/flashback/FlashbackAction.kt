package me.senseiwells.replay.util.flashback

import net.minecraft.resources.ResourceLocation

enum class FlashbackAction(path: String) {
    CacheChunk("action/level_chunk_cached"),
    ConfigurationPacket("action/configuration_packet"),
    CreatePlayer("action/create_local_player"),
    GamePacket("action/game_packet"),
    MoveEntities("action/move_entities"),
    NextTick("action/next_tick"),
    VoiceChat("action/simple_voice_chat_sound_optional");

    val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath("flashback", path)

    companion object {
        private val idToAction = FlashbackAction.entries.associateBy { it.id }

        fun from(id: ResourceLocation): FlashbackAction? {
            return this.idToAction[id]
        }
    }
}