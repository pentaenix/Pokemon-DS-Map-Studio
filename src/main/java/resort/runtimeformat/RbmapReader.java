package resort.runtimeformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import resort.formats.RtpksArchive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class RbmapReader {

    private static final Gson GSON = new GsonBuilder().create();

    private RbmapReader() {
    }

    public static LoadResult load(Path rbmapPath) throws IOException {
        Map<String, byte[]> archive = RtpksArchive.readAll(rbmapPath);
        RbmapDocument document = GSON.fromJson(
                RtpksArchive.getRequiredText(archive, "manifest.json"),
                RbmapDocument.class);
        validateDocument(document);
        return new LoadResult(rbmapPath, document, archive);
    }

    private static void validateDocument(RbmapDocument document) throws IOException {
        if (document == null || !RbmapDocument.FORMAT.equals(document.format)) {
            throw new IOException("Unsupported RBMAP manifest format");
        }
        if (document.version != RbmapDocument.VERSION) {
            throw new IOException("Unsupported RBMAP version: " + document.version);
        }
        if (document.mapId == null || document.mapId.trim().isEmpty()) {
            throw new IOException("RBMAP mapId is missing");
        }
        if (document.rpakDependency == null || document.rpakDependency.trim().isEmpty()) {
            throw new IOException("RBMAP rpakDependency is missing");
        }
        if (document.bake == null) {
            throw new IOException("RBMAP bake info is missing");
        }
        if (document.chunks == null) {
            throw new IOException("RBMAP chunks list is missing");
        }
    }

    public static final class LoadResult {
        public final Path rbmapPath;
        public final RbmapDocument document;
        public final Map<String, byte[]> archive;

        public LoadResult(Path rbmapPath, RbmapDocument document, Map<String, byte[]> archive) {
            this.rbmapPath = rbmapPath;
            this.document = document;
            this.archive = archive;
        }
    }
}
