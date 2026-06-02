package resort.runtimeformat;

import resort.formats.ResortMapMetadata;

import java.util.ArrayList;
import java.util.List;

public class RbmapDocument {

    public static final String FORMAT = "pokemon_resort.rbmap";
    public static final int VERSION = 1;

    public String format = FORMAT;
    public int version = VERSION;
    public String mapId;
    public String displayName;
    public String rpakDependency;
    public String defaultCamera;
    public List<ResortMapMetadata.SpawnEntry> spawns = new ArrayList<>();
    public BakeInfo bake = new BakeInfo();
    public List<RuntimeChunk> chunks = new ArrayList<>();
    public CollisionPlaceholder collision = new CollisionPlaceholder();
    /** Full editor bake/config; round-trips with Save as RBMAP (no .resort.json sidecar required). */
    public ResortMapMetadata editorConfig;

    public static class BakeInfo {
        public int[] mapCoordinate = new int[]{0, 0};
        public ResortMapMetadata.ExportRegion exportRegion = new ResortMapMetadata.ExportRegion();
        public int chunkSizeTiles;
        public float heightScale;
    }

    public static class CollisionPlaceholder {
        public String status = "reserved";
    }
}
