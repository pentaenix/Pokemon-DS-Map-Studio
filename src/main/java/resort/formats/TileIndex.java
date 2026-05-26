package resort.formats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TileIndex {

    public static final String FORMAT = "pokemon_resort.tile_index";
    public static final int VERSION = 1;

    public String format = FORMAT;
    public int version = VERSION;
    public String packId;
    public List<TileIndexEntry> entries = new ArrayList<>();

    public Map<Integer, TileIndexEntry> activeByLocalIndex() {
        Map<Integer, TileIndexEntry> map = new HashMap<>();
        for (TileIndexEntry entry : entries) {
            if (entry.isActive()) {
                map.put(entry.localIndex, entry);
            }
        }
        return map;
    }

    public long resolveResortTileId(int localIndex) {
        for (TileIndexEntry entry : entries) {
            if (entry.isActive() && entry.localIndex == localIndex) {
                return entry.resortTileId;
            }
        }
        return -1L;
    }

    public Set<Long> activeResortTileIds() {
        Set<Long> ids = new HashSet<>();
        for (TileIndexEntry entry : entries) {
            if (entry.isActive()) {
                ids.add(entry.resortTileId);
            }
        }
        return ids;
    }

    public long maxActiveResortTileId() {
        long max = -1L;
        for (TileIndexEntry entry : entries) {
            if (entry.isActive()) {
                max = Math.max(max, entry.resortTileId);
            }
        }
        return max;
    }
}
