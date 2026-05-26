package resort.bake;

import editor.grid.MapGrid;
import resort.formats.ResortMapMetadata;
import resort.integration.ResortTilesetBinding;
import tileset.Tile;
import tileset.Tileset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PdsmsMapExtractor {

    public static ExtractResult extract(MapGrid grid,
                                        Tileset tileset,
                                        ResortTilesetBinding binding,
                                        ResortMapMetadata.ExportRegion region) {
        List<PlacedTile> placedTiles = new ArrayList<>();
        Set<Integer> localUsed = new HashSet<>();
        Set<Long> resortUsed = new HashSet<>();

        for (int layer = 0; layer < MapGrid.numLayers; layer++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                for (int y = region.y; y < region.y + region.height; y++) {
                    int localIndex = grid.tileLayers[layer][x][y];
                    if (localIndex < 0) {
                        continue;
                    }
                    long resortTileId = TileResolver.resolveOrThrow(binding, localIndex);
                    Tile tile = tileset.get(localIndex);
                    placedTiles.add(new PlacedTile(
                            x, y, layer, grid.heightLayers[layer][x][y],
                            localIndex, resortTileId, tile));
                    localUsed.add(localIndex);
                    resortUsed.add(resortTileId);
                }
            }
        }

        ExtractResult result = new ExtractResult();
        result.placedTiles = placedTiles;
        result.visibleCellCount = placedTiles.size();
        result.uniqueLocalTileIndices = localUsed.size();
        result.uniqueResortTileIds = resortUsed.size();
        return result;
    }

    public static class PlacedTile {
        public final int gridX;
        public final int gridY;
        public final int layer;
        public final int height;
        public final int localIndex;
        public final long resortTileId;
        public final Tile tile;

        public PlacedTile(int gridX, int gridY, int layer, int height,
                          int localIndex, long resortTileId, Tile tile) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.layer = layer;
            this.height = height;
            this.localIndex = localIndex;
            this.resortTileId = resortTileId;
            this.tile = tile;
        }
    }

    public static class ExtractResult {
        public List<PlacedTile> placedTiles = new ArrayList<>();
        public int visibleCellCount;
        public int uniqueLocalTileIndices;
        public int uniqueResortTileIds;
    }
}
