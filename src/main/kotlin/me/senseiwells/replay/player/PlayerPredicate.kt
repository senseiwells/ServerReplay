package me.senseiwells.replay.player

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

abstract class PlayerPredicate {
    abstract fun shouldRecord(player: ServerPlayer): Boolean

    abstract fun serialise(): JsonElement

    companion object {
        fun none(): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return false
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "none")
                    return json
                }
            }
        }

        fun all(): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return true
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "all")
                    return json
                }
            }
        }

        fun hasName(names: List<String>): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return names.contains(player.scoreboardName)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "has_name")
                    val array = JsonArray()
                    for (name in names) {
                        array.add(name)
                    }
                    json.add("names", array)
                    return json
                }
            }
        }

        fun hasUUID(uuids: List<String>): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return uuids.contains(player.stringUUID) || uuids.contains(player.stringUUID.replace("-", ""))
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "has_uuid")
                    val array = JsonArray()
                    for (uuid in uuids) {
                        array.add(uuid)
                    }
                    json.add("uuids", array)
                    return json
                }
            }
        }

        fun hasOP(level: Int): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return player.hasPermissions(level)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "has_op")
                    json.addProperty("level", level)
                    return json
                }
            }
        }

        fun inTeam(teams: List<String>): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    val name = player.team?.name ?: return false
                    return teams.contains(name)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "in_team")
                    val array = JsonArray()
                    for (team in teams) {
                        array.add(team)
                    }
                    json.add("teams", array)
                    return json
                }
            }
        }

        fun not(predicate: PlayerPredicate): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return !predicate.shouldRecord(player)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "not")
                    json.add("predicate", predicate.serialise())
                    return json
                }
            }
        }

        fun or(first: PlayerPredicate, second: PlayerPredicate): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return first.shouldRecord(player) || second.shouldRecord(player)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "or")
                    json.add("first", first.serialise())
                    json.add("second", second.serialise())
                    return json
                }
            }
        }

        fun and(first: PlayerPredicate, second: PlayerPredicate): PlayerPredicate {
            return object: PlayerPredicate() {
                override fun shouldRecord(player: ServerPlayer): Boolean {
                    return first.shouldRecord(player) && second.shouldRecord(player)
                }

                override fun serialise(): JsonElement {
                    val json = JsonObject()
                    json.addProperty("type", "and")
                    json.add("first", first.serialise())
                    json.add("second", second.serialise())
                    return json
                }
            }
        }
    }
}