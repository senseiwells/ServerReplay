# Server Replay

A completely server-side implementation of the replay mod, this mod allows you
to record multiple players that are online on a server at a time. This will
produce replay files which can then be used with the replay mod for rendering.

Currently, the implementation only allows for recording and following specific
players, recording a static area is not yet supported.

## Usage

This mod requires the fabric launcher, then to install just simply drag the
mod into your mods' folder.

For very simply use-cases you can run `/replay start <player(s)>` and `/replay stop <player(s)>`,
however this mod also provides the ability to configure and automatically start and
stop replays. The rest of this document will detail how you can configure this.

### Commands

A note for all commands; players must either have op (level 4), alternatively if you
have a permission mod (for example, [LuckPerms](https://luckperms.net/)) players can
have the permission `replay.commands.replay` to access these commands.

- `/replay enable` Enables the replay mod to automatically recording players that should
  be recorded based on the given predicate (more details in the [Predicates](#predicates) section).
- `/replay disable` Disables the replay mod from automatically recording players, this will
  also stop any currently recording players.
- `/replay start <player(s)>` Manually starts recording the replay for some given player(s).
- `/replay stop <player(s)> <save?>` Manually stops recording the replay for some given player(s),
  you may optionally pass in whether the replay should be saved; by default, this is true.
- `/replay stop <save?>` Manually stops **all** replays you may optionally pass in whether the
  replay should be saved; by default, this is true.
- `/replay status` Sends a status message of whether replay is enabled and a list of all the
  players that are currently being recorded and how long they've been recorded for.
- `/replay reload` Reloads the config file for the replay mod.

### Configuring

After you boot the server a new file will be generated in the path 
``./config/ServerReplay/config.json``, by default it should look like:

```json
{
  "enabled": false,
  "world_name": "World",
  "server_name": "Server",
  "recording_path": "./recordings",
  "predicate": {
    "type": "none"
  }
}
```

### Enabling and Disabling

By default, the replay functionality is disabled, you can enable it by either
changing the `config.json` then running `/replay reload` in game, or by simply
just running `/replay enable` or `/replay disable` in game.

This will then check whether any online players should be recorded and start their
recording accordingly.

### Recording Path

This determines where your recordings are saved. By default, this is `./recordings`, 
then there will be subdirectories which are named according to the player's uuid, then
any replays will be located in there which are named by date and time.
For example: `./recordings/d4fca8c4-e083-4300-9a73-bf438847861c/2023-05-22--13-38-13.mcpr`

### Predicates

You must define a predicate, which determines which players on your server
will be recorded, you can do this by specifying whether players have a specific uuid, 
name, are on a specific team, or whether they are an operator.

After defining a predicate you must run `/replay reload` in game then players must 
re-log if they want to be recorded (and meet the predicate criteria). 

Most basic option is just to record all players in which case you can use:
```json
{
  // ...
  "predicate": {
    "type": "all"
  }
}
```

If you wanted to only record players with specific names or uuids you can do the following:
```json
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

```json
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
```json
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
```json
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
For example if you wanted to record all non-operators that also don't have the name 'senseiwells' or is on the red team:
```json
{
  // ...
  "predicate": {
    "type": "and",
    "first": {
      "type": "not",
      "predicate": {
        "type": "has_op",
        "level": 4
      }
    },
    "second": {
      "type": "not",
      "predicate": {
        "type": "or",
        "first": {
          "type": "has_name",
          "names": [
            "senseiwells"
          ]
        },
        "second": {
          "type": "in_team",
          "teams": [
            "Red"
          ]
        }
      }
    }
  }
}
```

## Developers

If you want more control over when players are recorded, and have more specific
predicates you can implement this into your own mod.

To implement the API into your project you can simply add the
following to your `build.gradle.kts`

```kts
repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // For the most recent version use the latest commit hash
    val version = "86a2d48e8a"
    modImplementation("com.github.Senseiwells:ServerReplay:$version")
}
```

You can then set the predicate to your own by setting the field:
```kt
class ExampleMod: ModInitializer {
    override fun onInitialize() {
        PlayerRecorders.predicate = Predicate<ServerPlayer> { player ->
            player.isSurvival
        }
    }
}
```