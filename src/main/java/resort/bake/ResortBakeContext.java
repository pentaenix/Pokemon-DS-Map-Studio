package resort.bake;

import editor.grid.MapGrid;
import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import resort.integration.ResortTilesetBinding;

import java.nio.file.Path;

public class ResortBakeContext {

    public final MapEditorHandler handler;
    public final ResortTilesetBinding binding;
    public final ResortMapMetadata metadata;
    public final Path sourceRtpksPath;
    public final Path sourceMapPath;
    public final Path runtimeRoot;
    public final Path rpakOutputPath;
    public final Path rbmapOutputPath;
    public final ResortMapMetadata.ExportRegion exportRegion;
    public final int chunkSizeTiles;
    public final float heightScale;

    public ResortBakeContext(MapEditorHandler handler,
                             ResortTilesetBinding binding,
                             ResortMapMetadata metadata,
                             Path runtimeRoot,
                             Path rpakOutputPath,
                             Path rbmapOutputPath) {
        this.handler = handler;
        this.binding = binding;
        this.metadata = metadata;
        this.sourceRtpksPath = binding.getRtpksPath();
        this.sourceMapPath = handler.getMapMatrix().filePath.isEmpty()
                ? null
                : java.nio.file.Paths.get(handler.getMapMatrix().filePath);
        this.runtimeRoot = runtimeRoot;
        this.rpakOutputPath = rpakOutputPath;
        this.rbmapOutputPath = rbmapOutputPath;
        this.exportRegion = metadata.bake.exportRegion;
        this.chunkSizeTiles = Math.max(1, metadata.bake.chunkSizeTiles);
        this.heightScale = metadata.bake.heightScale;
    }

    public MapGrid grid() {
        return handler.getGrid();
    }
}
