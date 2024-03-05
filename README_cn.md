# Server Replay

[English](./README.md) | **中文**

译 / tanh_Heng

一个只在服务端生效的replay模组，它允许你在服务器上一次性同时录制多个在线玩家或某一片区块，然后产生一个可以被客户端replay模组使用的录制文件用来渲染。

## 为什么选择服务端？

与客户端的Replay Mod相比，在服务端录制有着许多好处：

* 能够录制固定的区块

    + 你可以指定确切的区块大小（不受服务端视距的影响）

    + 这些记录的区块可以在不影响replay的情况下卸载

        - 区块不会在卸载与加载的过程中闪烁

        - 这些区块也不会被recorder录制器加载（不像PCRC一样会手动加载区块）

        - recorder录制器会跳过区块被卸载的时间

        - *这里指的是录制器不会手动加载那些本应被卸载的区块（只有当区块被加载时才会进行记录），并且区块的加载与卸载不会对记录产生影响。————译者注*

* 能够录制玩家

    + 玩家不需要安装replay模组 *（包括此模组和客户端replay。 ————译者注）*

    + 你可以一次性录制多个机位

    + 录制可以依靠设置项自动进行

* 录制行为可以在任何时间被管理员开启（或其他有权限的任何人）

但是这仍然有一些缺点和已知问题：

* 一些东西将不会被记录，比如boss栏

* 要预览录制的replay，你必须从服务端下载录制文件。

* 录制玩家的内容可能不会和客户端replay的录制内容100%一致

* 模组兼容问题。模组可能会和其他一些修改了网络的模组冲突，如果你遇到任何兼容性问题请提交一个issue

## 用法

此模组需要fabric launcher、fabric-api和fabric-kotlin

有以下两种方法来在服务端进行录制：你可以对模组进行设置来从玩家的视角跟随并记录玩家；或者，你可以录制固定的一片区块。

### 快速开始

这部分的文档将会简短地引导你进行基础的构建，同时包含了一些重要的信息。

#### 玩家

要在服务端记录玩家，你可以运行`/replay start players <player(s)>`，例如：

```
/replay start players senseiwells
/replay start players @a
/replay start players @a[gamemode=survival]
```

玩家录制将会和玩家绑定，并且按服务端视距进行录制。

如果玩家退出了服务器或者服务端停止了，录制将会自动停止并保存。

同时，如果你想要手动停止录制，你可以运行`/replay stop players <player(s)> <是否保存?>`。这个指令还可以停止录制并取消保存，例如：

```
/replay stop players senseiwells
/replay stop players @r
/replay stop players senseiwells false
```

此录制之后将会被保存在`"player_recording_path"`所指定的文件夹的玩家uuid目录下。在默认情况下，它将被保存在`./recordings/players/<uuid>/<date-and-time>.mcpr`。

此文件然后可以被放在客户端的`./replay_recordings`文件夹下，并用客户端replay模组打开。

**重要提示：** 如果你要记录carpet的假人，你大概率需要在设置中打开`"fix_carpet_bot_view_distance"`，否则只有假人周围的2个区块会被记录。

#### 区块

> **重要提示：** 对于模组录制的指定区域的区块，Minecraft客户端并不会渲染最边缘的那些区块。所以要记录一片**可见的**区块，你必须在边缘添加一个区块 *（当选取时）*，例如录制一片从`-5, -5`到`5, 5`的可见区域，你必须从`-6, 6`到`6, 6`进行录制。

要记录服务端的一块区域内的区块，你可以运行`/replay start chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度?> named <名称?>`，例如：

```
/replay start chunks from -5 -5 to 5 5 in minecraft:overworld named MyChunkRecording
/replay start chunks from 54 67 to 109 124
/replay start chunks from 30 30 to 60 60 in minecraft:the_nether
```

同时你可以指定一个区块和它周围的半径来进行录制，`/replay start chunks around <chunkX> <chunkZ> radius <半径> in <维度?> named <名称?>`，例如：

```
/replay start chunks around 0 0 radius 5
/replay start chunks around 67 12 radius 16 in minecraft:overworld named Perimeter Recorder
```

