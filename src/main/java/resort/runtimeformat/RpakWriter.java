package resort.runtimeformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import resort.formats.RtpksArchive;
import tileset.Tile;
import tileset.Tileset;
import tileset.TilesetMaterial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RpakWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RpakWriter() {
    }

    public static void write(Path rpakPath, RpakDocument document, Tileset tileset,
                             Map<Integer, Long> localToResort) throws IOException {
        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("manifest.json", GSON.toJson(document).getBytes(StandardCharsets.UTF_8));

        for (int localIndex = 0; localIndex < tileset.size(); localIndex++) {
            Long resortTileId = localToResort.get(localIndex);
            if (resortTileId == null) {
                continue;
            }
            Tile tile = tileset.get(localIndex);
            RuntimeMeshSerializer.MeshPayload mesh = RuntimeMeshSerializer.serializeTile(tile);
            archive.put("meshes/tile_" + resortTileId + ".json",
                    GSON.toJson(mesh).getBytes(StandardCharsets.UTF_8));
        }

        for (RpakDocument.TextureEntry texture : document.textures) {
            for (TilesetMaterial material : tileset.getMaterials()) {
                if (texture.name.equals(material.getImageName()) && material.getTextureImg() != null) {
                    Path temp = Files.createTempFile("rpak-texture-", ".png");
                    try {
                        javax.imageio.ImageIO.write(material.getTextureImg(), "png", temp.toFile());
                        archive.put("textures/" + texture.name, Files.readAllBytes(temp));
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                    break;
                }
            }
        }

        RtpksArchive.write(rpakPath, archive);
    }
}
