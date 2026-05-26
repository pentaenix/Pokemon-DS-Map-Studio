package resort.runtimeformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import resort.formats.RtpksArchive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RbmapWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RbmapWriter() {
    }

    public static void write(Path rbmapPath, RbmapDocument document) throws IOException {
        Map<String, byte[]> archive = new LinkedHashMap<>();
        archive.put("manifest.json", GSON.toJson(document).getBytes(StandardCharsets.UTF_8));
        RtpksArchive.write(rbmapPath, archive);
    }
}
