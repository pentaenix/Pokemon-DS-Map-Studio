import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

import utils.DirectoryFriendlyExtensionFilter;

public class FileChooserSmoke {
    private static void logDirContents(String label, File dir) {
        System.out.println("[FileChooserSmoke] " + label + " path=" + dir.getAbsolutePath());
        System.out.println("[FileChooserSmoke] " + label + " exists=" + dir.exists()
                + " canRead=" + dir.canRead());
        File[] files = dir.listFiles();
        System.out.println("[FileChooserSmoke] " + label + " count=" + (files == null ? "null" : files.length));
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (int i = 0; i < Math.min(files.length, 100); i++) {
                File f = files[i];
                System.out.println("[FileChooserSmoke] " + label + " "
                        + (f.isDirectory() ? "[dir] " : "[file] ")
                        + f.getName());
            }
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            System.setProperty("apple.awt.fileDialogForDirectories", "false");

            File downloads = new File(System.getProperty("user.home"), "Downloads");
            logDirContents("Downloads (before chooser)", downloads);

            JFileChooser fc = new JFileChooser(downloads);
            fc.setFileHidingEnabled(false);
            fc.setAcceptAllFileFilterUsed(true);
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                    "Pokemon DS map (*.pdsmap)",
                    "pdsmap"));

            int r = fc.showOpenDialog(null);
            System.out.println("result=" + r + " selected=" + fc.getSelectedFile());
            System.exit(0);
        });
    }
}
