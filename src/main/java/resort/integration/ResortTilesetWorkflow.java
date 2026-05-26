package resort.integration;

import editor.MainFrame;
import editor.handler.MapEditorHandler;
import resort.formats.RtpksReader;
import resort.formats.RtpksWriter;
import resort.formats.TileIndex;
import utils.Utils;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResortTilesetWorkflow {

    private final MainFrame mainFrame;
    private final MapEditorHandler handler;

    public ResortTilesetWorkflow(MainFrame mainFrame, MapEditorHandler handler) {
        this.mainFrame = mainFrame;
        this.handler = handler;
    }

    public void openRtpksWithDialog() {
        JFileChooser chooser = new JFileChooser();
        if (handler.getLastTilesetDirectoryUsed() != null) {
            chooser.setCurrentDirectory(new File(handler.getLastTilesetDirectoryUsed()));
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Resort Tile Pack Source (*.rtpks)", "rtpks"));
        chooser.setDialogTitle("Open RTPKS as Tileset");
        chooser.setApproveButtonText("Open");
        if (chooser.showOpenDialog(mainFrame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        openRtpks(chooser.getSelectedFile().toPath());
    }

    public void openRtpks(Path rtpksPath) {
        try (RtpksReader.LoadResult result = RtpksReader.load(rtpksPath)) {
            handler.setTileset(result.tileset);
            handler.getMapMatrix().tilesetFilePath = rtpksPath.toString();
            handler.setLastTilesetDirectoryUsed(rtpksPath.getParent().toString());
            handler.setResortTilesetBinding(new ResortTilesetBinding(
                    rtpksPath, result.manifest, result.tileIndex));
            mainFrame.refreshEditorAfterTilesetChange();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                    ex.getMessage(),
                    "Error opening RTPKS",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveTilesetAsRtpksWithDialog() {
        if (handler.getTileset() == null || handler.getTileset().size() == 0) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Load a tileset before saving RTPKS.",
                    "Save RTPKS",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        if (handler.getLastTilesetDirectoryUsed() != null) {
            chooser.setCurrentDirectory(new File(handler.getLastTilesetDirectoryUsed()));
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Resort Tile Pack Source (*.rtpks)", "rtpks"));
        chooser.setDialogTitle("Save Current Tileset as RTPKS");
        chooser.setApproveButtonText("Save");

        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding != null && binding.getRtpksPath() != null) {
            chooser.setSelectedFile(binding.getRtpksPath().toFile());
        }

        if (chooser.showSaveDialog(mainFrame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".rtpks")) {
            path = path.resolveSibling(path.getFileName() + ".rtpks");
        }
        saveTilesetAsRtpks(path);
    }

    public void saveTilesetAsRtpks(Path rtpksPath) {
        try {
            ResortTilesetBinding binding = handler.getResortTilesetBinding();
            String packId = binding != null && binding.getManifest() != null
                    ? binding.getManifest().packId
                    : Utils.removeExtensionFromPath(rtpksPath.getFileName().toString());
            String displayName = binding != null
                    ? binding.getDisplayName()
                    : packId;
            TileIndex existing = binding != null ? binding.getTileIndex() : null;

            RtpksWriter.WriteRequest request = new RtpksWriter.WriteRequest(
                    handler.getTileset(),
                    packId,
                    displayName,
                    existing,
                    captureTilesetThumbnail());
            RtpksWriter.write(rtpksPath, request);

            if (binding == null) {
                binding = new ResortTilesetBinding(
                        rtpksPath,
                        null,
                        request.writtenTileIndex);
                handler.setResortTilesetBinding(binding);
            } else {
                binding.updateAfterSave(rtpksPath, request.writtenTileIndex);
            }
            handler.getMapMatrix().tilesetFilePath = rtpksPath.toString();
            handler.setLastTilesetDirectoryUsed(rtpksPath.getParent().toString());
            mainFrame.updateResortStatus();
            JOptionPane.showMessageDialog(mainFrame,
                    "Saved RTPKS:\n" + rtpksPath,
                    "Save RTPKS",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                    ex.getMessage(),
                    "Error saving RTPKS",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private byte[] captureTilesetThumbnail() {
        try {
            BufferedImage image = mainFrame.getTilesetThumbnailImage();
            if (image == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    public static String defaultPackIdFromPath(Path path) {
        return Utils.removeExtensionFromPath(path.getFileName().toString());
    }

    public static Path normalizeRtpksPath(String path) {
        return Paths.get(path).toAbsolutePath().normalize();
    }
}
