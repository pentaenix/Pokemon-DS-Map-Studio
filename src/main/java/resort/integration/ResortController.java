package resort.integration;

import editor.MainFrame;
import editor.handler.MapEditorHandler;
import resort.bake.ResortBakeReport;
import resort.bake.ResortBaker;
import resort.formats.ResortMapMetadata;
import resort.formats.ResortMapMetadataIO;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.nio.file.Path;

public class ResortController {

    private final MainFrame mainFrame;
    private final MapEditorHandler handler;
    private final ResortTilesetWorkflow tilesetWorkflow;
    private final ResortMapWorkflow mapWorkflow;
    private final ConfigureResortMetadataDialog metadataDialog;
    private final ResortBaker baker;

    public ResortController(MainFrame mainFrame, MapEditorHandler handler) {
        this.mainFrame = mainFrame;
        this.handler = handler;
        this.tilesetWorkflow = new ResortTilesetWorkflow(mainFrame, handler);
        this.mapWorkflow = new ResortMapWorkflow(mainFrame, handler);
        this.metadataDialog = new ConfigureResortMetadataDialog(handler);
        this.baker = new ResortBaker(handler);
    }

    public void installMenu(JMenuBar menuBar) {
        JMenu resortMenu = new JMenu("Pokémon Resort");

        JMenuItem openRtpks = new JMenuItem("Open RTPKS as Tileset...");
        openRtpks.addActionListener(e -> tilesetWorkflow.openRtpksWithDialog());
        resortMenu.add(openRtpks);

        JMenuItem saveRtpks = new JMenuItem("Save Current Tileset as RTPKS...");
        saveRtpks.addActionListener(e -> tilesetWorkflow.saveTilesetAsRtpksWithDialog());
        resortMenu.add(saveRtpks);
        resortMenu.addSeparator();

        JMenuItem configureMetadata = new JMenuItem("Configure Resort Map Metadata...");
        configureMetadata.addActionListener(e -> metadataDialog.showDialog(mainFrame));
        resortMenu.add(configureMetadata);
        resortMenu.addSeparator();

        JMenuItem validate = new JMenuItem("Validate Current Resort Setup...");
        validate.addActionListener(e -> validateSetup());
        resortMenu.add(validate);

        JMenuItem bakeSelected = new JMenuItem("Bake Selected Map to RBMAP...");
        bakeSelected.addActionListener(e -> bakeSelectedMap());
        resortMenu.add(bakeSelected);

        JMenuItem openRbmap = new JMenuItem("Open RBMAP as Map...");
        openRbmap.addActionListener(e -> mapWorkflow.openRbmapWithDialog());
        resortMenu.add(openRbmap);

        JMenuItem bakeAll = new JMenuItem("Bake All Maps to RBMAPs...");
        bakeAll.setEnabled(false);
        bakeAll.setToolTipText("Not implemented yet.");
        bakeAll.addActionListener(e -> JOptionPane.showMessageDialog(mainFrame,
                "Bake All Maps is not implemented yet.",
                "Pokémon Resort",
                JOptionPane.INFORMATION_MESSAGE));
        resortMenu.add(bakeAll);

        menuBar.add(resortMenu);
    }

    public void validateSetup() {
        ResortMapMetadata metadata = loadSidecarOrWarn(mainFrame);
        if (metadata == null && handler.getMapMatrix().filePath.isEmpty()) {
            return;
        }
        ResortSetupValidator.ValidationReport report =
                ResortSetupValidator.validate(handler, metadata);
        JOptionPane.showMessageDialog(mainFrame,
                report.toDisplayText(),
                report.valid ? "Validation Passed" : "Validation Failed",
                report.valid ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    public void bakeSelectedMap() {
        ResortMapMetadata metadata = loadSidecarOrWarn(mainFrame);
        if (metadata == null) {
            return;
        }

        ResortSetupValidator.ValidationReport validation =
                ResortSetupValidator.validate(handler, metadata);
        if (!validation.valid) {
            int proceed = JOptionPane.showConfirmDialog(mainFrame,
                    validation.toDisplayText() + "\n\nBake anyway?",
                    "Validation Failed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (proceed != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            ResortBakeReport report = baker.bakeSelectedMap(metadata);
            JOptionPane.showMessageDialog(mainFrame,
                    report.toDisplayText(),
                    "Bake Complete",
                    report.errors.isEmpty()
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                    ex.getMessage(),
                    "Bake Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private ResortMapMetadata loadSidecarOrWarn(Component parent) {
        Path sidecarPath = ResortSetupValidator.resolveSidecarForHandler(handler);
        if (sidecarPath == null) {
            JOptionPane.showMessageDialog(parent,
                    "Save the map/project first, then configure Resort metadata.",
                    "Pokémon Resort",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            ResortMapMetadata metadata = ResortMapMetadataIO.read(sidecarPath);
            if (metadata == null) {
                JOptionPane.showMessageDialog(parent,
                        "Missing sidecar:\n" + sidecarPath
                                + "\nUse Configure Resort Map Metadata first.",
                        "Pokémon Resort",
                        JOptionPane.WARNING_MESSAGE);
            }
            return metadata;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Pokémon Resort", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    public void onTilesetOpenedFromPlainPdsts() {
        handler.clearResortTilesetBinding();
        mainFrame.updateResortStatus();
    }

    public String getStatusText() {
        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding == null) {
            return "RTPKS: (none)";
        }
        String dirty = binding.isDirty() ? " *" : "";
        String path = binding.getRtpksPath() != null ? binding.getRtpksPath().toString() : "";
        return "RTPKS: " + binding.getDisplayName() + dirty + " — " + path;
    }
}
