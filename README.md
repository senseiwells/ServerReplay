# <img src="./src/main/resources/assets/serverreplay/icon.png" align="center" width="64px"/> Server Replay

A completely server-side implementation of the replay mod, this mod allows you
to record multiple players that are online, or chunk areas, on a server at a time. This will
produce replay files which can then be used with the replay mod for rendering.

### Why Server-Side?

Compared to the client [Replay Mod](https://www.replaymod.com/) recording
server-side has many benefits:
- The ability to record static chunks.
  - You can specify the exact chunk size (not bound by server view distance).
  - The recorded chunks may be unloaded without affecting the replay.
    - No chunk flickering (from unloading and loading the chunks).
    - The chunks will also not be loaded by the recorder (like, for example, [PCRC](https://github.com/Fallen-Breath/PCRC)).
    - The recorder can skip periods of time that the area is unloaded.
- The ability to record individual players.
  - Players aren't required to install replay mod.
  - You can record all POVs at once.
  - Recordings can be automated using the configuration.
- Recordings can be started at anytime by operators (or anyone with permissions).

However, there are also some downsides and known issues:
- Some features are not recorded by chunk recordings, e.g. custom boss bars.
- To view the replay, you must download the file from the server.
- Player recordings may not be 100% consistent with the client [Replay Mod](https://www.replaymod.com/).
- Mod compatability, this mod may conflict with other mods that mess with networking, if you encounter any compatability issues please submit a issue.

## Usage

This mod requires the fabric launcher, fabric-api, and fabric-kotlin.

There are two ways of recording on the server, you can either configure it
to follow and record players from their view. 
Alternatively, you can record a static area of chunks.

### Quick Start

This section of the documentation will briefly guide you through a basic setup. 
As well as containing some important information.

#### Players

To record a player on your server you can run `/replay start players <player(s)>`, for example:
```
/replay start players senseiwells
/replay start players @a
/replay start players @a[gamemode=survival]
```

Player recorders are tied to the player and will record at the 
servers view distance.

If the player leaves or the server stops the replay will automatically stop and save.

Alternatively if you wish to stop the recording manually you can run `/replay stop players <player(s)> <save?>`, 
using this command you can also stop a recording without saving it, for example:
```
/replay stop players senseiwells
/replay stop players @r
/replay stop players senseiwells false
```

The replay will then be saved to your `"player_recording_path"` location
specified in a folder with the player's uuid. 
By default, this will be in `./recordings/players/<uuid>/<date-and-time>.mcpr`.

This file can then be put in `./replay_recordings` on your client and be opened with replay mod.

An important note: if you are going to record carpet bots you most likely want to
enable `"fix_carpet_bot_view_distance"` in the config otherwise only an area of 2 chunks
around the carpet bot will be recorded.

#### Chunks

> **IMPORTANT NOTE:** While the mod will record the chunks you specify, the Minecraft client will **not** render the outermost chunks. So to record an area of **visible** chunks, you must add one chunk to your border, e.g. recording a visible area from `-5, -5` to `5, 5` you must record between `-6, -6` and `6, 6`.

To record an area of chunks on your server you can run `/replay start chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> named <name?>`, for example:
```
/replay start chunks from -5 -5 to 5 5 in minecraft:overworld named MyChunkRecording
/replay start chunks from 54 67 to 109 124
/replay start chunks from 30 30 to 60 60 in minecraft:the_nether 
```

Alternatively you can specify a chunk and a radius around it to be recorded `/replay start chunks around <chunkX> <chunkZ> radius <radius> in <dimension?> named <name?>`, for example:
```
/replay start chunks around 0 0 radius 5
/replay start chunks around 67 12 radius 16 in minecraft:overworld named Perimeter Recorder
```

Chunk recorders are static and cannot move, they record the specified chunks.
An important thing to note is that when the replay starts, the specified chunks
will be loaded (and generated if necessary). 
However, after this the chunk recorder does not load the chunks.

If the server stops, the replay will automatically stop and save.

Alternatively if you wish to stop the recording manually you can run `/replay stop chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> <save?>`,
using this command you can also stop a recording without saving it, for example:
```
/replay stop chunks from 0 0 to 5 5 in minecraft:overworld false
/replay stop chunks from 54 67 to 109 124
```

You can also stop the chunks by using their name using `/replay stop chunks named <name> <save?>`, for example:
```
/replay stop chunks named "Perimeter Recorder" false
/replay stop chunks named MyChunkRecording
```

The replay will then be saved to your `"chunk_recording_path"` location
specified in a folder with the chunk recorders name.
By default, this will be in `./recordings/chunks/<name>/<date-and-time>.mcpr`.

This file can then be put in `./replay_recordings` on your client and be opened with replay mod.

### Commands

A note for all commands; players must either have op (level 4), alternatively if you
have a permission mod (for example, [LuckPerms](https://luckperms.net/)) players can
have the permission `replay.commands.replay` to access these commands.

- `/replay enable` Enables the replay mod to automatically recording players that should
  be recorded based on the given predicate (more details in the [Predicates](#predicates-config) section).
- `/replay disable` Disables the replay mod from automatically recording players, this will
  also stop any current recording players and chunks.
- `/replay start players <player(s)>` Manually starts recording the replay for some given player(s).
- `/replay start chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> named <name?>` 
  Manually starts recording the replay for the given chunk area, if no dimension is specified the command user's
  dimension will be used instead, the name determines where the replay file will be saved in the recording path.
- `/replay start chunks around <chunkX> <chunkZ> radius <radius> in <dimension?> named <name?>`
  This achieves the same as the command above; however, you can specify a radius around a given chunk instead.
- `/replay stop players <player(s)> <save?>` Manually stops recording the replay for some given player(s),
  you may optionally pass in whether the replay should be saved; by default, this is true.
- `/replay stop chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> <save?>` 
  Manually stops recording the replay for the given chunk area, if no dimension is specified the command user's
  dimension will be used instead, you may optionally pass in whether the replay should be saved; by default, this is true.
- `/replay stop chunks named <name> <save?>`
  This lets you do the same as the command above; however, you can specify the chunk area by its name.
- `/replay stop [chunks|players] all <save?>` Manually stops **all** chunks or player replays you may optionally pass in whether the
  replay should be saved; by default, this is true.
- `/replay status` Sends a status message of whether replay is enabled and a list of all the
  players and chunks that are currently being recorded, how long they've been recorded for, and their file sizes.
- `/replay reload` Reloads the config file for the replay mod.

### Configuring

After you boot the server a new file will be generated in the path 
``./config/ServerReplay/config.json``, by default it should look like:

```json
{
  "enabled": false,
  "world_name": "World",
  "server_name": "Server",
  "chunk_recording_path": "./recordings/chunks",
  "player_recording_path": "./recordings/players",
  "max_file_size": "0GB",
  "restart_after_max_file_size": false,
  "include_compressed_in_status": true,
  "fixed_daylight_cycle": -1,
  "pause_unloaded_chunks": false,
  "pause_notify_players": true,
  "fix_carpet_bot_view_distance": false,
  "ignore_sound_packets": false,
  "ignore_light_packets": true,
  "ignore_chat_packets": false,
  "ignore_scoreboard_packets": false,
  "optimize_explosion_packets": true,
  "optimize_entity_packets": false,
  "player_predicate": {
    "type": "none"
  },
  "chunks": []
}
```

| Config                           | Description                                                                                                                                                                                                                                                 |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"enabled"`                      | <p> By default replay functionality is disabled. You can enable it by by editing the `config.json` and running `/replay reload` or running the `/replay [enable\|disable]` command.</p>                                                                     |
| `"world_name"`                   | <p> The name of the world that will appear on the replay file. </p>                                                                                                                                                                                         |
| `"server_name"`                  | <p> The name of the server that will appear on the replay file. </p>                                                                                                                                                                                        |
| `"player_recording_path"`        | <p> The path where you want player recordings to be saved. </p>                                                                                                                                                                                             |
| `"chunk_recording_path"`         | <p> The path where you want chunk recordings to be saved. </p>                                                                                                                                                                                              |
| `"max_file_size"`                | <p> The maximum replay file size you want to allow to record, this is any number followed by a unit, e.g. `5.2mb`. </p> <p> If this limit is reached then the replay recorder will stop. Set this to `0` to not have a limit. </p>                          |
| `"pause_unloaded_chunks"`        | <p> If an area of chunks is being recorded and the area is unloaded and this is set to `true` then the replay will pause the recording until the chunks are loaded again. </p> <p> If set to false the chunks will be recorded as if they were loaded. </p> |
| `"pause_notify_players"`         | <p> If `pause_unloaded_chunks` is enabled and this is enabled then when the recording for the chunk area is paused or resumed all online players will be notified. </p>                                                                                     |
| `"restart_after_max_file_size"`  | <p> If a max file size is set and this limit is reached then the replay recording will automatically restart creating a new replay file. </p>                                                                                                               |
| `"include_compressed_in_status"` | <p> Includes the compressed file size of the replays when you do `/replay status`, for long replays this may cause the status message to take a while to be displayed, so you can disable it. </p>                                                          |
| `"fixed_daylight_cycle"`         | <p> This fixes the daylight cycle in the replay if you do not want the constant day-night cycle in long timelapses. This should be set to the time of day in ticks, e.g. `6000` (midday). To disable the fixed daylight cycle set the value to `-1`. </p>   |
| `"fix_carpet_bot_view_distance"` | <p> If you are recording carpet bots you want to enable this as it sets the view distance to the server view distance. Otherwise it will only record a distance of 2 chunks around the bot. </p>                                                            |
| `"ignore_sound_packets"`         | <p> If you are recording a large area for a timelapse it's unlikely you'll want to record any sounds, these can eat up significant storage space. </p>                                                                                                      |
| `"ignore_light_packets"`         | <p> Light is calculated on the client as well as on the server so light packets are mostly redundant. </p>                                                                                                                                                  |
| `"ignore_chat_packets"`          | <p> Stops chat packets (from both the server and other players) from being recorded if they are not necessary for your replay. </p>                                                                                                                         |
| `"ignore_scoreboard_packets"`    | <p> Stops scoreboard packets from being recorded (for example, if you have a scoreboard displaying digs then this will not appear, and player's scores will also not be recorded). </p>                                                                     |
| `"optimize_explosion_packets"`   | <p> This reduces the file size greatly by not sending the client explosion packets instead just sending the explosion particles and sounds. </p>                                                                                                            |
| `"optimize_entity_packets"`      | <p> This reduces the file size by letting the client handle the logic for some entities, e.g. projectiles and tnt. This may cause some inconsistencies however it will likely be negligible. </p>                                                           |
| `"player_predicate"`             | <p> The predicate for recording players automatically, more information in the [Predicates](#predicates-config) section. </p>                                                                                                                               |
| `"chunks"`                       | <p> The list of chunks to automatically record when the server stars, more information in the [Chunks](#chunks-config) section. </p>                                                                                                                        |

### Chunks Config

You can define chunk areas to be recorded automatically when the server starts or when
you enable ServerReplay.

Each chunk definition must include: `"name"`, `"dimension"`, `"from_x"`, `"to_x"`, `"from_z"`, and `"to_z"`. For example:
```json5
{
  // ...
  "chunks": [
    {
      "name": "My Chunks",
      "dimension": "minecraft:overworld",
      "from_x": -5,
      "from_z": -5,
      "to_x": 5,
      "to_z": 5
    },
    {
      "name": "My Nether Chunks",
      "dimension": "minecraft:the_nether",
      "from_x": 100,
      "from_z": 50,
      "to_x": 90,
      "to_z": 60
    }
    // ...
  ]
}
```

### Predicates Config

You can define a predicate, which determines which players on your server
will be recorded automatically. 
You can do this by specifying whether players have a specific uuid, 
name, are on a specific team, or whether they are an operator.

After defining a predicate you must run `/replay reload` in game then players must 
re-log if they want to be recorded (and meet the predicate criteria). 

Most basic option is just to record all players in which case you can use:
```json5
{
  // ...
  "predicate": {
    "type": "all"
  }
}
```

If you wanted to only record players with specific names or uuids you can do the following:
```json5
{
  // ...
  "predicate": {
    "type": "has_name",
    "names": [
      "senseiwells",
      "foobar"
    ]
  }
}
```

```json5
{
  // ...
  "predicate": {
    "type": "has_uuid",
    "uuids": [
      "41048400-886d-497d-9d97-9fe7c9b63afa",
      "71266dbd-db0a-484a-b859-3f135590d7a9",
      "47d072ca-d7a2-467c-9b60-de501907e91d",
      "0e324e7f-e78e-4777-b501-7ae08a65b1eb",
      "7d9e24c2-9d0f-479f-81c7-27389624ebb2"
    ]
  }
}
```

If you only wanted to record operators:
```json5
{
  // ...
  "predicate": {
    "type": "has_op",
    "level": 4
  }
}
```

If you only want to record players on specific teams, this is useful for allowing players to be
added and removed in-game, as you can just add players to a team and then have them re-log:
```json5
{
  // ...
  "predicate": {
    "type": "in_team",
    "teams": [
      "Red",
      "Blue",
      "Spectators"
    ]
  }
}
```

You are also able to negate predicates, using 'not' and combine them using 'or' and 'and'.
For example, if you wanted to record all non-operators that also don't have the name 'senseiwells' or is on the red team:
```json5
{
  // ...
  "predicate": {
    "type": "and",
    "predicates": [
      {
        "type": "not",
        "predicate": {
          "type": "has_op",
          "level": 4
        }
      },
      {
        "type": "not",
        "predicate": {
          "type": "or",
          "predicates": [
            {
              "type": "has_name",
              "names": [
                "senseiwells"
              ]
            },
            {
              "type": "in_team",
              "teams": [
                "Red"
              ]
            }
          ]
        }
      } 
    ]
  }
}
```

## Developers

If you want more control over, when players are recorded, you can implement this into your own mod.

To implement the API into your project, you can add the
following to your `build.gradle.kts`

```kts
repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // For the most recent version use the latest commit hash
    val version = "e444c355ad"
    modImplementation("com.github.Senseiwells:ServerReplay:$version")
}
```

Here's a basic example with what you can do:
```kt
class ExampleMod: ModInitializer {
    override fun onInitialize() {
        ServerPlayConnectionEvents.JOIN.register { connection, _, _ ->
            val player = connection.player
            if (!PlayerRecorders.has(player)) {
                if (player.level().dimension() == Level.END) {
                    val recorder = PlayerRecorders.create(player)
                    recorder.tryStart(log = true)
                }
            } else {
                val existing = PlayerRecorders.get(player)!!
                existing.getCompressedRecordingSize().thenAccept { size ->
                    println("Replay is $size bytes")
                }
                existing.stop(save = false)
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val recorder = ChunkRecorders.create(
                server.overworld(),
                ChunkPos.ZERO,
                ChunkPos(5, 5),
                "Named"
            )
            recorder.tryStart(log = false)
        }
    }
}
```