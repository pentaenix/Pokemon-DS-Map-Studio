package resort.formats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds or updates tile_index.json while preserving stable Resort tile IDs.
 */
public final class TileIndexBuilder {

    private TileIndexBuilder() {
    }

    public static TileIndex buildOrUpdate(String packId, int tileCount, TileIndex existing) {
        TileIndex index = new TileIndex();
        index.packId = packId;
        index.entries = new ArrayList<>();

        Map<Integer, TileIndexEntry> existingActive = existing != null
                ? existing.activeByLocalIndex()
                : new HashMap<>();
        Set<Long> usedIds = existing != null
                ? existing.activeResortTileIds()
                : new HashSet<>();
        long nextId = existing != null ? existing.maxActiveResortTileId() + 1 : 0L;
        if (nextId < 0) {
            nextId = 0L;
        }

        if (existing != null) {
            for (TileIndexEntry entry : existing.entries) {
                if (!entry.isActive()) {
                    index.entries.add(copy(entry));
                } else if (entry.localIndex >= tileCount) {
                    TileIndexEntry tombstone = copy(entry);
                    tombstone.status = TileIndexEntry.STATUS_INACTIVE;
                    index.entries.add(tombstone);
                }
            }
        }

        for (int localIndex = 0; localIndex < tileCount; localIndex++) {
            TileIndexEntry prior = existingActive.get(localIndex);
            if (prior != null) {
                index.entries.add(copy(prior));
                continue;
            }
            while (usedIds.contains(nextId)) {
                nextId++;
            }
            index.entries.add(new TileIndexEntry(
                    localIndex,
                    nextId,
                    String.format("tile_%04d", localIndex),
                    TileIndexEntry.STATUS_ACTIVE));
            usedIds.add(nextId);
            nextId++;
        }

        index.entries.sort((a, b) -> {
            if (a.localIndex != b.localIndex) {
                return Integer.compare(a.localIndex, b.localIndex);
            }
            return Long.compare(a.resortTileId, b.resortTileId);
        });
        return index;
    }

    private static TileIndexEntry copy(TileIndexEntry source) {
        return new TileIndexEntry(source.localIndex, source.resortTileId, source.key, source.status);
    }
}
