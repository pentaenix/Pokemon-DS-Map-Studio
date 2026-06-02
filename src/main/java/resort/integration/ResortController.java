package resort.integration;

import editor.MainFrame;
import editor.handler.MapEditorHandler;
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
    private final SaveRbmapDialog saveRbmapDialog;

    public ResortController(MainFrame mainFrame, MapEditorHandler handler) {
        this.mainFrame = mainFrame;
        this.handler = handler;
        this.tilesetWorkflow = new ResortTilesetWorkflow(mainFrame, handler);
        this.mapWorkflow = new ResortMapWorkflow(mainFrame, handler);
        this.metadataDialog = new ConfigureResortMetadataDialog(handler);
        this.saveRbmapDialog = new SaveRbmapDialog(handler);
    }

    public void installMenu(JMenuBar menuBar) {
        JMenu resortMenu = new JMenu("Pokémon Resort");

        JMenuItem openRtpks = new JMenuItem("Open RTPKS as Tileset...");
        openRtpks.addActionListener(e -> tilesetWorkflow.openRtpksWithDialog());
        resortMenu.add(openRtpks);

        JMenuItem saveRtpks = new JMenuItem("Save Current Tileset as RTPKS...");
        saveRtpks.addActionListener(e -> tilesetWorkflow.saveTilesetAsRtpksWithDialog());
        resortMenu.add(saveRtpks);

        JMenuItem appendRtpks = new JMenuItem("Append Current Tiles to RTPKS...");
        appendRtpks.setToolTipText("Merge the editor tileset onto an existing RTPKS pack.");
        appendRtpks.addActionListener(e -> tilesetWorkflow.appendCurrentTilesToRtpksWithDialog());
        resortMenu.add(appendRtpks);
        resortMenu.addSeparator();

        JMenuItem saveRbmap = new JMenuItem("Save as RBMAP (bake + config)...");
        saveRbmap.addActionListener(e -> saveAsRbmap());
        resortMenu.add(saveRbmap);

        JMenuItem configureMetadata = new JMenuItem("Configure Resort Map Metadata...");
        configureMetadata.addActionListener(e -> metadataDialog.showDialog(mainFrame));
        resortMenu.add(configureMetadata);
        resortMenu.addSeparator();

        JMenuItem validate = new JMenuItem("Validate Current Resort Setup...");
        validate.addActionListener(e -> validateSetup());
        resortMenu.add(validate);

        JMenuItem bakeSelected = new JMenuItem("Bake Selected Map to RBMAP...");
        bakeSelected.setToolTipText("Opens Save as RBMAP (same one-step bake + export).");
        bakeSelected.addActionListener(e -> saveAsRbmap());
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

    public void saveAsRbmap() {
        if (saveRbmapDialog.showDialog(mainFrame)) {
            mainFrame.setTitleFromHandler();
            mainFrame.updateResortStatus();
        }
    }

    public void validateSetup() {
        ResortMapMetadata metadata = resolveMetadata(mainFrame, false);
        if (metadata == null) {
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
        saveAsRbmap();
    }

    /** Metadata: in-memory → sidecar → defaults (no PDMS save required). */
    ResortMapMetadata resolveMetadata(Component parent, boolean warnOnEmptyBinding) {
        if (handler.getResortMapMetadata() != null) {
            return handler.getResortMapMetadata();
        }

        Path sidecarPath = ResortSetupValidator.resolveSidecarForHandler(handler);
        if (sidecarPath != null) {
            try {
                ResortMapMetadata metadata = ResortMapMetadataIO.read(sidecarPath);
                if (metadata != null) {
                    handler.setResortMapMetadata(metadata);
                    return metadata;
                }
            } catch (Exception ex) {
                if (parent != null) {
                    JOptionPane.showMessageDialog(parent, ex.getMessage(), "Pokémon Resort", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }
        }

        if (warnOnEmptyBinding && handler.getResortTilesetBinding() == null) {
            JOptionPane.showMessageDialog(parent,
                    "Open a tileset from RTPKS first.",
                    "Pokémon Resort",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        ResortMapMetadata defaults = ResortMetadataDefaults.fromMapFilePath(handler);
        handler.setResortMapMetadata(defaults);
        return defaults;
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
