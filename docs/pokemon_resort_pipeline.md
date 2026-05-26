# Pokémon Resort Pipeline

This document describes how modified PDSMS authors source content for **Pokémon Resort** and bakes runtime assets without replacing the normal editor workflow.

## Objective

PDSMS remains the map and tileset authoring UI. Pokémon Resort (C++ runtime) loads lean baked files, not PDSMS editor formats.

Goals:

1. Preserve normal PDSMS editing (tile list, painting, layers, height, 2D/3D preview).
2. Add a shared **RTPKS** source tile pack with stable Resort tile IDs.
3. Keep PDSMS map/project saves as the source map format.
4. Bake maps into **RPAK** (shared assets) + **RBMAP** (per-map runtime).

## Source vs Runtime

| Role | Format | Extension | Used by |
|------|--------|-----------|---------|
| Source tile pack | RTPKS | `.rtpks` | PDSMS editor, baker |
| Source map | PDSMS map/project | `.pdsmap` etc. | PDSMS editor |
| Resort sidecar | Map binding metadata | `.resort.json` | Baker, validation |
| Runtime asset pack | RPAK | `.rpak` | Pokémon Resort C++ |
| Runtime baked map | RBMAP | `.rbmap` | Pokémon Resort C++ |

Runtime layout:

```
resort_runtime/
  packs/
    gen4_base.rpak
  maps/
    route_201.rbmap
    test_10x10_tree.rbmap
```

One shared `.rpak` per active RTPKS. One `.rbmap` per baked map entry.

## Why no separate source map editor

The spec deliberately avoids a second map editor or custom Resort tile palette. Users paint with the normal PDSMS tile list using local tile indices. Stable Resort tile IDs exist only in RTPKS and runtime output.

## RTPKS (Resort Tile Pack Source)

Extension: `.rtpks` (ZIP archive)

Layout:

```
manifest.json
pdsms/tileset.pdsts          # canonical native PDSMS payload
pdsms/textures/*.png         # archived texture payloads
pdsms/TilesetThumbnail.png   # optional
index/tile_index.json        # local index → stable Resort tile ID
metadata/tile_metadata.json  # optional, future use
```

### Native PDSMS payload

`/pdsms/tileset.pdsts` is loaded through existing `TilesetIO`. The editor never reconstructs tiles from runtime meshes.

**One PDSMS `Tile` = one RTPKS tile identity.** Multi-cell tiles (trees) remain a single tile.

On load, textures from `pdsms/textures/` are extracted beside `tileset.pdsts` because `TilesetIO` expects textures in the tileset folder.

### tile_index.json

Maps `localIndex` (PDSMS tile list index) → `resortTileId` (uint32, stable across saves).

Rules:

- Tile ID **0 is valid** (empty cells use local index **-1** in the map grid).
- Existing active IDs are preserved on re-save.
- Removed tiles become `inactive` tombstones; IDs are not silently renumbered.
- Any future compact/reindex tool must be explicit and produce a remap report.

Example entry:

```json
{
  "localIndex": 17,
  "resortTileId": 17,
  "key": "tile_0017",
  "status": "active"
}
```

### PDSMS menu actions

**Pokémon Resort → Open RTPKS as Tileset...**  
Loads native payload into the normal tileset, restores tile index binding, refreshes tile selector and map display.

**Pokémon Resort → Save Current Tileset as RTPKS...**  
Writes native payload + textures + tile index. Preserves existing Resort IDs when saving an RTPKS-backed tileset.

## .resort.json sidecar

Stored beside the PDSMS map/project file (`my_map.resort.json` next to `my_map.pdsmap`).

Stores Resort-only metadata:

- `mapId`, `displayName`
- `tilePackSource` (RTPKS path)
- camera preset, spawns
- bake export region, chunk size, height scale
- runtime output paths (RPAK/RBMAP)

Configured via **Pokémon Resort → Configure Resort Map Metadata...**

No custom grid editor is used; export region is numeric fields only.

## Bake pipeline

Actions:

- **Validate Current Resort Setup...**
- **Bake Selected Map to RBMAP...**
- **Bake All Maps to RBMAPs...** (visible, disabled until multi-map baking is implemented)

Flow:

1. Read current `MapGrid` (8 tile layers, 8 height layers, 32×32 grid, optional export crop).
2. For each cell: local index `-1` = empty; `>= 0` resolves through `tile_index.json`.
3. Pull geometry from the PDSMS `Tile` (same conceptual model as `MapLayerGL` preview).
4. Group placements into chunks (default 8×8 tiles) and material groups.
5. Write/update shared `.rpak` from active RTPKS tileset.
6. Write one `.rbmap` for the selected map.

Bake report includes source paths, region, visible cell count, unique tile IDs, chunk/material counts, output paths, warnings/errors.

## RPAK (Runtime Asset Pack)

Shared runtime pack for Pokémon Resort C++:

- Deduplicated textures
- Material metadata
- One runtime tile template per PDSMS tile (no tree decomposition)
- Stable `resortTileId` lookup

Current implementation: ZIP + JSON manifest (designed to evolve toward binary sections).

Does **not** contain editor-only PDSMS payload.

## RBMAP (Runtime Baked Map)

Per-map runtime file:

- Reference to shared RPAK
- Chunked combined geometry summaries
- Material groups per chunk
- Spawns, camera preset
- Collision/nav placeholders (reserved, not fully populated yet)

Does **not** require per-tile draw calls at runtime.

## Tile ID rules (summary)

| Concept | Value |
|---------|-------|
| Empty map cell | local index `-1` |
| Valid Resort tile ID | `0` and above |
| Editor internal IDs | PDSMS local tile indices |
| Runtime/bake IDs | Stable `resortTileId` from RTPKS |

## Acceptance workflow

1. Open a PDSMS tileset (ground + single-list tree tile).
2. **Save Current Tileset as RTPKS...**
3. **Open RTPKS as Tileset...** — tile count, order, sizes, and tree-as-one-tile must match.
4. Paint ground (layer 0) and tree (layer 1) in the normal editor.
5. Save map normally.
6. **Configure Resort Map Metadata...**
7. **Bake Selected Map to RBMAP...**
8. Confirm output: one `.rpak`, one `.rbmap`, bake report shows expected counts and paths.

## Implementation notes

- Code lives under `src/main/java/resort/` split by formats, integration, bake, and runtimeformat.
- UI wiring is in `ResortController`; `MainFrame` only exposes refresh/status hooks.
- `MapEditorHandler` stores the active `ResortTilesetBinding` when an RTPKS is loaded.

## Future work

- **Bake All Maps** when map-matrix batch export is ready.
- Binary RPAK/RBMAP sections for C++ loader.
- Collision, BDHC, permissions from existing PDSMS structures.
- Optional **Append Tileset Into Current Tileset...** for merging RTPKS sources.
