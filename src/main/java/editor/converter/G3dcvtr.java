package editor.converter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches Nitro system's g3dcvtr.exe.
 * Windows: runs the converter directly.
 * Linux / macOS: runs {@code wine converter/g3dcvtr.exe ...} — install Wine/CrossOver and place
 * g3dcvtr.exe (+ bundled DLL next to it) under {@code converter/}.
 */
public final class G3dcvtr {

    public static final String EXE_UNDER_CONVERTER = "converter/g3dcvtr.exe";

    private G3dcvtr() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /** Working directory containing the {@code converter/} folder. */
    public static Path workspaceDirectory() throws IOException {
        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path exe = userDir.resolve(EXE_UNDER_CONVERTER.replace('/', File.separatorChar));
        if (!Files.isRegularFile(exe)) {
            throw new IOException("Missing converter: " + exe);
        }
        return userDir;
    }

    /**
     * IMD path argument for the subprocess. Relative under Wine/Linux/macOS when possible
     * (Wine is fragile with absolute Windows-style paths).
     */
    public static String imdPathArgument(String absoluteImdPath, Path cwd) throws IOException {
        Path imdAbs = Paths.get(absoluteImdPath).toRealPath().normalize();
        if (isWindows()) {
            return imdAbs.toString();
        }
        try {
            return cwd.relativize(imdAbs).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // IMD lives outside workspace (different volume or root) — pass absolute POSIX path for Wine fallback
            return imdAbs.toString().replace('\\', '/');
        }
    }

    private static ProcessBuilder baseBuilder(Path cwd, List<String> argv) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(cwd.toFile());
        return pb;
    }

    /** NSBMD export: {@code -eboth} or {@code -emdl}. */
    public static ProcessBuilder processBuilderForModel(String absoluteImdPath, String filenameOutFlag, boolean eboth)
            throws IOException {
        Path cwd = workspaceDirectory();
        String imdArg = imdPathArgument(absoluteImdPath, cwd);
        List<String> argv = new ArrayList<>();
        if (!isWindows()) {
            argv.add("wine");
        }
        argv.add(EXE_UNDER_CONVERTER);
        argv.add(imdArg);
        argv.add(eboth ? "-eboth" : "-emdl");
        argv.add("-o");
        argv.add(filenameOutFlag);
        return baseBuilder(cwd, argv);
    }

    /** NSBTX texture export {@code -etex}. */
    public static ProcessBuilder processBuilderForTexture(String absoluteImdPath, String filenameOutFlag)
            throws IOException {
        Path cwd = workspaceDirectory();
        String imdArg = imdPathArgument(absoluteImdPath, cwd);
        List<String> argv = new ArrayList<>();
        if (!isWindows()) {
            argv.add("wine");
        }
        argv.add(EXE_UNDER_CONVERTER);
        argv.add(imdArg);
        argv.add("-etex");
        argv.add("-o");
        argv.add(filenameOutFlag);
        return baseBuilder(cwd, argv);
    }
}
