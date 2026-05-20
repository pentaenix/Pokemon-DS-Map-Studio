import java.io.File;
import java.util.Arrays;

public class DirectoryAccessSmoke {
    public static void main(String[] args) {
        File dir = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "Downloads");

        System.out.println("[DirectoryAccessSmoke] java=" + System.getProperty("java.version"));
        System.out.println("[DirectoryAccessSmoke] dir=" + dir.getAbsolutePath());
        System.out.println("[DirectoryAccessSmoke] exists=" + dir.exists());
        System.out.println("[DirectoryAccessSmoke] isDirectory=" + dir.isDirectory());
        System.out.println("[DirectoryAccessSmoke] canRead=" + dir.canRead());
        System.out.println("[DirectoryAccessSmoke] canExecute=" + dir.canExecute());

        File[] files = dir.listFiles();
        System.out.println("[DirectoryAccessSmoke] listFiles=" + (files == null ? "null" : files.length));

        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (int i = 0; i < Math.min(files.length, 100); i++) {
                File f = files[i];
                System.out.println("[DirectoryAccessSmoke] "
                        + (f.isDirectory() ? "[dir] " : "[file] ")
                        + f.getName());
            }
        }
    }
}
