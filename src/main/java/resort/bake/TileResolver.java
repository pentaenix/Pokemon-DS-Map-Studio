package resort.bake;

import resort.integration.ResortTilesetBinding;

public final class TileResolver {

    private TileResolver() {
    }

    public static long resolveOrThrow(ResortTilesetBinding binding, int localIndex) {
        if (localIndex < 0) {
            return -1L;
        }
        long resortTileId = binding.resolveResortTileId(localIndex);
        if (resortTileId < 0) {
            throw new IllegalStateException("Missing Resort tile ID for local index " + localIndex);
        }
        return resortTileId;
    }
}
