# Server Replay

A completely server-side implementation of the replay mod, this mod allows you
to record multiple players that are online on a server at a time. This will
produce replay files which can then be used with the replay mod for rendering.

Currently, the implementation only allows for recording and following specific
players, recording a static area is not yet supported.

## Usage

This mod requires the fabric launcher, then to install just simply drag the
mod into your mods folder.

After you boot the server a new file will be generated in the path 
``./config/ServerReplay/config.json``, by default it should look like:

```json
{
  "enabled": false,
  "world_name": "World",
  "server_name": "Server",
  "recording_path": "./recordings",
  "has_predicate": true,
  "predicate": {
    "type": "none"
  }
}
```

### Enabling

By default, the replay functionality is disabled, you can enable it by either
changing the `config.json` then running `/replay reload` in game, or by simply
just running `replay enable` or `replay disable` in game.

If you enable it while players are online it will **require** them to re-log
for the mod to record them.
And if you disable it while players are online it will stop recording for all
players.

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

If you only want to record players on specific teams:
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