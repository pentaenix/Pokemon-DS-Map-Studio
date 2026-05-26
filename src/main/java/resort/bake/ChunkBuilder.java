package resort.bake;

import editor.grid.MapGrid;
import resort.formats.ResortMapMetadata;
import resort.runtimeformat.RuntimeChunk;
import resort.runtimeformat.RuntimeMaterialGroup;
import tileset.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkBuilder {

    public BuildResult build(PdsmsMapExtractor.ExtractResult extractResult,
                             ResortMapMetadata.ExportRegion region,
                             int chunkSizeTiles,
                             float heightScale) {
        Map<String, RuntimeChunk> chunks = new HashMap<>();
        int materialGroupCount = 0;

        for (PdsmsMapExtractor.PlacedTile placed : extractResult.placedTiles) {
            int chunkX = (placed.gridX - region.x) / chunkSizeTiles;
            int chunkY = (placed.gridY - region.y) / chunkSizeTiles;
            String chunkKey = chunkX + "," + chunkY;
            RuntimeChunk chunk = chunks.computeIfAbsent(chunkKey, key -> {
                RuntimeChunk created = new RuntimeChunk();
                created.chunkX = chunkX;
                created.chunkY = chunkY;
                created.minTileX = region.x + chunkX * chunkSizeTiles;
                created.minTileY = region.y + chunkY * chunkSizeTiles;
                created.maxTileX = Math.min(region.x + (chunkX + 1) * chunkSizeTiles, region.x + region.width) - 1;
                created.maxTileY = Math.min(region.y + (chunkY + 1) * chunkSizeTiles, region.y + region.height) - 1;
                return created;
            });

            List<Integer> materialIds = placed.tile.getTextureIDs();
            if (materialIds.isEmpty()) {
                materialIds = new ArrayList<>();
                materialIds.add(0);
            }

            for (Integer materialId : materialIds) {
                RuntimeMaterialGroup group = findOrCreateGroup(chunk, materialId);
                group.placements.add(createPlacement(placed, materialId, heightScale));
                materialGroupCount++;
            }
        }

        BuildResult result = new BuildResult();
        result.chunks = new ArrayList<>(chunks.values());
        result.materialGroupCount = materialGroupCount;
        return result;
    }

    private RuntimeMaterialGroup findOrCreateGroup(RuntimeChunk chunk, int materialId) {
        for (RuntimeMaterialGroup group : chunk.materialGroups) {
            if (group.materialId == materialId) {
                return group;
            }
        }
        RuntimeMaterialGroup group = new RuntimeMaterialGroup();
        group.materialId = materialId;
        chunk.materialGroups.add(group);
        return group;
    }

    private ChunkPlacement createPlacement(PdsmsMapExtractor.PlacedTile placed,
                                           int materialId,
                                           float heightScale) {
        ChunkPlacement placement = new ChunkPlacement();
        placement.resortTileId = placed.resortTileId;
        placement.localIndex = placed.localIndex;
        placement.layer = placed.layer;
        placement.materialId = materialId;
        placement.gridX = placed.gridX;
        placement.gridY = placed.gridY;
        placement.worldX = (placed.gridX - MapGrid.cols / 2.0f) + placed.tile.getXOffset();
        placement.worldZ = (placed.gridY - MapGrid.rows / 2.0f) + placed.tile.getYOffset();
        placement.worldY = placed.height * heightScale;
        placement.tileWidth = placed.tile.getWidth();
        placement.tileHeight = placed.tile.getHeight();
        placement.vertexCount = countVertices(placed.tile);
        placement.triangleCount = countTriangles(placed.tile);
        return placement;
    }

    private int countVertices(Tile tile) {
        int count = 0;
        if (tile.getVCoordsTri() != null) {
            count += tile.getVCoordsTri().length / 3;
        }
        if (tile.getVCoordsQuad() != null) {
            count += tile.getVCoordsQuad().length / 3;
        }
        return count;
    }

    private int countTriangles(Tile tile) {
        int count = 0;
        if (tile.getVCoordsTri() != null) {
            count += tile.getVCoordsTri().length / 9;
        }
        if (tile.getVCoordsQuad() != null) {
            count += (tile.getVCoordsQuad().length / 12) * 2;
        }
        return count;
    }

    public static class BuildResult {
        public List<RuntimeChunk> chunks = new ArrayList<>();
        public int materialGroupCount;
    }

    public static class ChunkPlacement {
        public long resortTileId;
        public int localIndex;
        public int layer;
        public int materialId;
        public int gridX;
        public int gridY;
        public float worldX;
        public float worldY;
        public float worldZ;
        public int tileWidth;
        public int tileHeight;
        public int vertexCount;
        public int triangleCount;
    }
}
