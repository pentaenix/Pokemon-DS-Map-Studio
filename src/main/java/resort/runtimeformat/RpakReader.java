package resort.runtimeformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import resort.formats.RtpksArchive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RpakReader {

    private static final Gson GSON = new GsonBuilder().create();

    private RpakReader() {
    }

    public static LoadResult load(Path rpakPath) throws IOException {
        Map<String, byte[]> archive = RtpksArchive.readAll(rpakPath);
        RpakDocument document = GSON.fromJson(
                RtpksArchive.getRequiredText(archive, "manifest.json"),
                RpakDocument.class);
        validateDocument(document);
        return new LoadResult(rpakPath, document, archive);
    }

    private static void validateDocument(RpakDocument document) throws IOException {
        if (document == null || !RpakDocument.FORMAT.equals(document.format)) {
            throw new IOException("Unsupported RPAK manifest format");
        }
        if (document.version != RpakDocument.VERSION) {
            throw new IOException("Unsupported RPAK version: " + document.version);
        }
        if (document.packId == null || document.packId.trim().isEmpty()) {
            throw new IOException("RPAK packId is missing");
        }
        if (document.tiles == null) {
            throw new IOException("RPAK tiles list is missing");
        }
    }

    public static Set<Long> tileIds(RpakDocument document) {
        Set<Long> ids = new HashSet<>();
        for (RuntimeTileTemplate tile : document.tiles) {
            ids.add(tile.resortTileId);
        }
        return ids;
    }

    public static boolean hasMeshEntry(Map<String, byte[]> archive, long resortTileId) {
        return archive.containsKey("meshes/tile_" + resortTileId + ".json");
    }

    public static boolean hasTextureEntry(Map<String, byte[]> archive, String textureName) {
        return archive.containsKey("textures/" + textureName);
    }

    public static final class LoadResult {
        public final Path rpakPath;
        public final RpakDocument document;
        public final Map<String, byte[]> archive;

        public LoadResult(Path rpakPath, RpakDocument document, Map<String, byte[]> archive) {
            this.rpakPath = rpakPath;
            this.document = document;
            this.archive = archive;
        }
    }
}
