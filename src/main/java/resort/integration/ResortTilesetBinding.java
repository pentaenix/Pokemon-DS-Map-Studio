package resort.integration;

import resort.formats.RtpksManifest;
import resort.formats.TileIndex;

import java.nio.file.Path;

/**
 * Tracks the active RTPKS source pack and stable Resort tile ID bindings.
 */
public class ResortTilesetBinding {

    private Path rtpksPath;
    private RtpksManifest manifest;
    private TileIndex tileIndex;
    private boolean dirty;

    public ResortTilesetBinding(Path rtpksPath, RtpksManifest manifest, TileIndex tileIndex) {
        this.rtpksPath = rtpksPath;
        this.manifest = manifest;
        this.tileIndex = tileIndex;
        this.dirty = false;
    }

    public Path getRtpksPath() {
        return rtpksPath;
    }

    public RtpksManifest getManifest() {
        return manifest;
    }

    public TileIndex getTileIndex() {
        return tileIndex;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void updateAfterSave(Path rtpksPath, TileIndex tileIndex) {
        this.rtpksPath = rtpksPath;
        this.tileIndex = tileIndex;
        this.dirty = false;
    }

    public long resolveResortTileId(int localIndex) {
        return tileIndex.resolveResortTileId(localIndex);
    }

    public String getDisplayName() {
        if (manifest != null && manifest.name != null && !manifest.name.isEmpty()) {
            return manifest.name;
        }
        if (rtpksPath != null) {
            return rtpksPath.getFileName().toString();
        }
        return "RTPKS";
    }
}
