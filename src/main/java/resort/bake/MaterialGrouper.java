package resort.bake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaterialGrouper {

    public static Map<Integer, List<ChunkBuilder.ChunkPlacement>> groupByMaterial(
            List<ChunkBuilder.ChunkPlacement> placements) {
        Map<Integer, List<ChunkBuilder.ChunkPlacement>> groups = new HashMap<>();
        for (ChunkBuilder.ChunkPlacement placement : placements) {
            groups.computeIfAbsent(placement.materialId, id -> new ArrayList<>()).add(placement);
        }
        return groups;
    }
}
