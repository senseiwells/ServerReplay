# <img src="./src/main/resources/assets/server-replay/icon.png" align="center" width="64px"/> Server Replay

[English](./README.md) | **中文**

*译 / tanh_Heng / WorldHim*

一个只在服务端生效的 Replay 模组，允许你在服务器上一次性同时录制多个在线玩家或区块，并产生一个可以被客户端 Replay Mod 使用的录制文件用来渲染。

[![Modrinth download](https://img.shields.io/modrinth/dt/server-replay?label=Download%20on%20Modrinth&style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbDpzcGFjZT0icHJlc2VydmUiIGZpbGwtcnVsZT0iZXZlbm9kZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLW1pdGVybGltaXQ9IjEuNSIgY2xpcC1ydWxlPSJldmVub2RkIiB2aWV3Qm94PSIwIDAgMTAwIDEwMCI+PHBhdGggZmlsbD0ibm9uZSIgZD0iTTAgMGgxMDB2MTAwSDB6Ii8+PGNsaXBQYXRoIGlkPSJhIj48cGF0aCBkPSJNMTAwIDBIMHYxMDBoMTAwVjBaTTQ2LjAwMiA0OS4yOTVsLjA3NiAxLjc1NyA4LjgzIDMyLjk2MyA3Ljg0My0yLjEwMi04LjU5Ni0zMi4wOTQgNS44MDQtMzIuOTMyLTcuOTk3LTEuNDEtNS45NiAzMy44MThaIi8+PC9jbGlwUGF0aD48ZyBjbGlwLXBhdGg9InVybCgjYSkiPjxwYXRoIGZpbGw9IiMwMGQ4NDUiIGQ9Ik01MCAxN2MxOC4yMDcgMCAzMi45ODggMTQuNzg3IDMyLjk4OCAzM1M2OC4yMDcgODMgNTAgODMgMTcuMDEyIDY4LjIxMyAxNy4wMTIgNTAgMzEuNzkzIDE3IDUwIDE3Wm0wIDljMTMuMjQgMCAyMy45ODggMTAuNzU1IDIzLjk4OCAyNFM2My4yNCA3NCA1MCA3NCAyNi4wMTIgNjMuMjQ1IDI2LjAxMiA1MCAzNi43NiAyNiA1MCAyNloiLz48L2c+PGNsaXBQYXRoIGlkPSJiIj48cGF0aCBkPSJNMCAwdjQ2aDUwbDEuMzY4LjI0MUw5OSA2My41NzhsLTIuNzM2IDcuNTE3TDQ5LjI5NSA1NEgwdjQ2aDEwMFYwSDBaIi8+PC9jbGlwUGF0aD48ZyBjbGlwLXBhdGg9InVybCgjYikiPjxwYXRoIGZpbGw9IiMwMGQ4NDUiIGQ9Ik01MCAwYzI3LjU5NiAwIDUwIDIyLjQwNCA1MCA1MHMtMjIuNDA0IDUwLTUwIDUwUzAgNzcuNTk2IDAgNTAgMjIuNDA0IDAgNTAgMFptMCA5YzIyLjYyOSAwIDQxIDE4LjM3MSA0MSA0MVM3Mi42MjkgOTEgNTAgOTEgOSA3Mi42MjkgOSA1MCAyNy4zNzEgOSA1MCA5WiIvPjwvZz48Y2xpcFBhdGggaWQ9ImMiPjxwYXRoIGQ9Ik01MCAwYzI3LjU5NiAwIDUwIDIyLjQwNCA1MCA1MHMtMjIuNDA0IDUwLTUwIDUwUzAgNzcuNTk2IDAgNTAgMjIuNDA0IDAgNTAgMFptMCAzOS41NDljNS43NjggMCAxMC40NTEgNC42ODMgMTAuNDUxIDEwLjQ1MSAwIDUuNzY4LTQuNjgzIDEwLjQ1MS0xMC40NTEgMTAuNDUxLTUuNzY4IDAtMTAuNDUxLTQuNjgzLTEwLjQ1MS0xMC40NTEgMC01Ljc2OCA0LjY4My0xMC40NTEgMTAuNDUxLTEwLjQ1MVoiLz48L2NsaXBQYXRoPjxnIGNsaXAtcGF0aD0idXJsKCNjKSI+PHBhdGggZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMDBkODQ1IiBzdHJva2Utd2lkdGg9IjkiIGQ9Ik01MCA1MCA1LjE3MSA3NS44ODIiLz48L2c+PGNsaXBQYXRoIGlkPSJkIj48cGF0aCBkPSJNNTAgMGMyNy41OTYgMCA1MCAyMi40MDQgNTAgNTBzLTIyLjQwNCA1MC01MCA1MFMwIDc3LjU5NiAwIDUwIDIyLjQwNCAwIDUwIDBabTAgMjUuMzZjMTMuNTk5IDAgMjQuNjQgMTEuMDQxIDI0LjY0IDI0LjY0UzYzLjU5OSA3NC42NCA1MCA3NC42NCAyNS4zNiA2My41OTkgMjUuMzYgNTAgMzYuNDAxIDI1LjM2IDUwIDI1LjM2WiIvPjwvY2xpcFBhdGg+PGcgY2xpcC1wYXRoPSJ1cmwoI2QpIj48cGF0aCBmaWxsPSJub25lIiBzdHJva2U9IiMwMGQ4NDUiIHN0cm9rZS13aWR0aD0iOSIgZD0ibTUwIDUwIDUwLTEzLjM5NyIvPjwvZz48cGF0aCBmaWxsPSIjMDBkODQ1IiBkPSJNMzcuMjQzIDUyLjc0NiAzNSA0NWw4LTkgMTEtMyA0IDQtNiA2LTQgMS0zIDQgMS4xMiA0LjI0IDMuMTEyIDMuMDkgNC45NjQtLjU5OCAyLjg2Ni0yLjk2NCA4LjE5Ni0yLjE5NiAxLjQ2NCA1LjQ2NC04LjA5OCA4LjAyNkw0Ni44MyA2NS40OWwtNS41ODctNS44MTUtNC02LjkyOVoiLz48L3N2Zz4=)](https://modrinth.com/mod/server-replay)

## 为什么选择服务端？

与客户端的 [Replay Mod](https://github.com/ReplayMod/ReplayMod) 相比，在服务端录制有着许多好处：

* 能够录制固定的区块：

    + 你可以指定确切的区块大小（不受服务端视距的影响）。

    + 这些记录的区块可以在不影响回放的情况下卸载：

        - 区块不会在卸载与加载的过程中闪烁。

        - 这些区块也不会被录制器加载。（不像 [PCRC](https://github.com/Fallen-Breath/PCRC) 一样会手动加载区块）

        - 录制器可以跳过区块被卸载的时间。

        - *指录制器不会手动加载那些本应被卸载的区块（只有当区块被加载时才会进行记录），并且区块的加载与卸载不会对记录产生影响 ——译者注*

* 能够录制玩家：

    + 玩家不需要安装 Replay 模组。 *（包括本模组和客户端 Replay Mod ——译者注）*

    + 你可以一次性录制多个视角。

    + 录制可以依靠设置项自动进行。

* 能够由管理员随时开启录制行为（或其他有权限的任何人）。

但是这仍然存在一些缺陷和已知问题：

* 一些东西将不会被记录，比如 Boss 栏。

* 要预览录制的 Replay，你必须从服务端下载录制文件。

* 录制玩家内容可能不会和客户端 [Replay Mod](https://github.com/ReplayMod/ReplayMod) 的录制内容 100% 一致。

* 模组兼容性，本模组可能会和其他一些修改了网络的模组冲突，如果你遇到任何兼容性问题请 [提交 Issue](https://github.com/senseiwells/ServerReplay/issues)。

## 用法

此模组需要 [Fabric Loader](https://github.com/FabricMC/fabric-loader)、[Fabric API](https://github.com/FabricMC/fabric) 和 [Fabric-Language-Kotlin](https://github.com/FabricMC/fabric-language-kotlin)。

有以下两种方法来在服务端进行录制：你可以设置从玩家的视角跟随并记录玩家；或者，你可以录制固定的一些区块。

### 快速开始

这部分的文档将会简短地引导你进行基础的构建，同时包含了一些重要的信息。

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

此文件可以被放在客户端的 `./replay_recordings` 文件夹中并被客户端 Replay Mod 打开。

> [!NOTE]
> 如果你要记录 [Carpet](https://github.com/gnembon/fabric-carpet) 假人，你可能需要在设置中启用 `fix_carpet_bot_view_distance`，否则只有假人周围的 2 个区块会被记录。

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

同时，如果你希望手动停止录制，你可以运行 `/replay stop chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度> <是否保存>`。这个指令还可以停止录制并取消保存，例如：

`/replay stop chunks from 0 0 to 5 5 in minecraft:overworld false`  
`/replay stop chunks from 54 67 to 109 124`

此录制之后将会被保存在 `chunk_recording_path` 所指定的文件夹中区块录制器名称目录下。默认情况下，它将被保存在 `./recordings/chunks/<name>/<date-and-time>.mcpr`。

此文件可以被放在客户端的 `./replay_recordings` 文件夹中并被客户端 Replay Mod 打开。

#### 指令

注意：对于所有的指令，玩家必须要有等级 4 的 OP 权限，或如果你有一个权限模组（例如 [LuckPerms](https://luckperms.net/)），玩家可以在拥有权限节点 `replay.commands.replay` 时使用这些指令。

- `/replay enable`
   允许模组按照给定的规则（详见 [匹配规则](#匹配规则设置) 部分）自动记录玩家。

- `/replay disable`
   禁止模组自动录制玩家，这将会同时停止当前的所有的玩家录制和区块录制。

- `/replay start players <玩家>`
  手动开启对给定的玩家的录制。

- `/replay start chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度> named <名称>`
  手动开启对给定的区块范围的录制；如果维度没有被指定，将会使用发起指令的玩家所在的维度；名称决定了录制文件的保存路径。

- `/replay start chunks around <区块X轴位置> <区块Z轴位置> radius <半径> in <维度> named <名称>`
  该指令和上一个指令类似；但你可以指定录制给定区块周围的半径内的区域。

- `/replay stop players <玩家> <是否保存>`
  手动停止对给定玩家的录制，你可以选择性地设置录制是否被保存，默认情况下它将会被保存。

- `/replay stop chunks from <区块X轴起点> <区块Z轴起点> to <区块X轴终点> <区块Z轴终点> in <维度> named <名称>`
  手动停止对于给定区块范围的录制。如果维度没有被指定，将会使用发起指令的玩家所在的维度。你可以选择性地设置录制是否被保存，默认情况下它将会被保存。

- `/replay stop chunks named <名称> <是否保存>`
  该指令和上一个指令类似；但你可以依靠名称来选取指定的区块范围。

- `/replay stop [chunks|players] all <是否保存>`
  手动停止对**所有**区块或玩家的录制。你可以选择性的设置录制是否被保存，默认情况下它将会被保存。

- `/replay status`
  获取一个状态信息，包含录制是否被允许，以及当前所有对玩家和区块的录制的列表，它们已被录制的时长，和它们的文件大小。

- `/replay reload`
  重载 Server Replay 模组的配置文件。

### 配置项

在你启动服务器后，将会生成一个新的文件，位于 `./config/ServerReplay/config.json`，默认情况下它包含如下内容：

```json
{
  "enabled": false,
  "world_name": "World",
  "server_name": "Server",
  "chunk_recording_path": "./recordings/chunks",
  "player_recording_path": "./recordings/players",
  "max_file_size": "0GB",
  "restart_after_max_file_size": false,
  "max_duration": "0s",
  "restart_after_max_duration": false,
  "recover_unsaved_replays": true,
  "include_compressed_in_status": true,
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
  "record_voice_chat": false,
  "player_predicate": {
    "type": "none"
  },
  "chunks": []
}
```

| Config                           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`                      | <p> 默认情况下录制功能是被禁用的。你可以通过修改 `config.json` 然后运行 `/replay reload` 来开启它，或者执行 `/replay [enable\|disable]` 指令。</p>                                                                                                                                                                                                                                                                                                                                                                  |
| `world_name`                   | <p> 在录制文件中呈现的世界名称。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `server_name`                  | <p> 在录制文件中呈现的服务端名称。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `player_recording_path`        | <p> 玩家录制的保存路径。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `chunk_recording_path`         | <p> 区块录制的保存路径。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `max_file_size`                | <p> 回放文件允许录制的最大文件大小，这应当是一个 `数字` + `单位`，例如 `5.2mb`。 </p> <p> 如果录制达到了这个限制，录制器将会停止。这只是个近似值，因此真实的文件大小将会略微大一些。若要不对其进行限制，将此选项设置为 `0` 。 </p> <p> 需要注意的是，当最大文件大小被设置的太大时，这可能会影响服务端运行。为了检查一个文件是否超过了该大小（`>5GB`），文件必须被压缩，而这一过程可能会消耗较多性能。你可以通过运行 `/replay status` 来查看距下次文件大小检查的时间。</p>  |
| `restart_after_max_file_size`  | <p> 如果录制达到了被设置的最大文件大小，那么该录制将会自动地重新开始创建一个新的录制文件。</p>                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `max_duration`                 | <p> 设置录制的最大时长，录制会在达到了指定的最大时长时被停止，这应当是一个 `数字` + `单位`（你也可以有多个单位），例如 `4h 45m 2.1s`。将此选项设置为 `0` 来不限制录制时长。注意：如果一个记录器被暂停了，录制时长将不会增加。 </p>                                                                                                                              |
| `restart_after_max_duration`   | <p> 如果 `max_duration` 被设置了且录制达到了该最大时长，那么录制将会自动重启并创建一个新的录制文件。</p>                                                                                                                                                                                                                                                                                                                        |
| `recover_unsaved_replays`      | <p> 尝试恢复未保存的录制，例如你的服务端崩溃了、在某个录制停止或保存成功之前停止了。这不能保证录制一定不被损坏，但会尝试挽救仍可用的信息。 </p>                                                                                                                                                                                                                                                                                                                                                                                            |
| `include_compressed_in_status` | <p> 在 `/replay status` 中包含压缩的录制文件大小，对于较长的录制而言，这可能导致显示状态信息所需的时间增加，所以你可以禁用此选项。 </p>                                                                                                                                                                                                                                                                                                                                                                                         |
| `fixed_daylight_cycle`         | <p> 如果你不想要长时间恒定的昼夜周期，这将修复录制中的日光周期。此选项应当设置为以 `tick` 为单位的一天的时间，例如 `6000`（半天）。要禁用这一修复，将选项值设为 `-1`。</p>                                                                                                                                                                                                                                                                                                                                                                        |
| `pause_unloaded_chunks`        | <p> 如果某一范围内的区块正在被录制，而该区域又被卸载了，当此选项设置为 `true` 时，录制将会被暂停，直到区块被重新加载时继续录制。 </p> <p> 如果此选项设置为 `false`，区块将会仍然被录制，就像他们被加载了一样。*（指区块将继续以他们卸载时的状态呈现在回放中，而不是直接将区块卸载的时间跳过。————译者注）*  </p>                                                                                                                                                                                                                                                                                              |
| `pause_notify_players`         | <p> 如果 `pause_unload_chunks` 被启用，且此选项也被启用，那么将会在录制的区块区域被暂停或恢复时提醒所有的在线玩家。 </p>                                                                                                                                                                                                                                                                                                                                                                                            |
| `notify_admins_of_status`      | <p> 当启用时，这将会通知管理员录制的开始、结束和保存成功的时间，以及发生的任何错误。 </p>                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `fix_carpet_bot_view_distance` | <p> 如果你要录制 Carpet 假人，你需要启用此选项以将假人视距设置为服务端视距。否则只有假人周围两个区块的距离内会被记录。</p>                                                                                                                                                                                                                                                                                                                                                                                                     |
| `ignore_sound_packets`         | <p> 忽略声音包。如果你正在为一大片区域录制延时摄影，你大概不会想要记录任何声音，因为这将会占用掉极其巨大的存储空间。 </p>                                                                                                                                                                                                                                                                                                                                                                                                       |
| `ignore_light_packets`         | <p> 忽略光照包。光照是同时在客户端和服务端上计算的，所以光照包大多是多余的。</p>                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `ignore_chat_packets`          | <p> 如果聊天内容在你的录制中并不必要的话，停止对聊天包（来自服务端的和来自其他玩家）的记录。</p>                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `ignore_scoreboard_packets`    | <p> 停止对计分板包的录制（例如，如果你有一个显示挖掘的计分板，那么这个计分板以及玩家的分数都不会被录制）。</p>                                                                                                                                                                                                                                                                                                                                                                                                             |
| `optimize_explosion_packets`   | <p> 这通过不向客户端发送爆炸数据包，而只发送爆炸粒子和声音来大幅减小文件大小。</p>                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `optimize_entity_packets`      | <p> 这通过让客户端计算一些实体逻辑来减小文件大小，例如 弹射物 和 TNT。这可能会导致一些不一致，但这大概可以被忽略不计。 </p>                                                                                                                                                                                                                                                                                                                                                                                                      |
| `record_voice_chat`            | <p> 如果安装了 [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) 模组，此选项允许了对语音聊天的支持。当查看录制的时候，你必须安装 [Replay Voice Chat](https://github.com/henkelmax/replay-voice-chat) 模组。 </p>                                                                                                                                                                                    |
| `player_predicate`             | <p> 玩家自动录制的规则，详见 [匹配规则](#匹配规则设置) 部分。</p>                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `chunks`                       | <p> 当服务端启动时，要进行自动录制的区块列表。详见 [区块](#区块设置) 部分。 </p>                                                                                                                                                                                                                                                                                                                                    |

### 区块设置

你可以定义当服务端启动或你启用了 Server Replay 时，要被自动录制的区块区域。

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

### 匹配规则设置

*其实这个东西应该叫“断言”。————译者注*

你可以定义一个匹配规则，它将决定服务端上要被自动录制的玩家。
你可以通过指定某个玩家是否有特定的 uuid，名字，在某个特定的队伍里，或是否是一个管理员，来设置此规则。

在定义规则后，你必须在游戏中运行 `/replay reload`，同时玩家要想被自动录制，他们必须重新登录服务器（且满足匹配规则）。

最基本的选项是记录所有玩家，在这种情况下，您可以使用：
```json5
{
  // ...
  "player_predicate": {
    "type": "all"
  }
}
```

如果你想要只记录带有特定名字或 uui d的玩家，你可以使用：
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

你还可以使用否定规则，用 `not` 然后用 `or` 和 `and` 连接。
例如，如果你想要记录非管理员且玩家名不为 `senseiwells` 的玩家，或在红队中的玩家：
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

如果你正在使用carpet模组且能够召唤假人，你可能会想让假人不被自动记录。
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

## 开发者

如果你想要玩家被记录时的更多的控制权，你可以在你的模组中接入此方法。

要在你的项目中接入 API，你可以下面内容加入你的 `build.gradle.kts` 中：

```kts
repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // For the most recent version use the latest commit hash
    modImplementation("com.github.senseiwells:ServerReplay:281e9e0ec0")
}
```

这里有一个最基本的例子：
```kt
class ExampleMod: ModInitializer {
    override fun onInitialize() {
        ServerPlayConnectionEvents.JOIN.register { connection, _, _ ->
            val player = connection.player
            if (!PlayerRecorders.has(player)) {
                if (player.level().dimension() == Level.END) {
                    val recorder = PlayerRecorders.create(player)
                    recorder.start(log = true)
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
            recorder.start(log = false)
        }
    }
}
```
