package resort.formats;

import java.util.ArrayList;
import java.util.List;

public class ResortMapMetadata {

    public static final String FORMAT = "pokemon_resort.map_binding";
    public static final int VERSION = 1;

    public String format = FORMAT;
    public int version = VERSION;
    public String mapId;
    public String displayName;
    public String tilePackSource;
    public String defaultCamera = "gen4_platinum_default_exterior";
    public List<SpawnEntry> spawns = new ArrayList<>();
    public BakeSettings bake = new BakeSettings();

    public static class SpawnEntry {
        public String id = "player_start";
        public int[] map = new int[]{0, 0};
        public int[] tile = new int[]{0, 0};
        public int layer = 0;
        public String facing = "south";
    }

    public static class BakeSettings {
        public ExportRegion exportRegion = new ExportRegion();
        public int chunkSizeTiles = 8;
        public float heightScale = 16.0f;
        public OutputPaths output = new OutputPaths();
    }

    public static class ExportRegion {
        public int[] map = new int[]{0, 0};
        public int x = 0;
        public int y = 0;
        public int width = 32;
        public int height = 32;
    }

    public static class OutputPaths {
        public String runtimeRoot = "resort_runtime";
        public String assetPack = "packs/gen4_base.rpak";
        public String mapName = "maps/map.rbmap";
    }
}