区块录制将会被固定并且无法移动，它们将录制指定的区块。需要特别注意的是，当录制开始的时候，这些指定的区块将会被加载一下（并且在有必要的情况下生成）。但在这之后，录制器将不会手动加载这些区块。

同时，如果你希望手动停止录制，你可以运行`/replay stop chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度?> named <名称?>`。这个指令还可以停止录制并取消保存，例如：

```
/replay stop chunks from 0 0 to 5 5 in minecraft:overworld false
/replay stop chunks from 54 67 to 109 124
```

此录制之后将会被保存在`"player_recording_path"`所指定的文件夹的区块录制器名称下。在默认情况下，它将被保存在`./recordings/chunks/<name>/<date-and-time>.mcpr`。

此文件然后可以被放在客户端的`./replay_recordings`文件夹下，并用客户端replay模组打开。

#### 指令

注意：对于所有的指令，玩家必须要有等级4的op权限，或如果你有一个权限模组（例如[LuckPerms](https://luckperms.net/)），玩家可以在拥有权限节点 `replay.commands.replay` 时使用这些指令。

- `/replay enable` 允许模组按照给定的规则（详见 [Predicates](#predicates-config) 部分）自动记录玩家。

- `/replay disable` 禁止模组自动录制玩家，这将会同时停止当前的所有的玩家录制和区块录制。

- `/replay start players <玩家>` 手动开启对给定的玩家的录制。

- `/replay start chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度?> named <名称?>`
  手动开启对给定的区块范围的录制；如果维度没有被指定，将会使用发起指令的玩家所在的维度；名称决定了录制文件的保存路径。

- `/replay start chunks around <区块X轴位置> <区块Z轴位置> radius <半径> in <维度?> named <名称?>`
  该指令和上一个指令类似；但你可以指定录制给定区块周围的半径内的区域。

- `/replay stop players <player(s)> <是否保存?>` 手动停止对给定玩家的录制，你可以选择性地设置录制是否被保存，默认情况下它将会被保存。

- `/replay stop chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度?> named <名称?>`
  手动停止对于给定区块范围的录制。如果维度没有被指定，将会使用发起指令的玩家所在的维度。你可以选择性地设置录制是否被保存，默认情况下它将会被保存。

- `/replay stop chunks named <名称> <是否保存?>`
  该指令和上一个指令类似；但你可以依靠名称来选取指定的区块范围。

- `/replay stop [chunks|players] all <是否保存?>`
  手动停止对**所有**区块或玩家的录制。你可以选择性的设置录制是否被保存，默认情况下它将会被保存。

- `/replay status`
  获取一个状态信息，包含录制是否被允许，以及当前所有对玩家和区块的录制的列表，它们已被录制的时长，和它们的文件大小。

- `/replay reload` 重载replay模组的配置文件。

### 配置项

在你启动服务器后，一个新的文件将会生成在`./config/ServerReplay/config.json`，默认情况下它是这样的：

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
  "recover_unsaved_replays": true,
  "fixed_daylight_cycle": -1,
  "pause_unloaded_chunks": false,
  "pause_notify_players": true,
  "notify_admins_of_status": true,
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

| Config                           | Description                                                                                                                                                                                                                                                   |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"enabled"`                      | <p> 默认情况下录制功能是被禁用的。你可以通过修改`config.json`然后运行`/replay reload`来开启它，或者运行`/replay [enable\|disable]`指令。</p>                                                                       |
| `"world_name"`                   | <p> 在录制文件中呈现的世界名称。 </p>                                                                                                                                                                                           |
| `"server_name"`                  | <p> 在录制文件中呈现的服务端名称。 </p>                                                                                                                                                                                          |
| `"player_recording_path"`        | <p> 玩家录制的保存路径。 </p>                                                                                                                                                                                               |
| `"chunk_recording_path"`         | <p> 区块录制的保存路径。 </p>                                                                                                                                                                                                |
| `"max_file_size"`                | <p> 回放文件允许录制的最大文件大小，这应当是一个数字+单位，例如 `5.2mb`。 </p> <p> 如果录制达到了这个限制，录制器将会停止。若要不对其进行限制，将此选项设置为 `0` 。 </p>                            |
| `"pause_unloaded_chunks"`        | <p> 如果某一范围内的区块正在被录制，而该区域又被卸载了，当此选项设置为`true`时，录制将会被暂停，直到区块被重新加载时继续录制。 </p> <p> 如果此选项设置为`false`，区块将会仍然被录制，就像他们被加载了一样。*（指区块将继续以他们卸载时的状态呈现在回放中，而不是直接将区块卸载的时间跳过。————译者注）*  </p>   |
| `"pause_notify_players"`         | <p> If `pause_unloaded_chunks` is enabled and this is enabled then when the recording for the chunk area is paused or resumed all online players will be notified. </p>                                                                                       |
| `"notify_admins_of_status"`      | <p> When enabled this will notify admins of when a replay starts, when a replay ends, and when a replay has finished saving, as well as any errors that occur. </p>                                                                                           |
| `"restart_after_max_file_size"`  | <p> If a max file size is set and this limit is reached then the replay recording will automatically restart creating a new replay file. </p>                                                                                                                 |
| `"include_compressed_in_status"` | <p> Includes the compressed file size of the replays when you do `/replay status`, for long replays this may cause the status message to take a while to be displayed, so you can disable it. </p>                                                            |
| `"recover_unsaved_replays"`      | <p> This tries to recover any unsaved replays, for example if your server crashes or stops before a replay is stopped or has finished saving, this does not guarantee that the replay will not be corrupt, but it will try to salvage what is available. </p> |
| `"fixed_daylight_cycle"`         | <p> This fixes the daylight cycle in the replay if you do not want the constant day-night cycle in long timelapses. This should be set to the time of day in ticks, e.g. `6000` (midday). To disable the fixed daylight cycle set the value to `-1`. </p>     |
| `"fix_carpet_bot_view_distance"` | <p> If you are recording carpet bots you want to enable this as it sets the view distance to the server view distance. Otherwise it will only record a distance of 2 chunks around the bot. </p>                                                              |
| `"ignore_sound_packets"`         | <p> If you are recording a large area for a timelapse it's unlikely you'll want to record any sounds, these can eat up significant storage space. </p>                                                                                                        |
| `"ignore_light_packets"`         | <p> Light is calculated on the client as well as on the server so light packets are mostly redundant. </p>                                                                                                                                                    |
| `"ignore_chat_packets"`          | <p> Stops chat packets (from both the server and other players) from being recorded if they are not necessary for your replay. </p>                                                                                                                           |
| `"ignore_scoreboard_packets"`    | <p> Stops scoreboard packets from being recorded (for example, if you have a scoreboard displaying digs then this will not appear, and player's scores will also not be recorded). </p>                                                                       |
| `"optimize_explosion_packets"`   | <p> This reduces the file size greatly by not sending the client explosion packets instead just sending the explosion particles and sounds. </p>                                                                                                              |
| `"optimize_entity_packets"`      | <p> This reduces the file size by letting the client handle the logic for some entities, e.g. projectiles and tnt. This may cause some inconsistencies however it will likely be negligible. </p>                                                             |
| `"player_predicate"`             | <p> The predicate for recording players automatically, more information in the [Predicates](#predicates-config) section. </p>                                                                                                                                 |
| `"chunks"`                       | <p> The list of chunks to automatically record when the server stars, more information in the [Chunks](#chunks-config) section. </p>                                                                                                                          |

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
  "player_predicate": {
    "type": "all"
  }
}
```

If you wanted to only record players with specific names or uuids you can do the following:
```json5
{
  // ...
  "player_predicate": {
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
  "player_predicate": {
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
  "player_predicate": {
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
  "player_predicate": {
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
  "player_predicate": {
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

If you are using carpet mod and have the ability to spawn fake players you may want to exclude them from being recorded.
You can do this with the `is_fake` predicate:
```json5
{
  // ...
  "player_predicate": {
    "type": "not",
    "predicate": {
      "type": "is_fake"
    }
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

Here's a basic example of what you can do:
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
