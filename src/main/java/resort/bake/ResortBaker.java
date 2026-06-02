package resort.bake;

import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import resort.formats.TileIndexEntry;
import resort.integration.ResortTilesetBinding;
import resort.runtimeformat.RbmapDocument;
import resort.runtimeformat.RpakDocument;
import resort.runtimeformat.RbmapWriter;
import resort.runtimeformat.RpakWriter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ResortBaker {

    private final MapEditorHandler handler;
    private final ChunkBuilder chunkBuilder = new ChunkBuilder();

    public ResortBaker(MapEditorHandler handler) {
        this.handler = handler;
    }

    public ResortBakeReport bakeSelectedMap(ResortMapMetadata metadata) throws Exception {
        Path runtimeRoot = resolveRuntimeRoot(metadata, handler);
        Path rpakOutput = runtimeRoot.resolve(metadata.bake.output.assetPack).normalize();
        Path rbmapOutput = runtimeRoot.resolve(metadata.bake.output.mapName).normalize();
        return bakeToPaths(metadata, rbmapOutput, rpakOutput);
    }

    public ResortBakeReport bakeToPaths(ResortMapMetadata metadata, Path rbmapOutput, Path rpakOutput) throws Exception {
        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding == null) {
            throw new IllegalStateException("Current tileset is not loaded from RTPKS.");
        }

        Files.createDirectories(rbmapOutput.getParent());
        Files.createDirectories(rpakOutput.getParent());

        Path runtimeRoot = rbmapOutput.getParent() != null
                ? rbmapOutput.getParent()
                : Paths.get(".").toAbsolutePath().normalize();

        ResortBakeContext context = new ResortBakeContext(
                handler, binding, metadata, runtimeRoot, rpakOutput, rbmapOutput);

        PdsmsMapExtractor.ExtractResult extractResult = PdsmsMapExtractor.extract(
                context.grid(),
                handler.getTileset(),
                binding,
                context.exportRegion);

        ChunkBuilder.BuildResult chunkResult = chunkBuilder.build(
                extractResult,
                context.exportRegion,
                context.chunkSizeTiles,
                context.heightScale);

        Map<Integer, Long> localToResort = buildLocalToResortMap(binding);
        String packId = binding.getManifest() != null
                ? binding.getManifest().packId
                : binding.getRtpksPath().getFileName().toString();
        RpakDocument rpakDocument = RpakDocument.fromTileset(
                packId,
                binding.getRtpksPath().toString(),
                handler.getTileset(),
                localToResort);
        RpakWriter.write(rpakOutput, rpakDocument, handler.getTileset(), localToResort);

        RbmapDocument rbmapDocument = new RbmapDocument();
        rbmapDocument.mapId = metadata.mapId;
        rbmapDocument.displayName = metadata.displayName;
        rbmapDocument.rpakDependency = metadata.bake.output.assetPack;
        rbmapDocument.defaultCamera = metadata.defaultCamera;
        rbmapDocument.spawns = metadata.spawns;
        rbmapDocument.bake.mapCoordinate[0] = handler.getMapSelected().x;
        rbmapDocument.bake.mapCoordinate[1] = handler.getMapSelected().y;
        rbmapDocument.bake.exportRegion = metadata.bake.exportRegion;
        rbmapDocument.bake.chunkSizeTiles = context.chunkSizeTiles;
        rbmapDocument.bake.heightScale = context.heightScale;
        rbmapDocument.chunks = chunkResult.chunks;
        rbmapDocument.editorConfig = metadata;
        rbmapDocument.rpakDependency = rpakOutput.getFileName() != null
                ? rpakOutput.getFileName().toString()
                : metadata.bake.output.assetPack;
        RbmapWriter.write(rbmapOutput, rbmapDocument);

        ResortBakeReport report = new ResortBakeReport();
        report.sourceRtpksPath = binding.getRtpksPath().toString();
        report.sourceMapPath = handler.getMapMatrix().filePath;
        report.selectedMapCoordinate = handler.getMapSelected().x + "," + handler.getMapSelected().y;
        report.exportRegion = formatRegion(metadata.bake.exportRegion);
        report.visibleCellCount = extractResult.visibleCellCount;
        report.uniqueLocalTileIndices = extractResult.uniqueLocalTileIndices;
        report.uniqueResortTileIds = extractResult.uniqueResortTileIds;
        report.chunkCount = chunkResult.chunks.size();
        report.materialGroupCount = countMaterialGroups(chunkResult);
        report.outputRpakPath = rpakOutput.toString();
        report.outputRbmapPath = rbmapOutput.toString();
        return report;
    }

    private static Map<Integer, Long> buildLocalToResortMap(ResortTilesetBinding binding) {
        Map<Integer, Long> map = new HashMap<>();
        for (TileIndexEntry entry : binding.getTileIndex().entries) {
            if (entry.isActive()) {
                map.put(entry.localIndex, entry.resortTileId);
            }
        }
        return map;
    }

    private static Path resolveRuntimeRoot(ResortMapMetadata metadata, MapEditorHandler handler) {
        Path runtimeRoot = Paths.get(metadata.bake.output.runtimeRoot);
        if (!runtimeRoot.isAbsolute()) {
            String mapFilePath = handler.getMapMatrix().filePath;
            if (mapFilePath != null && !mapFilePath.isEmpty()) {
                Path mapParent = Paths.get(mapFilePath).getParent();
                if (mapParent != null) {
                    runtimeRoot = mapParent.resolve(runtimeRoot);
                }
            }
        }
        return runtimeRoot.toAbsolutePath().normalize();
    }

    private static int countMaterialGroups(ChunkBuilder.BuildResult chunkResult) {
        int count = 0;
        for (resort.runtimeformat.RuntimeChunk chunk : chunkResult.chunks) {
            count += chunk.materialGroups.size();
        }
        return count;
    }

    private static String formatRegion(ResortMapMetadata.ExportRegion region) {
        return region.x + "," + region.y + " "
                + region.width + "x" + region.height
                + " @ map " + region.map[0] + "," + region.map[1];
    }
}
