<div align="center">

# MINEZ Roughly Enough Items

A fork of [Roughly Enough Items](https://github.com/shedaniel/RoughlyEnoughItems) maintained for the MINEZ server.

<a href="README.md">English</a> ｜
<a href="docs/README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/shedaniel/RoughlyEnoughItems"><img src="https://img.shields.io/badge/upstream-Roughly%20Enough%20Items-1976d2" alt="upstream"></a>
<a href="#supported-versions"><img src="https://img.shields.io/badge/Minecraft-26.3-4caf50" alt="Minecraft"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<br>

<a href="https://github.com/MINEZ/RoughlyEnoughItems/issues">Issue Tracker</a> ｜
<a href="https://github.com/MINEZ/RoughlyEnoughItems/releases">Releases</a> ｜
<a href="#changes-from-upstream">What's Changed</a>

</div>

> [!IMPORTANT]
> **This is an unofficial fork and is not affiliated with, endorsed by, or supported by shedaniel or the Roughly Enough Items project.**
>
> Never take anything about this fork to the upstream repository or its community channels. Use [this repository's issue tracker](https://github.com/MINEZ/RoughlyEnoughItems/issues) instead.
>
> To tell the two apart, reproduce the problem on an official build first:
>
> - **It still happens without this fork** — an upstream problem. Report it upstream as you normally would, with no mention of this fork.
> - **It does not happen without this fork** — ours. It belongs here, and only here.

## About

This repository follows the upstream `26.3` branch and adds a small set of changes needed by the MINEZ server, a Paper server running [CraftEngine](https://github.com/Xiao-MoMi/craft-engine). Everything not listed below is identical to upstream.

### Supported Versions

Following upstream, Minecraft 26.3 on Fabric and NeoForge. The ReiSync item stream is wired up for Fabric only; every other change lives in the shared code and applies to both loaders.

## Changes from Upstream

| Change | Type | Loaders |
| --- | --- | --- |
| Receiving CraftEngine items from the ReiSync server plugin | Added | Fabric |
| Right-click opening an item's usages | Fixed | Fabric, NeoForge |
| CraftEngine items in recipe and usage lookups | Fixed | Fabric, NeoForge |
| “Recipes not full” warning | Removed | Fabric, NeoForge |

**ReiSync item stream.** CraftEngine items only exist on the server, so a client has no way to list them by itself. The ReiSync server plugin pushes every CraftEngine item on the `reisync:items` channel shortly after a player joins, and again after `/ce reload`; this fork receives them and adds them to the item list. Each item arrives as the NBT Paper serializes it and is rebuilt one data component at a time, so a component this client does not understand is skipped instead of losing the whole item, and a server on a different Minecraft version than the client still works. Entries are added 200 per tick (adjustable with `-Dreisync.batch=<n>`) without refiltering the whole list, so joining a server does not stall the game, and they are removed again on leaving. Launch with `-Dreisync.debug=true` to log the items that fail to decode.

**Right-click usages.** Some clients, NetEase's among them, never pass the right mouse button's release on to REI, so right-clicking an item did nothing and only the U key opened its usages. Right-click now acts on press instead. It is never used for dragging, so nothing is lost; the left button still acts on release, which keeps press-and-drag for pulling items out.

**CraftEngine recipes.** A CraftEngine item is a vanilla item underneath, with its real identity stored as `craftengine:id` in `minecraft:custom_data`. Upstream compares items by their base item when looking up recipes and usages, so a CraftEngine item picked up every vanilla recipe of its base item, and the other way round. That id is now part of the comparison, and the wildcard fallback upstream runs when a lookup finds nothing keeps it as well. Exact comparisons are unchanged.

**Recipes-not-full warning.** The MINEZ server does not run REI, so REI never receives the full recipe data from it. With Local Recipes Fallback set to Never, upstream shows a warning in the top-left corner on every join. That is expected here, so the warning is not shown at all.

## Building

Requires JDK 25.

```bash
GITHUB_RUN_NUMBER=<upstream build>.<fork revision> ./gradlew :fabric:build
```

The result lands in `fabric/build/libs/`, for example `RoughlyEnoughItems-26.3.<upstream build>.<fork revision>.jar`. On Windows PowerShell, set the variable with `$env:GITHUB_RUN_NUMBER = "<upstream build>.<fork revision>"` first.

## Versioning

The version is `<base_version>.<build number>`: `base_version` comes from `gradle.properties`, and the build number from the `GITHUB_RUN_NUMBER` environment variable, or `9999` when it is unset. Releases of this fork set the build number to the upstream build they are based on followed by the fork revision: in `21.9.813.3`, `813` is the upstream build and the trailing `.3` is the fork revision. This keeps our version numbers from colliding with upstream releases.

The download links on the upstream project point at official builds and do **not** include these changes.

## Upstream

Roughly Enough Items is developed by [shedaniel](https://github.com/shedaniel) and its contributors. For the mod as upstream ships it, the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/roughly-enough-items) remains the right place to go — not for anything in this fork. Mod developers who want to depend on REI should use the official Maven artifacts described in the [upstream README](https://github.com/shedaniel/RoughlyEnoughItems#maven); this fork is not published to any Maven repository.

The changes here are built for one server's needs and are maintained separately. Please keep discussion of them out of upstream's channels.

## License

Roughly Enough Items is open source under the MIT License, Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel. Modifications in this fork are provided under the same terms. See [LICENSE](LICENSE) for the full text.
