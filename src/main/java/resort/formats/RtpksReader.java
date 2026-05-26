package resort.formats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tileset.Tileset;
import tileset.TilesetIO;
import tileset.TextureNotFoundException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RtpksReader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RtpksReader() {
    }

    public static LoadResult load(Path rtpksPath) throws IOException {
        Map<String, byte[]> archive = RtpksArchive.readAll(rtpksPath);
        RtpksManifest manifest = GSON.fromJson(
                RtpksArchive.getRequiredText(archive, "manifest.json"),
                RtpksManifest.class);
        validateManifest(manifest);

        TileIndex tileIndex = GSON.fromJson(
                RtpksArchive.getRequiredText(archive, manifest.tileIndex),
                TileIndex.class);
        validateTileIndex(tileIndex);

        Path tempRoot = Files.createTempDirectory("pdsms-rtpks-");
        Path pdsmsDir = tempRoot.resolve("pdsms");
        Files.createDirectories(pdsmsDir);

        byte[] pdstsBytes = RtpksArchive.getRequired(archive, manifest.editorPayload.tileset);
        Path pdstsPath = pdsmsDir.resolve("tileset.pdsts");
        Files.write(pdstsPath, pdstsBytes);

        for (Map.Entry<String, byte[]> entry : archive.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(manifest.editorPayload.textureDirectory + "/")
                    && !name.endsWith("/")) {
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                // PDSMS TilesetIO expects textures beside tileset.pdsts.
                Files.write(pdsmsDir.resolve(fileName), entry.getValue());
            }
        }

        String thumbPath = manifest.editorPayload.thumbnail;
        if (archive.containsKey(thumbPath)) {
            Files.write(pdsmsDir.resolve("TilesetThumbnail.png"), archive.get(thumbPath));
        }

        Tileset tileset;
        try {
            tileset = TilesetIO.readTilesetFromFile(pdstsPath.toString());
        } catch (TextureNotFoundException ex) {
            throw new IOException("Missing texture while loading embedded tileset: " + ex.getMessage(), ex);
        }
        tileset.tilesetFolderPath = pdsmsDir.toString();

        return new LoadResult(rtpksPath, manifest, tileIndex, tileset, tempRoot);
    }

    private static void validateManifest(RtpksManifest manifest) throws IOException {
        if (manifest == null || !RtpksManifest.FORMAT.equals(manifest.format)) {
            throw new IOException("Unsupported RTPKS manifest format");
        }
        if (manifest.version != RtpksManifest.VERSION) {
            throw new IOException("Unsupported RTPKS version: " + manifest.version);
        }
        if (manifest.editorPayload == null
                || !"pdsms_tileset".equals(manifest.editorPayload.type)) {
            throw new IOException("RTPKS editor payload must be pdsms_tileset");
        }
    }

    private static void validateTileIndex(TileIndex tileIndex) throws IOException {
        if (tileIndex == null || !TileIndex.FORMAT.equals(tileIndex.format)) {
            throw new IOException("Unsupported tile index format");
        }
    }

    public static final class LoadResult implements AutoCloseable {
        public final Path rtpksPath;
        public final RtpksManifest manifest;
        public final TileIndex tileIndex;
        public final Tileset tileset;
        private final Path tempRoot;

        public LoadResult(Path rtpksPath, RtpksManifest manifest, TileIndex tileIndex,
                          Tileset tileset, Path tempRoot) {
            this.rtpksPath = rtpksPath;
            this.manifest = manifest;
            this.tileIndex = tileIndex;
            this.tileset = tileset;
            this.tempRoot = tempRoot;
        }

        @Override
        public void close() {
            deleteQuietly(tempRoot);
        }

        private static void deleteQuietly(Path root) {
            if (root == null || !Files.exists(root)) {
                return;
            }
            try {
                Files.walk(root)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException ignored) {
            }
        }
    }
}
