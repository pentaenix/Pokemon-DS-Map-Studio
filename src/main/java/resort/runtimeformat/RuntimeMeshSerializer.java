package resort.runtimeformat;

import tileset.Tile;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeMeshSerializer {

    private RuntimeMeshSerializer() {
    }

    public static MeshPayload serializeTile(Tile tile) {
        MeshPayload payload = new MeshPayload();
        payload.width = tile.getWidth();
        payload.height = tile.getHeight();
        payload.xOffset = tile.getXOffset();
        payload.yOffset = tile.getYOffset();
        payload.triangles = copy(tile.getVCoordsTri());
        payload.quads = copy(tile.getVCoordsQuad());
        payload.texCoordsTri = copy(tile.getTCoordsTri());
        payload.texCoordsQuad = copy(tile.getTCoordsQuad());
        payload.normalCoordsTri = copy(tile.getNCoordsTri());
        payload.normalCoordsQuad = copy(tile.getNCoordsQuad());
        payload.textureIds = new ArrayList<>(tile.getTextureIDs());
        return payload;
    }

    private static float[] copy(float[] source) {
        if (source == null) {
            return new float[0];
        }
        float[] copy = new float[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    public static class MeshPayload {
        public int width;
        public int height;
        public float xOffset;
        public float yOffset;
        public float[] triangles = new float[0];
        public float[] quads = new float[0];
        public float[] texCoordsTri = new float[0];
        public float[] texCoordsQuad = new float[0];
        public float[] normalCoordsTri = new float[0];
        public float[] normalCoordsQuad = new float[0];
        public List<Integer> textureIds = new ArrayList<>();
    }
}
