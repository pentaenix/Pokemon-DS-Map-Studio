package resort.runtimeformat;

import resort.bake.ChunkBuilder;

import java.util.ArrayList;
import java.util.List;

public class RuntimeMaterialGroup {
    public int materialId;
    public List<ChunkBuilder.ChunkPlacement> placements = new ArrayList<>();
}
