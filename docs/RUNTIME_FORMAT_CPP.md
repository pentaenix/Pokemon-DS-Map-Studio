# Pokémon Resort runtime formats — C++ loader guide

This document describes how **Pokémon DS Map Studio** bakes maps for the **Pokémon Resort** C++ runtime. It is derived from the Java implementation under `src/main/java/resort/`.

The runtime consumes two file types:

| File | Role |
|------|------|
| **`.rpak`** | Shared asset pack: tile mesh templates, materials, PNG textures |
| **`.rbmap`** | One baked map: instance placements grouped by chunk and material |

Editor-only formats (not required at runtime): `.rtpks`, `.pdsmap`, `.resort.json`. Their data is folded into RPAK/RBMAP at bake time.

---

## 1. Container format (all three extensions)

`.rpak`, `.rbmap`, and `.rtpks` are **ZIP archives** with forward-slash paths. There is no custom binary header beyond ZIP.

**C++ steps:**

1. Open the path with a ZIP library (minizip, libzip, etc.).
2. Normalize entry names: `\` → `/`, strip leading `/`.
3. Read `manifest.json` as UTF-8 JSON (use a JSON library: nlohmann/json, RapidJSON, etc.).

Reference implementation: [`RtpksArchive.java`](../src/main/java/resort/formats/RtpksArchive.java).

---

## 2. File layout on disk

Typical export from **File → Save as RBMAP (bake)**:

```
my_level/
  my_level.rbmap      # map instances + metadata
  my_level.rpak       # meshes + textures (sibling, same basename)
