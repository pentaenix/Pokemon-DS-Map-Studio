package resort.runtimeformat;

import java.util.ArrayList;
import java.util.List;

public class RuntimeChunk {
    public int chunkX;
    public int chunkY;
    public int minTileX;
    public int minTileY;
    public int maxTileX;
    public int maxTileY;
    public List<RuntimeMaterialGroup> materialGroups = new ArrayList<>();
}
