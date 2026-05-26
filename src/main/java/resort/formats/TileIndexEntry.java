package resort.formats;

public class TileIndexEntry {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    public int localIndex;
    public long resortTileId;
    public String key;
    public String status = STATUS_ACTIVE;

    public TileIndexEntry() {
    }

    public TileIndexEntry(int localIndex, long resortTileId, String key, String status) {
        this.localIndex = localIndex;
        this.resortTileId = resortTileId;
        this.key = key;
        this.status = status;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
