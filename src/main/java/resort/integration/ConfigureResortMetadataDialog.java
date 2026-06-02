package resort.integration;

import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import resort.formats.ResortMapMetadataIO;
import utils.Utils;

import javax.swing.JOptionPane;
import java.io.File;
import java.nio.file.Path;

/**
 * Edit resort metadata in memory; optional .resort.json sidecar when a PDMS map path exists.
 */
public class ConfigureResortMetadataDialog {

    private final MapEditorHandler handler;

    public ConfigureResortMetadataDialog(MapEditorHandler handler) {
        this.handler = handler;
    }

    public boolean showDialog(java.awt.Component parent) {
        ResortMapMetadata metadata = loadMetadata(parent);
        if (metadata == null) {
            return false;
        }

        ResortMetadataForm form = new ResortMetadataForm(metadata);
        int result = JOptionPane.showConfirmDialog(parent, form.getPanel(),
                "Configure Resort Map Metadata",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        if (!form.apply(metadata)) {
            return false;
        }

        handler.setResortMapMetadata(metadata);

        Path sidecarPath = ResortSetupValidator.resolveSidecarForHandler(handler);
        if (sidecarPath != null) {
            try {
                ResortMapMetadataIO.write(sidecarPath, metadata);
                JOptionPane.showMessageDialog(parent,
                        "Saved in-memory and sidecar:\n" + sidecarPath,
                        "Resort Metadata",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, ex.getMessage(), "Resort Metadata", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            JOptionPane.showMessageDialog(parent,
                    "Saved in memory. Use File → Save as RBMAP to export, or save a PDMS map to also write a .resort.json sidecar.",
                    "Resort Metadata",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        return true;
    }

    private ResortMapMetadata loadMetadata(java.awt.Component parent) {
        if (handler.getResortMapMetadata() != null) {
            return ResortMetadataDefaults.copy(handler.getResortMapMetadata());
        }

        Path sidecarPath = ResortSetupValidator.resolveSidecarForHandler(handler);
        if (sidecarPath != null) {
            try {
                ResortMapMetadata fromSidecar = ResortMapMetadataIO.read(sidecarPath);
                if (fromSidecar != null) {
                    return fromSidecar;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, ex.getMessage(), "Resort Metadata", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }

        String mapPath = handler.getMapMatrix().filePath;
        String base = "untitled_map";
        if (mapPath != null && !mapPath.isEmpty()) {
            base = Utils.removeExtensionFromPath(new File(mapPath).getName());
        }
        return ResortMetadataDefaults.forHandler(handler, base);
    }
}
