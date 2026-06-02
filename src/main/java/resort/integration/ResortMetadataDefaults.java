package resort.integration;

import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import utils.Utils;

import java.io.File;

/**
 * In-memory resort metadata when no .resort.json sidecar exists (unsaved PDMS project).
 */
public final class ResortMetadataDefaults {

    private ResortMetadataDefaults() {
    }

    public static ResortMapMetadata forHandler(MapEditorHandler handler, String suggestedBaseName) {
        ResortMapMetadata metadata = handler.getResortMapMetadata();
        if (metadata != null) {
            return copy(metadata);
        }

        String base = suggestedBaseName;
        if (base == null || base.isEmpty()) {
            base = "untitled_map";
        }

        metadata = new ResortMapMetadata();
        metadata.mapId = base;
        metadata.displayName = base;
        metadata.bake.exportRegion.map[0] = handler.getMapSelected().x;
        metadata.bake.exportRegion.map[1] = handler.getMapSelected().y;
        metadata.bake.output.mapName = base + ".rbmap";
        metadata.bake.output.assetPack = base + ".rpak";
        metadata.bake.output.runtimeRoot = ".";

        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding != null && binding.getRtpksPath() != null) {
            metadata.tilePackSource = binding.getRtpksPath().toString();
        }

        ResortMapMetadata.SpawnEntry spawn = new ResortMapMetadata.SpawnEntry();
        spawn.map[0] = handler.getMapSelected().x;
        spawn.map[1] = handler.getMapSelected().y;
        spawn.tile[0] = 5;
        spawn.tile[1] = 5;
        metadata.spawns.clear();
        metadata.spawns.add(spawn);
        return metadata;
    }

    public static ResortMapMetadata fromMapFilePath(MapEditorHandler handler) {
        String mapPath = handler.getMapMatrix().filePath;
        if (mapPath != null && !mapPath.isEmpty()) {
            String base = Utils.removeExtensionFromPath(new File(mapPath).getName());
            return forHandler(handler, base);
        }
        return forHandler(handler, "untitled_map");
    }

    public static ResortMapMetadata copy(ResortMapMetadata source) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJson(source), ResortMapMetadata.class);
    }

    /** Align output paths with a user-chosen RBMAP file (RPAK sibling in the same folder). */
    public static void applyRbmapOutputPaths(ResortMapMetadata metadata, java.nio.file.Path rbmapPath) {
        java.nio.file.Path parent = rbmapPath.getParent();
        String rbmapFile = rbmapPath.getFileName().toString();
        String base = Utils.removeExtensionFromPath(rbmapFile);
        String rpakFile = base + ".rpak";

        metadata.bake.output.mapName = rbmapFile;
        metadata.bake.output.assetPack = rpakFile;
        metadata.bake.output.runtimeRoot = parent != null ? parent.toString() : ".";

        if (metadata.mapId == null || metadata.mapId.isEmpty()) {
            metadata.mapId = base;
        }
        if (metadata.displayName == null || metadata.displayName.isEmpty()) {
            metadata.displayName = base;
        }
    }

    public static java.nio.file.Path siblingRpakPath(java.nio.file.Path rbmapPath) {
        String base = Utils.removeExtensionFromPath(rbmapPath.getFileName().toString());
        java.nio.file.Path parent = rbmapPath.getParent();
        if (parent == null) {
            return java.nio.file.Paths.get(base + ".rpak");
        }
        return parent.resolve(base + ".rpak");
    }
}
