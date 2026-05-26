package resort.bake;

import editor.grid.MapGrid;
import resort.runtimeformat.RbmapDocument;
import resort.runtimeformat.RuntimeChunk;
import resort.runtimeformat.RuntimeMaterialGroup;
import tileset.Tileset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RbmapGridImporter {

    private RbmapGridImporter() {
    }

    public static ImportResult apply(MapGrid grid, RbmapDocument document, Tileset tileset) throws IOException {
        if (grid == null || document == null || tileset == null) {
            throw new IOException("Missing grid, RBMAP document, or tileset");
        }

        ImportResult result = new ImportResult();
        float heightScale = document.bake != null && document.bake.heightScale > 0.0f
                ? document.bake.heightScale
                : 16.0f;

        grid.clearAllLayers();
        Map<String, ChunkBuilder.ChunkPlacement> cells = collectUniquePlacements(document, result);

        for (ChunkBuilder.ChunkPlacement placement : cells.values()) {
            result.placementCount++;
            if (placement.gridX < 0 || placement.gridX >= MapGrid.cols
                    || placement.gridY < 0 || placement.gridY >= MapGrid.rows) {
                result.warnings.add("Skipped placement outside 32x32 grid at "
                        + placement.gridX + "," + placement.gridY);
                continue;
            }
            if (placement.layer < 0 || placement.layer >= MapGrid.numLayers) {
                result.warnings.add("Skipped placement with invalid layer: " + placement.layer);
                continue;
            }
            if (placement.localIndex < 0 || placement.localIndex >= tileset.size()) {
                throw new IOException("RBMAP references local tile index "
                        + placement.localIndex + " but tileset size is " + tileset.size());
            }

            int height = Math.round(placement.worldY / heightScale);
            grid.tileLayers[placement.layer][placement.gridX][placement.gridY] = placement.localIndex;
            grid.heightLayers[placement.layer][placement.gridX][placement.gridY] = height;
            result.cellCount++;
        }

        return result;
    }

    private static Map<String, ChunkBuilder.ChunkPlacement> collectUniquePlacements(
            RbmapDocument document, ImportResult result) {
        Map<String, ChunkBuilder.ChunkPlacement> cells = new LinkedHashMap<>();
        for (RuntimeChunk chunk : document.chunks) {
            if (chunk.materialGroups == null) {
                continue;
            }
            for (RuntimeMaterialGroup group : chunk.materialGroups) {
                if (group.placements == null) {
                    continue;
                }
                for (ChunkBuilder.ChunkPlacement placement : group.placements) {
                    String key = placement.layer + "," + placement.gridX + "," + placement.gridY;
                    ChunkBuilder.ChunkPlacement existing = cells.get(key);
                    if (existing == null) {
                        cells.put(key, placement);
                    } else if (existing.localIndex != placement.localIndex) {
                        result.warnings.add("Conflicting local tile indices at "
                                + placement.gridX + "," + placement.gridY
                                + " layer " + placement.layer);
                    }
                }
            }
        }
        return cells;
    }

    public static class ImportResult {
        public int placementCount;
        public int cellCount;
        public final List<String> warnings = new ArrayList<>();
    }
}
