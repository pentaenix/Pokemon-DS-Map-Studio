package utils;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.filechooser.FileFilter;

public class DirectoryFriendlyExtensionFilter extends FileFilter {
    private final String description;
    private final Set<String> extensions;

    public DirectoryFriendlyExtensionFilter(String description, String... extensions) {
        this.description = description;
        this.extensions = new HashSet<>();
        for (String ext : extensions) {
            if (ext == null) {
                continue;
            }
            String cleaned = ext.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith(".")) {
                cleaned = cleaned.substring(1);
            }
            if (!cleaned.isEmpty()) {
                this.extensions.add(cleaned);
            }
        }
    }

    @Override
    public boolean accept(File file) {
        if (file == null) {
            return false;
        }

        // Critical: always show folders so users can navigate.
        if (file.isDirectory()) {
            return true;
        }

        if (extensions.isEmpty()) {
            return true;
        }

        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }

        String ext = name.substring(dot + 1);
        return extensions.contains(ext);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "DirectoryFriendlyExtensionFilter{"
                + "description='" + description + '\''
                + ", extensions=" + Arrays.toString(extensions.toArray())
                + '}';
    }
}
