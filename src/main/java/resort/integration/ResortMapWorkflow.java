package resort.integration;

import editor.MainFrame;
import editor.handler.MapEditorHandler;
import resort.bake.RbmapGridImporter;
import resort.formats.RtpksReader;
import resort.runtimeformat.RbmapReader;
import resort.runtimeformat.RbmapVerificationReport;
import resort.runtimeformat.RbmapVerifier;
import resort.runtimeformat.RpakDocument;
import resort.runtimeformat.RpakReader;
import utils.DirectoryFriendlyExtensionFilter;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResortMapWorkflow {

    private final MainFrame mainFrame;
    private final MapEditorHandler handler;

    public ResortMapWorkflow(MainFrame mainFrame, MapEditorHandler handler) {
        this.mainFrame = mainFrame;
        this.handler = handler;
    }

    public void openRbmapWithDialog() {
        JFileChooser chooser = new JFileChooser();
        String lastDir = handler.getLastMapDirectoryUsed();
        if (lastDir == null || lastDir.isEmpty()) {
            lastDir = handler.getLastTilesetDirectoryUsed();
        }
        if (lastDir != null && !lastDir.isEmpty()) {
            chooser.setCurrentDirectory(new File(lastDir));
        }
        chooser.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Resort Baked Map (*.rbmap)", "rbmap"));
        chooser.setDialogTitle("Open RBMAP as Map");
        chooser.setApproveButtonText("Open");
        if (chooser.showOpenDialog(mainFrame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        openRbmap(chooser.getSelectedFile().toPath());
    }

    public void openRbmap(Path rbmapPath) {
        try {
            RbmapVerificationReport verification = RbmapVerifier.verify(rbmapPath);
            if (!verification.valid) {
                JOptionPane.showMessageDialog(mainFrame,
                        verification.toDisplayText(),
                        "RBMAP Verification Failed",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            RbmapReader.LoadResult rbmap = RbmapReader.load(rbmapPath);
            Path rpakPath = RbmapVerifier.resolveRpakPath(rbmapPath, rbmap.document.rpakDependency);
            RpakReader.LoadResult rpak = RpakReader.load(rpakPath);

            Path rtpksPath = resolveRtpksPath(rbmapPath, rpakPath, rpak.document);
            if (rtpksPath == null) {
                rtpksPath = promptForRtpks();
                if (rtpksPath == null) {
                    return;
                }
            }

            try (RtpksReader.LoadResult rtpks = RtpksReader.load(rtpksPath)) {
                RbmapGridImporter.ImportResult importResult = mainFrame.openRbmap(
                        rbmapPath,
                        rbmap.document,
                        rtpks);
                showOpenSuccess(rbmapPath, rtpksPath, verification, importResult);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                    ex.getMessage() != null ? ex.getMessage() : ex.toString(),
                    "Error Opening RBMAP",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    static Path resolveRtpksPath(Path rbmapPath, Path rpakPath, RpakDocument rpakDocument) {
        if (rpakDocument.sourceRtpks == null || rpakDocument.sourceRtpks.trim().isEmpty()) {
            return null;
        }

        Path configured = Paths.get(rpakDocument.sourceRtpks);
        if (Files.isRegularFile(configured)) {
            return configured.toAbsolutePath().normalize();
        }

        Path runtimeRoot = RbmapVerifier.inferRuntimeRoot(rbmapPath);
        if (runtimeRoot != null) {
            Path candidate = runtimeRoot.resolve(configured).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        if (rpakPath.getParent() != null) {
            Path candidate = rpakPath.getParent().resolve(configured).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        if (rbmapPath.getParent() != null) {
            Path candidate = rbmapPath.getParent().resolve(configured).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private Path promptForRtpks() {
        JFileChooser chooser = new JFileChooser();
        String lastDir = handler.getLastTilesetDirectoryUsed();
        if (lastDir != null && !lastDir.isEmpty()) {
            chooser.setCurrentDirectory(new File(lastDir));
        }
        chooser.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Resort Tile Pack Source (*.rtpks)", "rtpks"));
        chooser.setDialogTitle("Select Source RTPKS");
        chooser.setApproveButtonText("Open");
        int result = chooser.showOpenDialog(mainFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private void showOpenSuccess(Path rbmapPath,
                                 Path rtpksPath,
                                 RbmapVerificationReport verification,
                                 RbmapGridImporter.ImportResult importResult) {
        StringBuilder message = new StringBuilder();
        message.append("Loaded RBMAP into editor.\n\n");
        message.append("RBMAP: ").append(rbmapPath.toAbsolutePath().normalize()).append('\n');
        message.append("RTPKS: ").append(rtpksPath.toAbsolutePath().normalize()).append('\n');
        message.append("Placements imported: ").append(importResult.cellCount).append('\n');
        message.append("Chunks: ").append(verification.chunkCount).append('\n');
        if (!importResult.warnings.isEmpty()) {
            message.append("\nWarnings:\n");
            for (String warning : importResult.warnings) {
                message.append(" - ").append(warning).append('\n');
            }
        }
        if (!verification.warnings.isEmpty()) {
            message.append("\nVerification warnings:\n");
            for (String warning : verification.warnings) {
                message.append(" - ").append(warning).append('\n');
            }
        }
        JOptionPane.showMessageDialog(mainFrame,
                message.toString(),
                "RBMAP Loaded",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
