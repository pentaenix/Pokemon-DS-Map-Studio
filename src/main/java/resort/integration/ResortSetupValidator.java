package resort.integration;

import editor.grid.MapGrid;
import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import resort.formats.ResortMapMetadataIO;
import resort.formats.TileIndex;
import resort.formats.TileIndexEntry;
import tileset.Tileset;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResortSetupValidator {

    public static ValidationReport validate(MapEditorHandler handler, ResortMapMetadata metadata) {
        ValidationReport report = new ValidationReport();
        Tileset tileset = handler.getTileset();
        if (tileset == null || tileset.size() == 0) {
            report.errors.add("No tileset is loaded.");
            return report;
        }

        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding == null || binding.getTileIndex() == null) {
            report.errors.add("Current tileset is not loaded from RTPKS. Open or save as RTPKS first.");
            return report;
        }

        TileIndex tileIndex = binding.getTileIndex();
        if (tileIndex.entries == null || tileIndex.entries.isEmpty()) {
            report.errors.add("RTPKS tile index is empty.");
        }

        Set<Long> activeIds = new HashSet<>();
        int activeCount = 0;
        for (TileIndexEntry entry : tileIndex.entries) {
            if (!entry.isActive()) {
                continue;
            }
            activeCount++;
            if (!activeIds.add(entry.resortTileId)) {
                report.errors.add("Duplicate active Resort tile ID: " + entry.resortTileId);
            }
            if (entry.localIndex < 0 || entry.localIndex >= tileset.size()) {
                report.errors.add("Tile index localIndex out of range: " + entry.localIndex);
            }
        }

        if (activeCount != tileset.size()) {
            report.errors.add("Active tile index count (" + activeCount
                    + ") does not match tileset size (" + tileset.size() + ").");
        }

        if (metadata == null) {
            report.errors.add("Resort map metadata (.resort.json) is missing.");
            return report;
        }

        if (metadata.tilePackSource == null || metadata.tilePackSource.trim().isEmpty()) {
            report.warnings.add("RTPKS path is not set in resort metadata.");
        } else if (binding.getRtpksPath() != null) {
            Path configured = Paths.get(metadata.tilePackSource).toAbsolutePath().normalize();
            Path active = binding.getRtpksPath().toAbsolutePath().normalize();
            if (!configured.equals(active)) {
                report.warnings.add("Sidecar RTPKS path differs from active tileset RTPKS.");
            }
        }

        validateExportRegion(metadata, report);
        validateMapCells(handler, binding, metadata, report);

        report.valid = report.errors.isEmpty();
        return report;
    }

    private static void validateExportRegion(ResortMapMetadata metadata, ValidationReport report) {
        ResortMapMetadata.ExportRegion region = metadata.bake.exportRegion;
        if (region == null) {
            report.errors.add("Bake export region is missing.");
            return;
        }
        if (region.width <= 0 || region.height <= 0) {
            report.errors.add("Bake export region width/height must be positive.");
        }
        if (region.x < 0 || region.y < 0
                || region.x + region.width > MapGrid.cols
                || region.y + region.height > MapGrid.rows) {
            report.errors.add("Bake export region is outside the 32x32 map grid.");
        }
        if (metadata.bake.output == null
                || metadata.bake.output.assetPack == null
                || metadata.bake.output.mapName == null) {
            report.errors.add("Runtime output paths are not configured.");
        }
    }

    private static void validateMapCells(MapEditorHandler handler,
                                         ResortTilesetBinding binding,
                                         ResortMapMetadata metadata,
                                         ValidationReport report) {
        MapGrid grid = handler.getGrid();
        ResortMapMetadata.ExportRegion region = metadata.bake.exportRegion;
        Set<Integer> localUsed = new HashSet<>();
        Set<Long> resortUsed = new HashSet<>();
        int visibleCells = 0;

        for (int layer = 0; layer < MapGrid.numLayers; layer++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                for (int y = region.y; y < region.y + region.height; y++) {
                    int localIndex = grid.tileLayers[layer][x][y];
                    if (localIndex == -1) {
                        continue;
                    }
                    visibleCells++;
                    localUsed.add(localIndex);
                    long resortId = binding.resolveResortTileId(localIndex);
                    if (resortId < 0) {
                        report.errors.add("Local tile index " + localIndex
                                + " has no Resort tile ID mapping.");
                    } else {
                        resortUsed.add(resortId);
                    }
                }
            }
        }

        report.visibleCellCount = visibleCells;
        report.uniqueLocalTileIndices = localUsed.size();
        report.uniqueResortTileIds = resortUsed.size();
    }

    public static Path resolveSidecarForHandler(MapEditorHandler handler) {
        String mapPath = handler.getMapMatrix().filePath;
        if (mapPath == null || mapPath.isEmpty()) {
            return null;
        }
        return ResortMapMetadataIO.sidecarPathForMap(new File(mapPath).toPath());
    }

    public static class ValidationReport {
        public boolean valid;
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public int visibleCellCount;
        public int uniqueLocalTileIndices;
        public int uniqueResortTileIds;

        public String toDisplayText() {
            StringBuilder sb = new StringBuilder();
            if (valid) {
                sb.append("Validation passed.\n");
            } else {
                sb.append("Validation failed.\n");
            }
            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (String error : errors) {
                    sb.append(" - ").append(error).append('\n');
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : warnings) {
                    sb.append(" - ").append(warning).append('\n');
                }
            }
            sb.append("\nVisible cells: ").append(visibleCellCount);
            sb.append("\nUnique local tile indices: ").append(uniqueLocalTileIndices);
            sb.append("\nUnique Resort tile IDs: ").append(uniqueResortTileIds);
            return sb.toString();
        }
    }
}
