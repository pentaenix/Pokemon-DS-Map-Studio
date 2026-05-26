package resort.formats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tileset.Tileset;
import tileset.TilesetIO;
import tileset.TilesetMaterial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RtpksWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RtpksWriter() {
    }

    public static void write(Path rtpksPath, WriteRequest request) throws IOException {
        Path tempRoot = Files.createTempDirectory("pdsms-rtpks-write-");
        try {
            Path pdsmsDir = tempRoot.resolve("pdsms");
            Files.createDirectories(pdsmsDir);

            Path pdstsPath = pdsmsDir.resolve("tileset.pdsts");
            TilesetIO.saveTilesetToFile(pdstsPath.toString(), request.tileset);
            request.tileset.saveImagesToFile(pdsmsDir.toString());

            if (request.thumbnailBytes != null && request.thumbnailBytes.length > 0) {
                Files.write(pdsmsDir.resolve("TilesetThumbnail.png"), request.thumbnailBytes);
            }

            TileIndex tileIndex = TileIndexBuilder.buildOrUpdate(
                    request.packId,
                    request.tileset.size(),
                    request.existingTileIndex);
            tileIndex.packId = request.packId;

            RtpksManifest manifest = new RtpksManifest();
            manifest.packId = request.packId;
            manifest.name = request.displayName;
            manifest.tileIndex = "index/tile_index.json";

            Map<String, byte[]> archive = new LinkedHashMap<>();
            archive.put("manifest.json", GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            archive.put("index/tile_index.json", GSON.toJson(tileIndex).getBytes(StandardCharsets.UTF_8));
            archive.put("pdsms/tileset.pdsts", Files.readAllBytes(pdstsPath));

            for (TilesetMaterial material : request.tileset.getMaterials()) {
                Path texturePath = pdsmsDir.resolve(material.getImageName());
                if (Files.exists(texturePath)) {
                    archive.put("pdsms/textures/" + material.getImageName(),
                            Files.readAllBytes(texturePath));
                }
            }

            Path thumbPath = pdsmsDir.resolve("TilesetThumbnail.png");
            if (Files.exists(thumbPath)) {
                archive.put("pdsms/TilesetThumbnail.png", Files.readAllBytes(thumbPath));
            }

            RtpksArchive.write(rtpksPath, archive);
            request.writtenTileIndex = tileIndex;
        } finally {
            deleteDirectory(tempRoot);
        }
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
    }

    public static class WriteRequest {
        public final Tileset tileset;
        public final String packId;
        public final String displayName;
        public final TileIndex existingTileIndex;
        public final byte[] thumbnailBytes;
        public TileIndex writtenTileIndex;

        public WriteRequest(Tileset tileset, String packId, String displayName,
                            TileIndex existingTileIndex, byte[] thumbnailBytes) {
            this.tileset = tileset;
            this.packId = packId;
            this.displayName = displayName;
            this.existingTileIndex = existingTileIndex;
            this.thumbnailBytes = thumbnailBytes;
        }
    }
}
