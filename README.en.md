# KubeJS Editor

An in-game visual KubeJS editor for **Minecraft 1.21.1 / NeoForge**. Current prerelease: **0.18.0**.

Edit recipes through JEI, generate standard KubeJS scripts, register content, and manage changes with validation, backups, batch revocation and rollback. Generic compatibility patches reuse real data sources and public mod interfaces.

[Download 0.18.0](https://github.com/meizuishan/kubejs-editor/releases/tag/v0.18.0) · [中文](README.md) · [User guide](docs/distribution/GUIDE.md) · [Compatibility matrix](docs/distribution/COMPATIBILITY.md)

## Features

- Add, replace and disable recipes using JEI ingredient dragging, quantities, components and native parameters.
- Dedicated vanilla and Create authoring layouts, mechanical crafting, sequenced assembly and selected addon integration.
- Register items, food, tools, armor, common blocks, crops and configurable storage blocks.
- Generic field patches for native JSON values, item/fluid IDs, components and lists, with scoped replacement, previews and undo.
- Automatic method selection based on actual source evidence and field/slot coverage; confirmed manual mappings remain available.
- Autosaved drafts, generated code/diffs, conflict checks, single-reload batch revocation and recoverable transactions.
- Public mod configuration editing, with global settings distinguished from per-recipe parameters.

## Supported versions

| Dependency | Declared range |
|---|---|
| Minecraft | **1.21.1 only** |
| Java | **21** |
| NeoForge | **[21.1.199, 21.2)** |
| KubeJS | **2101.7.2-build.368 or later**, subject to its own requirements |
| Rhino | A version compatible with the installed KubeJS |
| JEI | **[19.25.0.322, 20)**; required on the client |
| Create | Optional; integration range **[6.0.10, 6.1)** |

This Jar does not target Forge, Fabric or other Minecraft versions. These ranges do not permit arbitrary dependency combinations: some newer JEI releases require newer NeoForge. Declared support, successful builds and actual client tests are documented separately in the [version matrix](docs/distribution/COMPATIBILITY.md).

Version 0.18.0 passed **613 regression checks, 39 serialization fixtures, five representative build combinations and 270 final isolated-client checks**. All mods, machines and dependency combinations have not been game-tested. Full physical-machine processing, multiplayer and a fresh-JVM native-registry lifecycle were not revalidated for this version.

## Force compatibility: scope and limits

Force compatibility is **not a universal rewrite of arbitrary machine logic**. It looks for authoritative recipes, public wrappers, complete native-codec matches and datapack registries, then compares the available editing methods.

- A real writable source allows raw-field editing when automatic ingredient mappings are incomplete. Unchanged native structure, components, tags and quantities are preserved.
- The native codec, registry and loaded result still determine validity. Changing a JEI display alone cannot change machine behavior.
- Ambiguous sources require confirmation. Manually selecting a source does not prove that it belongs to the displayed recipe; editing shared data can affect every consumer.
- Configuration-driven, hardcoded, private-manager or shared-loot behavior is not guaranteed to be editable as a normal recipe. There is no arbitrary Java/private-field mutation backend.
- Ordinary recipes normally reload; native datapack registries, registrations and some settings require a restart. Registry overlays require a compatible editor at runtime.
- Direct application targets local singleplayer/integrated servers. Remote-server writing is not provided. See the [generic patch guide](docs/distribution/GUIDE.md).

## Quick start

Close Minecraft, install the main Jar and matching KubeJS/Rhino/JEI dependencies, and keep only one editor version in the instance. The release bundle includes a PowerShell 7 installer with old-Jar backup support.

Press **K** or run `/kubejseditor` (`/kjseditor` also works). Open a recipe through JEI or the last **Force compatibility** tab. Review the queue and generated diff before applying. Batch revocation is under **Applied changes → Batch deletion**.

Raw fields: **More → Generic field patches**. Method selection: **More → Adaptation method**. Some dynamic UI and detailed diagnostics remain in Chinese.

Storage supports 1–9 rows, 1–12 columns and 1–4096 items per slot (default 3×9, 64 per slot). Non-stackable items remain limited to one; extraction and drops use legal stack sizes. Storage scripts require this mod at runtime.

## Build and attribution

Use JDK 21 and `./gradlew build`. Dependencies are resolved from public repositories with pinned hashes. See the [development guide](docs/distribution/BUILD.md). Do not commit isolated instances, player worlds, local dependencies or credentials.

Based on [Astronomy20/KubeJS-Recipe-Editor](https://github.com/Astronomy20/KubeJS-Recipe-Editor), retaining its Git history, MIT license and copyright. See [NOTICE.md](NOTICE.md) for reuse and design references. This project is not an official Minecraft, NeoForge, KubeJS, JEI or Create product.
