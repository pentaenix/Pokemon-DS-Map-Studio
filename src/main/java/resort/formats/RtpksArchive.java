package resort.formats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZIP archive helper for .rtpks, .rpak, and .rbmap containers.
 */
public final class RtpksArchive {

    private RtpksArchive() {
    }

    public static Map<String, byte[]> readAll(Path archivePath) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(archivePath);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeEntryName(entry.getName());
                entries.put(name, readEntryBytes(zip));
            }
        }
        return entries;
    }

    public static void write(Path archivePath, Map<String, byte[]> entries) throws IOException {
        Path parent = archivePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent != null ? parent : archivePath.getParent(), "rtpks-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            Files.move(temp, archivePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static byte[] getRequired(Map<String, byte[]> archive, String path) throws IOException {
        byte[] data = archive.get(normalizeEntryName(path));
        if (data == null) {
            throw new IOException("Missing archive entry: " + path);
        }
        return data;
    }

    public static String getRequiredText(Map<String, byte[]> archive, String path) throws IOException {
        return new String(getRequired(archive, path), StandardCharsets.UTF_8);
    }

    public static String normalizeEntryName(String name) {
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static byte[] readEntryBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
