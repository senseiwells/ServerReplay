# <img src="./src/main/resources/assets/server-replay/icon.png" align="center" width="64px"/> Server Replay

[English](./README.md) | **中文**

*译 / tanh_Heng / WorldHim / AndyOctopus*

一个完全在服务端实现的 [Replay Mod](https://www.replaymod.com/) 和 [Flashback](https://modrinth.com/mod/flashback) 模组，允许你在服务器上一次性同时录制多个在线玩家或区块。这将产生可以被 Replay Mod 或 Flashback 用于渲染的录制文件。

[![Modrinth download](https://img.shields.io/modrinth/dt/server-replay?label=Download%20on%20Modrinth&style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbDpzcGFjZT0icHJlc2VydmUiIGZpbGwtcnVsZT0iZXZlbm9kZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLW1pdGVybGltaXQ9IjEuNSIgY2xpcC1ydWxlPSJldmVub2RkIiB2aWV3Qm94PSIwIDAgMTAwIDEwMCI+PHBhdGggZmlsbD0ibm9uZSIgZD0iTTAgMGgxMDB2MTAwSDB6Ii8+PGNsaXBQYXRoIGlkPSJhIj48cGF0aCBkPSJNMTAwIDBIMHYxMDBoMTAwVjBaTTQ2LjAwMiA0OS4yOTVsLjA3NiAxLjc1NyA4LjgzIDMyLjk2MyA3Ljg0My0yLjEwMi04LjU5Ni0zMi4wOTQgNS44MDQtMzIuOTMyLTcuOTk3LTEuNDEtNS45NiAzMy44MThaIi8+PC9jbGlwUGF0aD48ZyBjbGlwLXBhdGg9InVybCgjYSkiPjxwYXRoIGZpbGw9IiMwMGQ4NDUiIGQ9Ik01MCAxN2MxOC4yMDcgMCAzMi45ODggMTQuNzg3IDMyLjk4OCAzM1M2OC4yMDcgODMgNTAgODMgMTcuMDEyIDY4LjIxMyAxNy4wMTIgNTAgMzEuNzkzIDE3IDUwIDE3Wm0wIDljMTMuMjQgMCAyMy45ODggMTAuNzU1IDIzLjk4OCAyNFM2My4yNCA3NCA1MCA3NCAyNi4wMTIgNjMuMjQ1IDI2LjAxMiA1MCAzNi43NiAyNiA1MCAyNloiLz48L2c+PGNsaXBQYXRoIGlkPSJiIj48cGF0aCBkPSJNMCAwdjQ2aDUwbDEuMzY4LjI0MUw5OSA2My41NzhsLTIuNzM2IDcuNTE3TDQ5LjI5NSA1NEgwdjQ2aDEwMFYwSDBaIi8+PC9jbGlwUGF0aD48ZyBjbGlwLXBhdGg9InVybCgjYikiPjxwYXRoIGZpbGw9IiMwMGQ4NDUiIGQ9Ik01MCAwYzI3LjU5NiAwIDUwIDIyLjQwNCA1MCA1MHMtMjIuNDA0IDUwLTUwIDUwUzAgNzcuNTk2IDAgNTAgMjIuNDA0IDAgNTAgMFptMCA5YzIyLjYyOSAwIDQxIDE4LjM3MSA0MSA0MVM3Mi42MjkgOTEgNTAgOTEgOSA3Mi42MjkgOSA1MCAyNy4zNzEgOSA1MCA5WiIvPjwvZz48Y2xpcFBhdGggaWQ9ImMiPjxwYXRoIGQ9Ik01MCAwYzI3LjU5NiAwIDUwIDIyLjQwNCA1MCA1MHMtMjIuNDA0IDUwLTUwIDUwUzAgNzcuNTk2IDAgNTAgMjIuNDA0IDAgNTAgMFptMCAzOS41NDljNS43NjggMCAxMC40NTEgNC42ODMgMTAuNDUxIDEwLjQ1MSAwIDUuNzY4LTQuNjgzIDEwLjQ1MS0xMC40NTEgMTAuNDUxLTUuNzY4IDAtMTAuNDUxLTQuNjgzLTEwLjQ1MS0xMC40NTEgMC01Ljc2OCA0LjY4My0xMC40NTEgMTAuNDUxLTEwLjQ1MVoiLz48L2NsaXBQYXRoPjxnIGNsaXAtcGF0aD0idXJsKCNjKSI+PHBhdGggZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMDBkODQ1IiBzdHJva2Utd2lkdGg9IjkiIGQ9Ik01MCA1MCA1LjE3MSA3NS44ODIiLz48L2c+PGNsaXBQYXRoIGlkPSJkIj48cGF0aCBkPSJNNTAgMGMyNy41OTYgMCA1MCAyMi40MDQgNTAgNTBzLTIyLjQwNCA1MC01MCA1MFMwIDc3LjU5NiAwIDUwIDIyLjQwNCAwIDUwIDBabTAgMjUuMzZjMTMuNTk5IDAgMjQuNjQgMTEuMDQxIDI0LjY0IDI0LjY0UzYzLjU5OSA3NC42NCA1MCA3NC42NCAyNS4zNiA2My41OTkgMjUuMzYgNTAgMzYuNDAxIDI1LjM2IDUwIDI1LjM2WiIvPjwvY2xpcFBhdGg+PGcgY2xpcC1wYXRoPSJ1cmwoI2QpIj48cGF0aCBmaWxsPSJub25lIiBzdHJva2U9IiMwMGQ4NDUiIHN0cm9rZS13aWR0aD0iOSIgZD0ibTUwIDUwIDUwLTEzLjM5NyIvPjwvZz48cGF0aCBmaWxsPSIjMDBkODQ1IiBkPSJNMzcuMjQzIDUyLjc0NiAzNSA0NWw4LTkgMTEtMyA0IDQtNiA2LTQgMS0zIDQgMS4xMiA0LjI0IDMuMTEyIDMuMDkgNC45NjQtLjU5OCAyLjg2Ni0yLjk2NCA4LjE5Ni0yLjE5NiAxLjQ2NCA1LjQ2NC04LjA5OCA4LjAyNkw0Ni44MyA2NS40OWwtNS41ODctNS44MTUtNC02LjkyOVoiLz48L3N2Zz4=)](https://modrinth.com/mod/server-replay)

## 为什么选择服务端？

与客户端的 [Replay Mod](https://www.replaymod.com/) 或 [Flashback](https://modrinth.com/mod/flashback) 相比，在服务端录制有着许多好处：

* 能够录制固定的区块：
  * 你可以指定确切的区块大小（不受服务端视距的影响）。
  * 这些记录的区块可以在不影响回放的情况下卸载：
    * 不会出现区块闪烁（由卸载和加载区块引起）。
    * 这些区块也不会被录制器加载（例如，不像 [PCRC](https://github.com/Fallen-Breath/PCRC)）。
  * 可以根据区块是否被加载或区块内是否有玩家来暂停和恢复录制器。
* 能够录制单个玩家：
  * 玩家不需要安装 Replay Mod 或 Flashback。
  * 你可以一次性录制所有视角。
  * 可以使用配置自动进行录制。
* 录制可以由管理员（或任何有权限的人）随时启动。

但是，也存在一些缺点和已知问题：
* 某些功能不会被区块录制记录，例如自定义 Boss 栏。
* 玩家录制可能与客户端 [Replay Mod](https://www.replaymod.com/) 或 [Flashback](https://modrinth.com/mod/flashback) 不完全一致。
* ServerReplay 并不是为重度模组化的服务器设计的。实现自己数据包的更复杂的模组可能不兼容。
  * 如果你遇到任何兼容性问题，请提交 Issue。

## 用法

此模组需要 [Fabric Loader](https://github.com/FabricMC/fabric-loader)、[Fabric API](https://github.com/FabricMC/fabric) 和 [Fabric-Language-Kotlin](https://github.com/FabricMC/fabric-language-kotlin)。

有以下两种方法来在服务端进行录制：你可以设置从玩家的视角跟随并记录玩家；或者，你可以录制固定的一些区块。

### 快速开始

> [!NOTE]
> 此文档适用于模组的最新版本，对于旧版本请查看其他分支。

这部分文档将简要引导你完成基本设置，同时包含一些重要信息。

#### 玩家

要在服务端记录玩家，你可以执行 `/replay start players <玩家>`，例如：

`/replay start players senseiwells`  
`/replay start players @a`  
`/replay start players @a[gamemode=survival]`

玩家录制将会和玩家绑定，并且按服务端视距进行录制。

如果玩家退出了服务器或者服务端停止了，录制将会自动停止并保存。

同时，如果你想要手动停止录制，你可以执行 `/replay stop players <玩家> <是否保存>`。这个指令还可以停止录制并取消保存，例如：

`/replay stop players senseiwells`  
`/replay stop players @r`  
`/replay stop players senseiwells false`

此录制之后将会被保存在 `player_recording_path` 所指定的文件夹中玩家 uuid 目录下。默认情况下，它将被保存在 `./recordings/players/<uuid>/<date-and-time>.mcpr`

此文件可以被放在客户端的 `./replay_recordings` 文件夹中，并使用 Replay Mod 打开。

> [!WARNING]
> 尝试录制 Carpet 假人或其他非真实玩家可能会导致意外行为。
> 如果你想录制大片区块区域，请使用区块录制器！

#### 区块

> [!NOTE]
> 对于模组录制的指定区域的区块，Minecraft 客户端**不会**渲染最边缘的那些区块。所以如果要记录一片**可见**的区块，你必须在边缘多选取一个区块。例如录制一片从 `-5,-5` 到 `5,5` 的可见区块，你必须从 `-6,6` 到 `6,6` 进行录制。

要记录服务端的一些区块，你可以执行 `/replay start chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度> named <名称>`，例如：

`/replay start chunks from -5 -5 to 5 5 in minecraft:overworld named MyChunkRecording`  
`/replay start chunks from 54 67 to 109 124`  
`/replay start chunks from 30 30 to 60 60 in minecraft:the_nether`

同时你可以指定一个中心区块和半径来进行录制，`/replay start chunks around <区块X轴> <区块Z轴> radius <半径> in <维度> named <名称>`，例如：

`/replay start chunks around 0 0 radius 5`  
`/replay start chunks around 67 12 radius 16 in minecraft:overworld named Perimeter Recorder`

区块录制将被固定并且无法移动，它们将录制指定的区块。需要特别注意的是，当录制开始的时候，这些指定的区块将会被加载一下（在有必要的情况下将会被生成）。在此之后，录制器将不会手动加载这些区块。

你可以通过配置 `chunk_recorder_load_radius` 来设定区块录制器会自动加载的最大范围。在这个范围外的录制区块需要手动加载来录制。

如果服务端停止了，录制将会自动停止并保存。

同时，如果你希望手动停止录制，你可以使用其名称停止指定的录制器，使用 `/replay stop chunks named <名称> <是否保存>`，例如：

```
/replay stop chunks named "Perimeter Recorder" false
/replay stop chunks named MyChunkRecording
```

此录制之后将会被保存在 `chunk_recording_path` 所指定的文件夹中区块录制器名称目录下。默认情况下，它将被保存在 `./recordings/chunks/<name>/<date-and-time>.mcpr`。

此文件可以被放在客户端的 `./replay_recordings` 文件夹中，并使用 Replay Mod 打开。

#### 编码格式

ServerReplay 支持 Flashback（适用于较新版本）以及 Replay Mod 录制。

默认情况下，所有录制将使用 Replay Mod 的格式进行录制；但是，你可以通过在游戏中运行以下命令来更改此设置：
```
/replay encoding default set flashback
/replay encoding default set replay_mod
```

> [!NOTE]
> 你可以同时使用 Flashback 和 Replay Mod 录制玩家的视角。
> 你可以通过将默认编码设置为 Flashback，开始录制，然后将编码设置为 replay_mod 并开始另一个录制来实现这一点。
> 
> 区块录制也可以这样做，但每个录制器必须有一个唯一的名称。

#### 查看

录制完成后，你可以完全在服务端查看录制。
查看录制的玩家将从实际服务器中完全移除，在查看录制时将被视为离线。

本质上，这只是"运行"另一个向客户端发送数据包的服务器。这与主服务器异步运行，因此对性能的影响很小或没有影响。

当录制完成时，你可以点击聊天中的绿色文本来查看刚刚完成的录制。
查看区块录制的命令是：`/replay view chunks <name> <date-time>`，对于玩家：`/replay view players <uuid> <date-time>`，例如：
```
/replay view player "d4fca8c4-e083-4300-9a73-bf438847861c" "2024-05-11--19-19-55"
/replay view chunks "Chunks (183, 166) to (203, 186)" "2024-05-11--19-19-55"
```

然后你将被传送到新的"服务器"，录制将开始播放。
在查看录制时，你只能访问有限的命令集，包括：
- `/replay view pause` 暂停当前录制的播放。
- `/replay view unpause` 取消暂停当前录制的播放。
- `/replay view speed <multiplier>` 设置当前录制的播放速度。
- `/replay view restart` 重新开始当前录制的播放。
- `/replay view close` 关闭当前录制并将你带回服务器。
- `/replay view progress <hide|show>` 隐藏或显示进度 Boss 栏。

如果你在观看录制时断开连接，当你登录时将被带回服务器。

#### 下载

如果配置中启用了 `"allow_downloading_replays"` 并且服务器 IP 和下载端口设置正确，你可以从服务器下载任何录制。

你可以使用 `/replay download` 命令来获取下载指定录制的 URL，例如：
```
/replay download players d4fca8c4-e083-4300-9a73-bf438847861c "2024-05-11--19-19-55"
/replay download chunks "Chunks (183, 166) to (203, 186)" "2024-05-11--19-19-55"
```

这将向你发送一条聊天消息；你可以点击提供的链接来下载文件。

### 命令

所有命令的注意事项：玩家必须拥有 OP（等级 4），或者如果你有权限模组（例如 [LuckPerms](https://luckperms.net/)），玩家可以拥有权限 `server-replay.commands.replay` 来访问这些命令。

- `/replay start players <player(s)>` 手动开始录制给定玩家（们）的录制。
- `/replay start chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> named <name?>` 
  手动开始录制给定区块区域的录制，如果未指定维度，将使用命令用户的维度，名称决定录制文件将保存在录制路径中的位置。
- `/replay start chunks around <chunkX> <chunkZ> radius <radius> in <dimension?> named <name?>`
  这与上面的命令相同；但是，你可以指定给定区块周围的半径。
- `/replay stop players <player(s)> <save?>` 手动停止录制给定玩家（们）的录制，
  你可以选择是否保存录制；默认情况下，这是 true。
- `/replay stop chunks from <chunkFromX> <chunkFromZ> to <chunkToX> <chunkToZ> in <dimension?> <save?>` 
  手动停止录制给定区块区域的录制，如果未指定维度，将使用命令用户的维度，你可以选择是否保存录制；默认情况下，这是 true。
- `/replay stop chunks named <name> <save?>`
  这让你可以做与上面命令相同的事情；但是，你可以通过名称指定区块区域。
- `/replay stop [chunks|players] all <save?>` 手动停止**所有**区块或玩家录制，你可以选择是否保存录制；默认情况下，这是 true。
- `/replay status` 发送一个状态消息，显示录制是否启用以及当前正在录制的所有玩家和区块的列表，它们已录制的时长以及它们的文件大小。
- `/replay reload` 重新加载 Replay 模组的配置文件。
- `/replay encoding default set <encoding-type>` 设置要录制的录制类型，`flashback` 或 `replay_mod`

### 配置

在你启动服务器后，将在路径 `./config/server-replay/config.json` 中生成一个新文件，默认情况下，它应该如下所示：

```json
{
  "default_encoding": "replay_mod",
  "world_name": "World",
  "server_name": "Server",
  "chunk_recording_path": "./recordings/chunks",
  "player_recording_path": "./recordings/players",
  "player_recording_name": "{uuid}",
  "max_file_size": "0 B",
  "restart_after_max_file_size": false,
  "max_duration": "0s",
  "restart_after_max_duration": false,
  "recover_unsaved_replays": true,
  "delete_replays_after_duration": "0s",
  "log_deleted_replays": true,
  "chunk_recorder_load_radius": -1,
  "chunk_recording_strategy": "always",
  "pause_notify_players": true,
  "notify_admins_of_status": true,
  "include_resource_packs": true,
  "ignore_custom_payloads": false,
  "ignore_sound_packets": false,
  "ignore_light_packets": true,
  "ignore_chat_packets": false,
  "ignore_action_bar_packets": false,
  "ignore_scoreboard_packets": false,
  "optimize_explosion_packets": true,
  "optimize_entity_packets": false,
  "record_hotbar": false,
  "record_voice_chat": false,
  "replay_server_ip": null,
  "allow_downloading_replays": false,
  "automatically_record": false,
  "player_predicate": {
    "type": "none"
  },
  "chunks": []
}
```

| Config                            | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `"default_encoding"`              | <p> 录制的编码格式，`"replay_mod"` 或 `"flashback"`。默认为 `"replay_mod"`（如果未指定）。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `"world_name"`                    | <p> 将出现在录制文件中的世界名称。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `"server_name"`                   | <p> 将出现在录制文件中的服务器名称。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `"player_recording_path"`         | <p> 你想要保存玩家录制的路径。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `"chunk_recording_path"`          | <p> 你想要保存区块录制的路径。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `"player_recording_name"`         | <p> 这决定了每个特定玩家录制目录的名称。默认设置为 `"{uuid}"`，使用玩家的 uuid。你也可以使用 `"{username}"` 插入玩家的名称。例如，你可以有：`"Recordings for: {username} ({uuid})"`。 </p>                                                                                                                                                                                                                                                                                          |
| `"max_file_size"`                 | <p> 这指定了录制的 `max_file_size`，如果达到限制，录制将自动停止。此文件大小指的是原始录制大小，***不是***最终压缩的录制大小，通常最终压缩的录制大小会小得多。 </p>                                                                                                                                                                                                                                                                                                                                     |
| `"restart_after_max_file_size"`   | <p> 如果设置了 `max_file_size` 并且达到此限制，则录制将自动重新开始创建新的录制文件。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `"max_duration"`                  | <p> 设置录制的最大时长，一旦录制达到指定的时长，它将停止，这是任何数字后跟单位（你也可以有多个单位），例如 `"4h 35m 2.1s"`。将此设置为 `"0s"` 以不设置最大时长限制。注意：如果录制器被暂停，其时长不会增加。 </p>                                                                                                                                                                                                                         |
| `"restart_after_max_duration"`    | <p> 如果设置了 `max_duration` 并且达到此限制，则录制将自动重新开始创建新的录制文件。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `"recover_unsaved_replays"`       | <p> 这尝试恢复任何未保存的录制，例如，如果你的服务器崩溃或在录制停止或完成保存之前停止，这不能保证录制不会被损坏，但会尝试挽救可用的内容。 </p>                                                                                                                                                                                                                                                                                                                                                                           |
| `"delete_replays_after_duration"` | <p> 删除旧录制文件的时长。这是自文件最后*修改*以来的时长。时长格式与 `max_duration` 选项相同，例如 `"5d 10h"`。将此设置为 `"0s"` 以禁用。 </p>                                                                                                                                                                                                                                                                                                                                       |
| `"log_deleted_replays"`           | <p> 是否在由于录制过期而删除录制时输出服务器日志。必须启用 `delete_replays_after_duration` 才能生效。 </p>                                                                                                                                                                                                                                                                                                                                                                                                       |
| `"fixed_daylight_cycle"`          | <p> 如果你不想要长时间恒定的昼夜周期，这将修复录制中的日光周期。此选项应当设置为以 `tick` 为单位的一天的时间，例如 `6000`（正午）。要禁用这一修复，将选项值设为 `-1`。</p>                                                                                                                                                                                                                                                                                                                                                                               |
| `"chunk_recorder_load_radius"`    | <p> 这设置默认的区块录制器加载半径，当你想要录制非常大的区域并且不想一次性加载所有录制的区块时，这很有用。 </p> <p> 例如，如果你正在录制 13x13 区块区域，你可以将半径设置为 3，这样中心 7x7 将最初被加载，其余区块将在它们被"自然"加载时被录制。 </p> <p> 将此设置为 `-1` 以加载所有区块。 </p>                                                                                               |
| `"chunk_recording_strategy"`      | <p> 这定义了区块录制器将如何录制，有 4 个选项： </p> <ul> <li> `"always"` - 即使卸载时也始终录制区块（就像它们被加载一样） </li> <li>  `"chunk_loaded"` - 仅当区域中的任何区块被加载时录制，如果所有区块都被卸载，则暂停录制 </li> <li> `"chunk_contains_player"` - 仅当有玩家在区域内时录制区块（仅限 Flashback） </li> <li> `"chunk_contains_non_spectator_player"` - 与上面相同，但仅适用于非观察者玩家（仅限 Flashback） </li> </ul> |
| `"pause_notify_players"`          | <p> 如果 `chunk_recording_strategy` 设置为允许录制器自动暂停的选项，并且启用了此选项，则每当区块区域的录制暂停或恢复时，所有在线玩家都会收到通知。 </p>                                                                                                                                                                                                                                                                                                                                                             |
| `"notify_admins_of_status"`       | <p> 启用后，这将通知管理员录制开始、录制结束和录制完成保存的时间，以及发生的任何错误。 </p>                                                                                                                                                                                                                                                                                                                                                                                                     |
| `"include_resource_packs"`        | <p> 如果启用，所有服务端资源包将被复制到录制文件中以确保正确播放。禁用此功能将减小文件大小，但会尝试在查看录制时从原始源下载资源包，不能保证这会正常工作。（仅限 Replay Mod） </p>                                                                                                                                                                                                                                         |
| `"ignore_custom_payloads"`        | <p> 如果启用，所有自定义数据包（模组数据包）将被忽略，如果另一个模组的数据包导致录制问题，可以启用此功能 </p>                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `"ignore_sound_packets"`          | <p> 如果你正在为大片区域录制延时摄影，你可能不想录制任何声音，这些会占用大量存储空间。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `"ignore_light_packets"`          | <p> 光照在客户端和服务端上都会计算，所以光照数据包大多是多余的。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `"ignore_chat_packets"`           | <p> 如果它们对你的录制不是必需的，停止录制聊天数据包（来自服务器和其他玩家）。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `"ignore_action_bar_packets"`     | <p> 如果它们对你的录制不是必需的，停止录制操作栏数据包。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `"ignore_scoreboard_packets"`     | <p> 停止录制计分板数据包（例如，如果你有一个显示挖掘的计分板，那么这不会出现，玩家的分数也不会被录制）。 </p>                                                                                                                                                                                                                                                                                                                                                                                 |
| `"optimize_explosion_packets"`    | <p> 这通过不向客户端发送爆炸数据包，而是只发送爆炸粒子和声音来大大减小文件大小。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `"optimize_entity_packets"`       | <p> 这通过让客户端处理某些实体的逻辑来减小文件大小，例如弹射物和 TNT。这可能会导致一些不一致，但可能可以忽略不计。 </p>                                                                                                                                                                                                                                                                                                                                                                       |
| `"record_hotbar"`                 | <p> 这为玩家录制启用热键栏录制，以获得更好的第一人称体验。（仅限 Flashback） </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `"record_voice_chat"`             | <p> 如果你安装了 [simple-voice-chat](https://github.com/henkelmax/simple-voice-chat) 模组，这将启用语音聊天录制支持，在 Flashback 中查看时将开箱即用，但在 Replay Mod 中观看录制时，你必须安装 [replay-voice-chat](https://github.com/henkelmax/replay-voice-chat)。 </p>                                                                                                                                                                                                          |
| `"replay_server_ip"`              | <p> 如果你的服务器使用自定义服务端资源包，并且你想能够在服务端录制查看器中查看这些资源包，则需要此选项。如果你想允许用户下载录制，也需要此选项。 </p> <p> 这应该包含你服务器的公共 IP 地址。 </p>                                                                                                                                                                                                                                                                                                                                  |
| `"allow_downloading_replays"`     | <p> 确定用户是否能够下载录制的录制。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `"automatically_record"`          | <p> 是否根据 `"player_predicate"` 和/或 `"chunks"` 配置自动录制玩家或区块。如果启用，任何满足定义的条件的玩家将被自动录制，并且定义的任何区块将在服务器启动时被录制。 </p>                                                                                                                                                                                                                                                                                                                                                         |
| `"player_predicate"`              | <p> 自动录制玩家的条件，更多信息请参见 [条件配置](#条件配置) 部分。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `"chunks"`                        | <p> 服务器启动时自动录制的区块列表，更多信息请参见 [区块配置](#区块配置) 部分。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                   |

### 区块配置

你可以定义在服务器启动时或启用 ServerReplay 时自动录制的区块区域。

每一个区块的定义必须包含：`name`, `dimension`, `from_x`, `to_x`, `from_z`, and `to_z`。例如：
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

### 条件配置

你可以定义一个条件，它将决定服务器上哪些玩家将被自动录制。
你可以通过指定某个玩家是否有特定的 uuid，名字，在某个特定的队伍里，或是否是一个管理员，来设置此规则。

在定义条件后，你必须在游戏中运行 `/replay reload`，然后玩家必须重新登录如果他们想被录制（并且满足条件标准）。

最基本的选项是记录所有玩家，在这种情况下，您可以使用：
```json5
{
  // ...
  "player_predicate": {
    "type": "all"
  }
}
```

如果你想要只记录带有特定名字或 uuid 的玩家，你可以使用：
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

如果你只想要记录管理员：
```json5
{
  // ...
  "player_predicate": {
    "type": "has_op",
    "level": 4
  }
}
```

如果你只想要记录在特定队伍中的玩家，这一选项可以支持玩家在游戏中被加入或移除队伍，因此你可以只玩家加入队伍，然后让他们重新登录（*来自动记录该玩家 ————译者注*）。
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

你还可以否定条件，使用 'not' 并使用 'or' 和 'and' 组合它们。
例如，如果你想要录制所有非管理员且玩家名不是 'senseiwells' 且不在红队中的玩家：
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

如果你正在使用 Carpet 模组并且能够生成假玩家，你可能想要排除它们被录制。
你可以使用 `is_fake` 条件来实现：
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

### 致谢

感谢 [ExperimentalIdea](https://www.youtube.com/@ExperimentalIdea) 帮助测试 Flashback 支持！