```

The RBMAP manifest’s `rpakDependency` is usually the **filename only** (e.g. `my_level.rpak`). Resolve it relative to the RBMAP’s directory:

```
rpak_path = directory(rbmap) / rpakDependency
```

If `rbmap` lives under `.../maps/foo.rbmap` and `rpakDependency` is `packs/gen4_base.rpak`, the studio also supports resolving via a parent `resort_runtime/` root — for Save-as-RBMAP exports, **sibling `.rpak` is the common case**.

Resolution logic (Java): [`RbmapVerifier.resolveRpakPath`](../src/main/java/resort/runtimeformat/RbmapVerifier.java).

---

## 3. RPAK — runtime asset pack

### 3.1 Archive contents

| Entry | Required | Description |
|-------|----------|-------------|
| `manifest.json` | Yes | Pack manifest (JSON) |
| `meshes/tile_{resortTileId}.json` | Per tile | Tile mesh in model space |
| `textures/{textureName}` | Per texture | PNG bytes; `textureName` matches material (e.g. `grass.png`) |

Example:

```
manifest.json
meshes/tile_0.json
meshes/tile_17.json
textures/ground.png
textures/tree.png
```

### 3.2 Manifest schema (`pokemon_resort.rpak`, version `1`)

```json
{
  "format": "pokemon_resort.rpak",
  "version": 1,
  "packId": "gen4_base",
  "sourceRtpks": "/path/to/source.rtpks",
  "tiles": [
    {
      "resortTileId": 17,
      "localIndex": 17,
      "width": 2,
      "height": 2,
      "materialCount": 1,
      "vertexCount": 24,
      "triangleCount": 12
    }
  ],
  "materials": [
    {
      "materialId": 0,
      "name": "Material0",
      "textureName": "ground.png",
      "alpha": 255
    }
  ],
  "textures": [
    { "name": "ground.png" }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `format` | string | Must be `"pokemon_resort.rpak"` |
| `version` | int | Must be `1` |
| `packId` | string | Logical pack id |
| `sourceRtpks` | string | Provenance; optional for loader |
| `tiles[]` | array | Summary per **resort tile id** (not full geometry) |
| `materials[]` | array | Material id → texture name + alpha |
| `textures[]` | array | List of texture files present in archive |

**Tile identity:** `resortTileId` is a stable `uint32` (0 is valid). Empty map cells are not stored in RBMAP; they simply have no placement.

### 3.3 Mesh entry schema (`meshes/tile_{resortTileId}.json`)

One JSON file per active tile. Geometry is **interleaved by primitive**, not indexed.

```json
{
  "width": 2,
  "height": 2,
  "xOffset": 0.5,
  "yOffset": 0.5,
  "triangles": [],
  "quads": [ /* float[] */ ],
  "texCoordsTri": [],
  "texCoordsQuad": [ /* float[] */ ],
  "normalCoordsTri": [],
  "normalCoordsQuad": [ /* float[] */ ],
  "textureIds": [ 0 ]
}
```

| Field | Type | Layout |
|-------|------|--------|
| `width`, `height` | int | Tile footprint in **grid cells** (1–6) |
| `xOffset`, `yOffset` | float | Sub-cell offset in tile space (already applied to placement `worldX`/`worldZ` in RBMAP) |
| `triangles` | float[] | 9 floats per triangle: 3 vertices × (x,y,z) |
| `quads` | float[] | 12 floats per quad: 4 vertices × (x,y,z) |
| `texCoordsTri` | float[] | 6 floats per triangle: 3 vertices × (u,v) |
| `texCoordsQuad` | float[] | 8 floats per quad: 4 vertices × (u,v) |
| `normalCoordsTri` | float[] | 9 floats per triangle |
| `normalCoordsQuad` | float[] | 12 floats per quad |
| `textureIds` | int[] | Material ids used by this tile; aligns with PDSMS multi-material tiles |

**Primitive counts:**

- `numTriangles = triangles.length / 9`
- `numQuads = quads.length / 12` → render each quad as **two** triangles (0-1-2, 0-2-3) unless you have a quad pipeline.

Serializer: [`RuntimeMeshSerializer.java`](../src/main/java/resort/runtimeformat/RuntimeMeshSerializer.java).

### 3.4 Materials and textures

- `materialId` in placements refers to `materials[].materialId`.
- Look up `materials[i].textureName`, load `textures/{textureName}` from the same RPAK ZIP.
- `alpha` is 0–255 (PDSMS material alpha).

---

## 4. RBMAP — runtime baked map

### 4.1 Archive contents

| Entry | Required | Description |
|-------|----------|-------------|
| `manifest.json` | Yes | Entire baked map (JSON) |

There are **no** separate binary chunk files in v1; all instance data lives in `manifest.json`.

### 4.2 Manifest schema (`pokemon_resort.rbmap`, version `1`)

```json
{
  "format": "pokemon_resort.rbmap",
  "version": 1,
  "mapId": "route_201",
  "displayName": "Route 201",
  "rpakDependency": "route_201.rpak",
  "defaultCamera": "gen4_platinum_default_exterior",
  "spawns": [
    {
      "id": "player_start",
      "map": [0, 0],
      "tile": [5, 5],
      "layer": 0,
      "facing": "south"
    }
  ],
  "bake": {
    "mapCoordinate": [0, 0],
    "exportRegion": {
      "map": [0, 0],
      "x": 0,
      "y": 0,
      "width": 32,
      "height": 32
    },
    "chunkSizeTiles": 8,
    "heightScale": 16.0
  },
  "chunks": [ /* RuntimeChunk[] */ ],
  "collision": { "status": "reserved" },
  "editorConfig": { /* optional, editor-only round-trip */ }
}
```

| Field | Type | C++ usage |
|-------|------|-----------|
| `rpakDependency` | string | Relative or absolute path to RPAK |
| `defaultCamera` | string | Game camera preset id |
| `spawns[]` | array | Spawn points (map matrix coords + tile coords) |
| `bake.mapCoordinate` | int[2] | Which map cell in a multi-map matrix this bake used |
| `bake.exportRegion` | object | Sub-rectangle of the 32×32 grid that was baked |
| `bake.chunkSizeTiles` | int | Chunk grid size (default **8**) |
| `bake.heightScale` | float | Multiply stored height layers to world Y (default **16.0**) |
| `chunks[]` | array | Spatial/material groupings for rendering |
| `editorConfig` | object | **Optional.** Full editor metadata; safe to ignore in runtime |
| `collision` | object | Placeholder; not populated yet |

### 4.3 Chunk and placement schema

```json
{
  "chunkX": 0,
  "chunkY": 0,
  "minTileX": 0,
  "minTileY": 0,
  "maxTileX": 7,
  "maxTileY": 7,
  "materialGroups": [
    {
      "materialId": 0,
      "placements": [
        {
          "resortTileId": 17,
          "localIndex": 17,
          "layer": 1,
          "materialId": 0,
          "gridX": 10,
          "gridY": 12,
          "worldX": -5.5,
          "worldY": 32.0,
          "worldZ": -3.5,
          "tileWidth": 2,
          "tileHeight": 2,
          "vertexCount": 24,
          "triangleCount": 12
        }
      ]
    }
  ]
}
```

**Chunk bounds** (`minTileX` … `maxTileY`) are inclusive grid indices inside the export region. Use them for coarse culling.

**Placement fields:**

| Field | Meaning |
|-------|---------|
| `resortTileId` | Key into `meshes/tile_{id}.json` in RPAK |
| `localIndex` | Editor tile list index (debug / re-import only) |
| `layer` | 0–7 (stacking); higher layers draw on top |
| `materialId` | Which sub-mesh/material pass this draw belongs to |
| `gridX`, `gridY` | Integer cell in the 32×32 map grid |
| `worldX`, `worldY`, `worldZ` | **Final** world translation for this instance |
| `tileWidth`, `tileHeight` | Footprint (cells) |
| `vertexCount`, `triangleCount` | Hints from source tile (optional validation) |

**Important:** RBMAP does **not** embed vertex buffers per placement. The C++ renderer **must** load the shared mesh from RPAK and transform it to `(worldX, worldY, worldZ)`.

**Duplicate placements:** A tile with multiple materials produces **one placement per material** in the same cell. Your renderer should draw each placement (same transform, different texture/material). The editor re-import dedupes by `(layer, gridX, gridY)` and keeps one local index.

Builder: [`ChunkBuilder.java`](../src/main/java/resort/bake/ChunkBuilder.java).

---

## 5. Coordinate system and conventions

### 5.1 Map grid (authoring space)

- Fixed **32 × 32** cells per map layer ([`MapGrid.cols` / `rows`](../src/main/java/editor/grid/MapGrid.java)).
- **8** tile layers, **8** height layers.
- Empty cell: `localIndex == -1` (not written to RBMAP).
- Array indexing in Java: `tileLayers[layer][gridX][gridY]`.

### 5.2 World space (runtime)

Baker computes placement position as:

```
worldX = (gridX - 16) + tile.xOffset
worldZ = (gridY - 16) + tile.yOffset
worldY = heightLayerValue * heightScale
```

- **Horizontal plane:** X and Z (origin near center of 32×32 grid).
- **Vertical:** Y is up.
- `heightScale` comes from `bake.heightScale` (default 16.0).
- `tile.xOffset` / `tile.yOffset` are already folded into `worldX` / `worldZ`; mesh vertices stay in **tile model space**.

### 5.3 Chunk indexing

Within the export region `(exportRegion.x, exportRegion.y, width, height)`:

```
chunkX = (gridX - exportRegion.x) / chunkSizeTiles
chunkY = (gridY - exportRegion.y) / chunkSizeTiles
```

Default `chunkSizeTiles = 8`.

### 5.4 Mesh space

- Tile meshes in RPAK are authored in PDSMS/OpenGL tile space (see mesh arrays above).
- Apply a **translation** matrix: `T(worldX, worldY, worldZ)` to each vertex before upload/draw.
- If your engine uses Y-up (recommended), align with the baker’s `worldY` as vertical.

---

## 6. C++ load order (recommended)

```
1. Load RBMAP ZIP → parse manifest.json → RbmapDocument
2. Resolve RPAK path from rbmap path + rpakDependency
3. Load RPAK ZIP → parse manifest.json → RpakDocument
4. Preload textures:
     for each materials[i]:
       load ZIP entry "textures/" + materials[i].textureName → GPU texture
5. Preload meshes:
     for each tiles[i].resortTileId used in placements:
       load "meshes/tile_" + id + ".json" → MeshPayload
6. Optional: index materials by materialId, meshes by resortTileId
7. Render loop (see §7)
```

Validate:

- `format` / `version` on both manifests.
- Every `placement.resortTileId` has `meshes/tile_{id}.json` in RPAK ([`RpakReader.hasMeshEntry`](../src/main/java/resort/runtimeformat/RpakReader.java)).
- Every `materialId` used has a material entry and texture file.

---

## 7. C++ rendering strategy

### 7.1 High-level pipeline

```
for each chunk in rbmap.chunks (optional: frustum vs chunk AABB from min/max tile):
  for each materialGroup in chunk.materialGroups:
    bind texture for materialGroup.materialId
    for each placement in materialGroup.placements:
      mesh = meshCache[placement.resortTileId]
      model = Translate(placement.worldX, placement.worldY, placement.worldZ)
      draw mesh quads + triangles with this material's texture
```

Sort **opaque** draws by `materialId` (texture batching). Respect `layer` for draw order if you do not use depth correctly (Painter’s algorithm: lower layer first, or rely on depth buffer with correct worldY).

### 7.2 Building draw geometry from `MeshPayload`

Pseudocode:

```cpp
struct MeshPayload {
  int width, height;
  float xOffset, yOffset;
  std::vector<float> triangles;      // 9 per tri
  std::vector<float> quads;          // 12 per quad
  std::vector<float> texCoordsTri;   // 6 per tri
  std::vector<float> texCoordsQuad;  // 8 per quad
  std::vector<float> normalCoordsTri, normalCoordsQuad;
  std::vector<int> textureIds;
};

void appendQuadAsTriangles(
    const MeshPayload& m, size_t quadIndex,
    const glm::mat4& world, MaterialId mat,
    std::vector<Vertex>& out)
{
  size_t base = quadIndex * 12;
  // vertices v0..v3 from m.quads[base .. base+11]
  // UVs from m.texCoordsQuad[quadIndex*8 .. +7]
  // emit tri (v0,v1,v2) and tri (v0,v2,v3) transformed by world
}

void drawPlacement(const Placement& p, const MeshPayload& mesh, Texture* tex) {
  glm::mat4 M = glm::translate(glm::mat4(1), {p.worldX, p.worldY, p.worldZ});
  // Only use submesh matching p.materialId if textureIds size > 1
  // (match PDSMS: one draw call per material pass on the tile)
  ...
}
```

### 7.3 What not to do

- Do not parse `.pdsmap` or `.rtpks` in the game runtime for baked maps.
- Do not expect per-placement mesh binaries inside RBMAP.
- Do not assume `editorConfig` exists (older bakes may omit it).
- Do not re-derive `worldX`/`worldZ` from `gridX`/`gridY` unless you also load tile offsets from RPAK meshes; use the baked `world*` fields.

---

## 8. Spawns and camera

**Spawns** (`spawns[]`):

| Field | Meaning |
|-------|---------|
| `id` | Spawn point name (e.g. `player_start`) |
| `map` | int[2] map-matrix coordinates |
| `tile` | int[2] tile coordinates within the 32×32 grid |
| `layer` | Layer index |
| `facing` | `"north"` \| `"south"` \| `"east"` \| `"west"` |

Convert tile coords to world using the same grid rules as placements (tile center ≈ `(tile[0] - 16, height * heightScale, tile[1] - 16)` if you need a point; exact height may require sampling the height layer at re-import).

**Camera:** `defaultCamera` is an opaque string id for your game’s camera system.

---

## 9. Versioning and compatibility

| Asset | `format` | `version` |
|-------|----------|-----------|
| RPAK | `pokemon_resort.rpak` | `1` |
| RBMAP | `pokemon_resort.rbmap` | `1` |

Reject unknown `format` or unsupported `version` with a clear error.

**Forward compatibility:** Extra JSON fields may appear; ignore unknown keys.

**Planned (not in v1):** binary mesh sections, collision meshes, navigation grids in `collision` (currently `"status": "reserved"`).

---

## 10. Minimal loader checklist

- [ ] ZIP read with normalized paths
- [ ] JSON parse `manifest.json` for RBMAP and RPAK
- [ ] Resolve sibling/relative RPAK from `rpakDependency`
- [ ] Cache `meshes/tile_{resortTileId}.json` keyed by id
- [ ] Cache PNG textures by `materials[].textureName`
- [ ] Iterate `chunks` → `materialGroups` → `placements`
- [ ] Transform mesh by `(worldX, worldY, worldZ)`
- [ ] Batch by `materialId` / texture
- [ ] Optional: chunk frustum culling using `minTileX`…`maxTileY`

---

## 11. Java reference map

| Concern | Source file |
|---------|-------------|
| ZIP container | `resort/formats/RtpksArchive.java` |
| RBMAP write/read | `resort/runtimeformat/RbmapWriter.java`, `RbmapReader.java` |
| RPAK write/read | `resort/runtimeformat/RpakWriter.java`, `RpakReader.java` |
| RBMAP schema | `resort/runtimeformat/RbmapDocument.java` |
| RPAK schema | `resort/runtimeformat/RpakDocument.java` |
| Mesh JSON | `resort/runtimeformat/RuntimeMeshSerializer.java` |
| Bake placements | `resort/bake/ChunkBuilder.java`, `PdsmsMapExtractor.java` |
| Path resolution | `resort/runtimeformat/RbmapVerifier.java` |
| Authoring overview | `docs/pokemon_resort_pipeline.md` |

---

## 12. Example: end-to-end paths

After **Save as RBMAP** to `/game/levels/route_201.rbmap`:

1. Open ZIP `/game/levels/route_201.rbmap` → `manifest.json`.
2. Read `rpakDependency` → `"route_201.rpak"`.
3. Open ZIP `/game/levels/route_201.rpak`.
4. For placement `resortTileId: 42`, `materialId: 0`:
   - Load `meshes/tile_42.json`.
   - Find `materials` where `materialId == 0` → `textureName`.
   - Load `textures/{textureName}`.
   - Draw mesh at `(worldX, worldY, worldZ)`.

This matches exactly what the Java baker wrote in [`ResortBaker.bakeToPaths`](../src/main/java/resort/bake/ResortBaker.java).
