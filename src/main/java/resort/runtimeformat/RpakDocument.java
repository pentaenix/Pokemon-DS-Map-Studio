package resort.runtimeformat;

import tileset.Tile;
import tileset.Tileset;
import tileset.TilesetMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpakDocument {

    public static final String FORMAT = "pokemon_resort.rpak";
    public static final int VERSION = 1;

    public String format = FORMAT;
    public int version = VERSION;
    public String packId;
    public String sourceRtpks;
    public List<RuntimeTileTemplate> tiles = new ArrayList<>();
    public List<MaterialEntry> materials = new ArrayList<>();
    public List<TextureEntry> textures = new ArrayList<>();

    public static RpakDocument fromTileset(String packId, String sourceRtpks,
                                           Tileset tileset,
                                           Map<Integer, Long> localToResort) {
        RpakDocument document = new RpakDocument();
        document.packId = packId;
        document.sourceRtpks = sourceRtpks;

        Map<Integer, MaterialEntry> materialMap = new HashMap<>();
        Map<String, TextureEntry> textureMap = new HashMap<>();

        for (int localIndex = 0; localIndex < tileset.size(); localIndex++) {
            Long resortTileId = localToResort.get(localIndex);
            if (resortTileId == null) {
                continue;
            }
            Tile tile = tileset.get(localIndex);
            RuntimeTileTemplate template = new RuntimeTileTemplate();
            template.resortTileId = resortTileId;
            template.localIndex = localIndex;
            template.width = tile.getWidth();
            template.height = tile.getHeight();
            template.materialCount = tile.getTextureIDs().size();
            template.vertexCount = countVertices(tile);
            template.triangleCount = countTriangles(tile);
            document.tiles.add(template);

            for (Integer materialId : tile.getTextureIDs()) {
                registerMaterial(tileset, materialId, materialMap, textureMap);
            }
        }

        document.materials.addAll(materialMap.values());
        document.textures.addAll(textureMap.values());
        return document;
    }

    private static void registerMaterial(Tileset tileset,
                                         int materialId,
                                         Map<Integer, MaterialEntry> materialMap,
                                         Map<String, TextureEntry> textureMap) {
        if (materialMap.containsKey(materialId)) {
            return;
        }
        TilesetMaterial material = tileset.getMaterial(materialId);
        MaterialEntry entry = new MaterialEntry();
        entry.materialId = materialId;
        entry.name = material.getMaterialName();
        entry.textureName = material.getImageName();
        entry.alpha = material.getAlpha();
        materialMap.put(materialId, entry);

        if (!textureMap.containsKey(material.getImageName())) {
            TextureEntry texture = new TextureEntry();
            texture.name = material.getImageName();
            textureMap.put(texture.name, texture);
        }
    }

    private static int countVertices(Tile tile) {
        int count = 0;
        if (tile.getVCoordsTri() != null) {
            count += tile.getVCoordsTri().length / 3;
        }
        if (tile.getVCoordsQuad() != null) {
            count += tile.getVCoordsQuad().length / 3;
        }
        return count;
    }

    private static int countTriangles(Tile tile) {
        int count = 0;
        if (tile.getVCoordsTri() != null) {
            count += tile.getVCoordsTri().length / 9;
        }
        if (tile.getVCoordsQuad() != null) {
            count += (tile.getVCoordsQuad().length / 12) * 2;
        }
        return count;
    }

    public static class MaterialEntry {
        public int materialId;
        public String name;
        public String textureName;
        public int alpha;
    }

    public static class TextureEntry {
        public String name;
    }
}
