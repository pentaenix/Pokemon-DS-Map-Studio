package resort.formats;

import tileset.Tile;
import tileset.Tileset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Appends tiles from one tileset onto an existing RTPKS pack.
 */
public final class RtpksAppender {

    private RtpksAppender() {
    }

    public static AppendResult append(Path rtpksPath, Tileset tilesToAppend, byte[] thumbnailBytes)
            throws IOException {
        if (tilesToAppend == null || tilesToAppend.size() == 0) {
            throw new IOException("No tiles to append.");
        }

        Map<String, byte[]> archive = RtpksArchive.readAll(rtpksPath);
        byte[] existingThumbnail = archive.get(
                RtpksArchive.normalizeEntryName(new RtpksManifest().editorPayload.thumbnail));

        try (RtpksReader.LoadResult loaded = RtpksReader.load(rtpksPath)) {
            Tileset merged = loaded.tileset;
            int priorCount = merged.size();

            ArrayList<Tile> copies = new ArrayList<>(tilesToAppend.size());
            for (int i = 0; i < tilesToAppend.size(); i++) {
                copies.add(tilesToAppend.get(i).clone());
            }
            merged.importTiles(copies);
            merged.removeUnusedTextures();

            byte[] thumb = thumbnailBytes != null && thumbnailBytes.length > 0
                    ? thumbnailBytes
                    : existingThumbnail;

            RtpksWriter.WriteRequest request = new RtpksWriter.WriteRequest(
                    merged,
                    loaded.manifest.packId,
                    loaded.manifest.name,
                    loaded.tileIndex,
                    thumb);
            RtpksWriter.write(rtpksPath, request);

            List<AppendedTileMapping> appended = new ArrayList<>();
            for (int localIndex = priorCount; localIndex < merged.size(); localIndex++) {
                long resortTileId = request.writtenTileIndex.resolveResortTileId(localIndex);
                appended.add(new AppendedTileMapping(localIndex, resortTileId));
            }

            return new AppendResult(
                    rtpksPath,
                    loaded.manifest,
                    request.writtenTileIndex,
                    merged.size(),
                    priorCount,
                    appended);
        }
    }

    public static final class AppendResult {
        public final Path rtpksPath;
        public final RtpksManifest manifest;
        public final TileIndex tileIndex;
        public final int mergedTileCount;
        public final int priorTileCount;
        public final List<AppendedTileMapping> appendedTiles;

        public AppendResult(Path rtpksPath, RtpksManifest manifest, TileIndex tileIndex,
                            int mergedTileCount, int priorTileCount,
                            List<AppendedTileMapping> appendedTiles) {
            this.rtpksPath = rtpksPath;
            this.manifest = manifest;
            this.tileIndex = tileIndex;
            this.mergedTileCount = mergedTileCount;
            this.priorTileCount = priorTileCount;
            this.appendedTiles = appendedTiles;
        }

        public int appendedCount() {
            return appendedTiles.size();
        }
    }

    public static final class AppendedTileMapping {
        public final int localIndex;
        public final long resortTileId;

        public AppendedTileMapping(int localIndex, long resortTileId) {
            this.localIndex = localIndex;
            this.resortTileId = resortTileId;
        }
    }
}
