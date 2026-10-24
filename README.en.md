# KubeJS Editor

An in-game KubeJS editor for Minecraft **1.21.1 / NeoForge**, focused on vanilla and Create recipes, item registration and reversible modpack changes. Version **0.12.0**.

Required: Java 21, NeoForge 21.1.251+ within the 21.1 series, KubeJS 2101.7.2-build.368+ (and Rhino), and JEI 19.27.0.335+ within the 19 series. Create 6.0.10 is optional. Other Minecraft versions and loaders need separate ports; this Jar is not a universal build. Tested combinations and declared dependency ranges are listed separately in the compatibility report.

Press **K** or run `/kjseditor` in a world. Use **Add new**, **Edit existing**, **Create**, and **Applied changes**. JEI supports ghost ingredient dragging and an edit button for recipes with real IDs. Dragging does not consume items.

Pending operations autosave across restarts. Cancel a pending row to discard that change. **Apply all** validates the entire queue, backs up files, writes standard KubeJS scripts and reloads the required stage. Registrations and startup settings require a restart. Validation failures keep your drafts; a written batch that failed to load must be rolled back before further editing.

Reverting an applied recipe first opens an in-game confirmation dialog. Confirming removes the editor's rule and immediately reloads that change, restoring the original recipe. Reverting an editor-added recipe removes it; reverting a disabled recipe enables it again. This does not create a new pending operation or submit other pending changes.

Create stations and addons are grouped under a Create submenu in the new-recipe picker. **View code** includes Code, Checks and File diff views; invalid records remain inspectable. Editor-owned item and block registrations can be deleted after confirmation. Applied registration deletion requires a restart, preserves unrelated pending work and checks for recipe and source references first. Existing copies in inventories or placed blocks may disappear when their registration is removed; a file backup supports rollback. Native mod items cannot be unregistered through this feature.

Core features:

- Vanilla crafting, cooking, stonecutting and smithing transformation; shaped/shapeless 3×3 editing.
- Create device previews, processing recipes, weighted sequenced assembly and up to 9×9 mechanical crafting.
- Live item/fluid search, tag suggestions, components, field errors and adjustable side panels.
- Material, food, tools, sword and armor registration; twelve block types including slabs, stairs, fences, gates, walls, doors, trapdoors, buttons, pressure plates, carpets and pillars.
- Native block interaction options, sound, collision, render layer, tool requirements, movement factors and separate door textures. Registrations require a restart.
- Public Create-family NeoForge configuration editing; custom-validator numeric types are inferred from their declared defaults, checked by the mod and verified after restart.
- Create: Vintage NeoForged 1.21.1-1.3.7: thirteen fixed processing types, native device previews and three safe assembly steps; dynamic mechanics route to their real source.
- Optional JSON processing templates, starting with the opt-in Create Crafts & Additions rolling example.
- Source editing, ownership transfer, hash conflict detection, export and file rollback.

Local singleplayer permits writes. Remote servers support local drafts and export only. Install the main Jar in the client instance; do not install the sources Jar. No third-party mod binaries, production worlds or account files are included.

Common controls and forms have English and Simplified Chinese resources. Detailed diagnostics, some dynamic labels and Create configuration explanations currently retain Chinese; translations are still being completed.

Build with JDK 21 using `bash ./gradlew build` (Linux/macOS) or `./gradlew.bat build` (Windows). Dependencies are pinned and resolved from public Maven repositories; no local `libs` directory is needed. The default profile is `core`. Use `-PcompatProfile=full` for integration development. GitHub Actions configuration is provided but has not been executed on a public repository yet.

Based on Astronomy20's MIT-licensed [KubeJS Recipe Editor](https://github.com/Astronomy20/KubeJS-Recipe-Editor). [ViScriptRecipe](https://github.com/zhenshiz/ViScriptRecipe) inspired interaction design. See [LICENSE](LICENSE), [NOTICE](NOTICE.md), [template documentation](docs/TEMPLATES.md), and [verification report](docs/verification.json).

Crop and storage registration: [REGISTRATION.md](docs/REGISTRATION.md). Storage supports 1–9 rows, 1–12 columns and a capacity of 1–4096 per slot (default 3×9 and 64). Stackable items can exceed their normal limit inside storage; extracted stacks remain legal. Storage scripts require KubeJS Editor at runtime. Experimental simple recipe discovery is available in the last top navigation tab and is off by default: [guide](docs/SIMPLE_RECIPES.md). Unknown serializers must expose supported native JSON; ambiguous JEI displays stay read-only.
