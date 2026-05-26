package resort.formats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResortMapMetadataIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ResortMapMetadataIO() {
    }

    public static Path sidecarPathForMap(Path mapPath) {
        String fileName = mapPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return mapPath.getParent().resolve(base + ".resort.json");
    }

    public static ResortMapMetadata read(Path sidecarPath) throws IOException {
        if (!Files.exists(sidecarPath)) {
            return null;
        }
        String json = new String(Files.readAllBytes(sidecarPath), StandardCharsets.UTF_8);
        ResortMapMetadata metadata = GSON.fromJson(json, ResortMapMetadata.class);
        if (metadata == null || !ResortMapMetadata.FORMAT.equals(metadata.format)) {
            throw new IOException("Unsupported resort sidecar format: " + sidecarPath);
        }
        return metadata;
    }

    public static void write(Path sidecarPath, ResortMapMetadata metadata) throws IOException {
        metadata.format = ResortMapMetadata.FORMAT;
        metadata.version = ResortMapMetadata.VERSION;
        Path parent = sidecarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(sidecarPath, GSON.toJson(metadata).getBytes(StandardCharsets.UTF_8));
    }
}
