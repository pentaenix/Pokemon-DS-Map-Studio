package resort.formats;

import com.google.gson.annotations.SerializedName;

public class RtpksManifest {

    public static final String FORMAT = "pokemon_resort.rtpks";
    public static final int VERSION = 1;

    public String format = FORMAT;
    public int version = VERSION;
    public String packId;
    public String name;
    @SerializedName("editorPayload")
    public EditorPayload editorPayload = new EditorPayload();
    @SerializedName("tileIndex")
    public String tileIndex = "index/tile_index.json";

    public static class EditorPayload {
        public String type = "pdsms_tileset";
        public String tileset = "pdsms/tileset.pdsts";
        @SerializedName("textureDirectory")
        public String textureDirectory = "pdsms/textures";
        public String thumbnail = "pdsms/TilesetThumbnail.png";
    }
}
