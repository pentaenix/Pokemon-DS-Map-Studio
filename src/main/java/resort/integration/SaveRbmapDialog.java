package resort.integration;

import editor.handler.MapEditorHandler;
import resort.bake.ResortBakeReport;
import resort.bake.ResortBaker;
import resort.formats.ResortMapMetadata;
import utils.DirectoryFriendlyExtensionFilter;
import utils.Utils;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-step bake + save: config is embedded in the RBMAP; no PDMS save or .resort.json sidecar required.
 */
public final class SaveRbmapDialog {

    private final MapEditorHandler handler;
    private final ResortBaker baker;

    public SaveRbmapDialog(MapEditorHandler handler) {
        this.handler = handler;
        this.baker = new ResortBaker(handler);
    }

    public boolean showDialog(Component parent) {
        if (handler.getResortTilesetBinding() == null) {
            JOptionPane.showMessageDialog(parent,
                    "Open a tileset from RTPKS first (Pokémon Resort → Open RTPKS as Tileset).",
                    "Save as RBMAP",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        ResortMapMetadata metadata = ResortMetadataDefaults.fromMapFilePath(handler);
        ResortMetadataForm form = new ResortMetadataForm(metadata);

        JTextField rbmapPathField = new JTextField(32);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            Path chosen = chooseRbmapPath(parent, rbmapPathField.getText());
            if (chosen != null) {
                rbmapPathField.setText(chosen.toString());
                updateRpakPreview(form, chosen);
                String base = Utils.removeExtensionFromPath(chosen.getFileName().toString());
                if (metadata.mapId == null || metadata.mapId.isEmpty() || "untitled_map".equals(metadata.mapId)) {
                    metadata.mapId = base;
                    metadata.displayName = base;
                    form.load(metadata);
                }
            }
        });

        JPanel pathRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pathRow.add(new JLabel("RBMAP file:"));
        pathRow.add(rbmapPathField);
        pathRow.add(browse);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.add(new JLabel("<html>Bake the current map into a single <b>.rbmap</b> file. "
                + "Settings below are stored inside the file (no separate config step).</html>"),
                BorderLayout.NORTH);
        root.add(form.getPanel(), BorderLayout.CENTER);
        root.add(pathRow, BorderLayout.SOUTH);

        suggestInitialRbmapPath(handler, metadata, rbmapPathField);
        if (!rbmapPathField.getText().isEmpty()) {
            updateRpakPreview(form, Paths.get(rbmapPathField.getText()));
        }

        int result = JOptionPane.showConfirmDialog(parent, root,
                "Save as RBMAP (bake + export)",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        if (!form.apply(metadata)) {
            return false;
        }

        String pathText = rbmapPathField.getText().trim();
        if (pathText.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Choose an RBMAP output path.", "Save as RBMAP",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        Path rbmapPath = Paths.get(pathText);
        if (!pathText.toLowerCase().endsWith(".rbmap")) {
            rbmapPath = Paths.get(pathText + ".rbmap");
        }

        ResortMetadataDefaults.applyRbmapOutputPaths(metadata, rbmapPath);
        Path rpakPath = ResortMetadataDefaults.siblingRpakPath(rbmapPath);

        ResortSetupValidator.ValidationReport validation =
                ResortSetupValidator.validate(handler, metadata);
        if (!validation.valid) {
            int proceed = JOptionPane.showConfirmDialog(parent,
                    validation.toDisplayText() + "\n\nSave and bake anyway?",
                    "Validation Failed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (proceed != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try {
            handler.setResortMapMetadata(metadata);
            ResortBakeReport report = baker.bakeToPaths(metadata, rbmapPath, rpakPath);
            handler.getMapMatrix().filePath = rbmapPath.toString();
            if (rbmapPath.getParent() != null) {
                handler.setLastMapDirectoryUsed(rbmapPath.getParent().toString());
            }
            JOptionPane.showMessageDialog(parent,
                    report.toDisplayText()
                            + "\n\nConfig is embedded in the RBMAP (editorConfig).",
                    "RBMAP Saved",
                    report.errors.isEmpty()
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    ex.getMessage() != null ? ex.getMessage() : ex.toString(),
                    "Save as RBMAP Failed",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static void suggestInitialRbmapPath(MapEditorHandler handler,
                                                ResortMapMetadata metadata,
                                                JTextField field) {
        String mapPath = handler.getMapMatrix().filePath;
        if (mapPath != null && mapPath.toLowerCase().endsWith(".rbmap")) {
            field.setText(mapPath);
            return;
        }
        String base = metadata.mapId != null && !metadata.mapId.isEmpty()
                ? metadata.mapId
                : "untitled_map";
        String dir = handler.getLastMapDirectoryUsed();
        if (dir == null || dir.isEmpty()) {
            dir = System.getProperty("user.home");
        }
        field.setText(Paths.get(dir, base + ".rbmap").toString());
    }

    private static Path chooseRbmapPath(Component parent, String current) {
        JFileChooser chooser = new JFileChooser();
        if (current != null && !current.isEmpty()) {
            File f = new File(current);
            chooser.setSelectedFile(f);
            if (f.getParentFile() != null) {
                chooser.setCurrentDirectory(f.getParentFile());
            }
        }
        chooser.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Resort Baked Map (*.rbmap)", "rbmap"));
        chooser.setDialogTitle("Save RBMAP");
        chooser.setApproveButtonText("Save");
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return null;
        }
        String path = selected.getPath();
        if (!path.toLowerCase().endsWith(".rbmap")) {
            path = path + ".rbmap";
        }
        return Paths.get(path);
    }

    private static void updateRpakPreview(ResortMetadataForm form, Path rbmapPath) {
        Path rpak = ResortMetadataDefaults.siblingRpakPath(rbmapPath);
        form.setRpakPreview(rpak.getFileName().toString());
    }

}
