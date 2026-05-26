package resort.integration;

import editor.handler.MapEditorHandler;
import resort.formats.ResortMapMetadata;
import resort.formats.ResortMapMetadataIO;
import utils.Utils;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;

public class ConfigureResortMetadataDialog {

    private final MapEditorHandler handler;

    public ConfigureResortMetadataDialog(MapEditorHandler handler) {
        this.handler = handler;
    }

    public boolean showDialog(java.awt.Component parent) {
        String mapPath = handler.getMapMatrix().filePath;
        if (mapPath == null || mapPath.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Save the map/project first so a .resort.json sidecar can be created.",
                    "Configure Resort Map Metadata",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        Path sidecarPath = ResortMapMetadataIO.sidecarPathForMap(new File(mapPath).toPath());
        ResortMapMetadata metadata;
        try {
            metadata = ResortMapMetadataIO.read(sidecarPath);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Resort Metadata", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (metadata == null) {
            metadata = createDefaultMetadata(mapPath);
        }

        MetadataForm form = new MetadataForm(metadata);
        int result = JOptionPane.showConfirmDialog(parent, form.panel,
                "Configure Resort Map Metadata", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        if (!form.apply(metadata)) {
            return false;
        }

        try {
            ResortMapMetadataIO.write(sidecarPath, metadata);
            JOptionPane.showMessageDialog(parent,
                    "Saved sidecar:\n" + sidecarPath,
                    "Resort Metadata",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Resort Metadata", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private ResortMapMetadata createDefaultMetadata(String mapPath) {
        ResortMapMetadata metadata = new ResortMapMetadata();
        String base = Utils.removeExtensionFromPath(new File(mapPath).getName());
        metadata.mapId = base;
        metadata.displayName = base;
        metadata.bake.output.mapName = "maps/" + base + ".rbmap";

        ResortTilesetBinding binding = handler.getResortTilesetBinding();
        if (binding != null && binding.getRtpksPath() != null) {
            metadata.tilePackSource = binding.getRtpksPath().toString();
        }

        ResortMapMetadata.SpawnEntry spawn = new ResortMapMetadata.SpawnEntry();
        spawn.map[0] = handler.getMapSelected().x;
        spawn.map[1] = handler.getMapSelected().y;
        spawn.tile[0] = 5;
        spawn.tile[1] = 5;
        metadata.spawns.clear();
        metadata.spawns.add(spawn);
        return metadata;
    }

    private static final class MetadataForm {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JTextField mapId = new JTextField(24);
        private final JTextField displayName = new JTextField(24);
        private final JTextField tilePackSource = new JTextField(24);
        private final JTextField defaultCamera = new JTextField(24);
        private final JTextField spawnMapX = new JTextField(4);
        private final JTextField spawnMapY = new JTextField(4);
        private final JTextField spawnTileX = new JTextField(4);
        private final JTextField spawnTileY = new JTextField(4);
        private final JComboBox<String> spawnFacing = new JComboBox<>(new String[]{
                "north", "south", "east", "west"
        });
        private final JTextField exportX = new JTextField(4);
        private final JTextField exportY = new JTextField(4);
        private final JTextField exportWidth = new JTextField(4);
        private final JTextField exportHeight = new JTextField(4);
        private final JTextField chunkSize = new JTextField(4);
        private final JTextField heightScale = new JTextField(6);
        private final JTextField runtimeRoot = new JTextField(24);
        private final JTextField assetPack = new JTextField(24);
        private final JTextField mapOutput = new JTextField(24);

        private MetadataForm(ResortMapMetadata metadata) {
            mapId.setText(metadata.mapId);
            displayName.setText(metadata.displayName);
            tilePackSource.setText(metadata.tilePackSource);
            defaultCamera.setText(metadata.defaultCamera);

            ResortMapMetadata.SpawnEntry spawn = metadata.spawns.isEmpty()
                    ? new ResortMapMetadata.SpawnEntry()
                    : metadata.spawns.get(0);
            spawnMapX.setText(Integer.toString(spawn.map[0]));
            spawnMapY.setText(Integer.toString(spawn.map[1]));
            spawnTileX.setText(Integer.toString(spawn.tile[0]));
            spawnTileY.setText(Integer.toString(spawn.tile[1]));
            spawnFacing.setSelectedItem(spawn.facing);

            exportX.setText(Integer.toString(metadata.bake.exportRegion.x));
            exportY.setText(Integer.toString(metadata.bake.exportRegion.y));
            exportWidth.setText(Integer.toString(metadata.bake.exportRegion.width));
            exportHeight.setText(Integer.toString(metadata.bake.exportRegion.height));
            chunkSize.setText(Integer.toString(metadata.bake.chunkSizeTiles));
            heightScale.setText(Float.toString(metadata.bake.heightScale));
            runtimeRoot.setText(metadata.bake.output.runtimeRoot);
            assetPack.setText(metadata.bake.output.assetPack);
            mapOutput.setText(metadata.bake.output.mapName);

            int row = 0;
            addRow(row++, "Map ID", mapId);
            addRow(row++, "Display Name", displayName);
            addRow(row++, "RTPKS Path", tilePackSource, createBrowseButton(tilePackSource, "rtpks"));
            addRow(row++, "Camera Preset", defaultCamera);
            addRow(row++, "Spawn Map X/Y", spawnMapX, spawnMapY);
            addRow(row++, "Spawn Tile X/Y", spawnTileX, spawnTileY);
            addRow(row++, "Spawn Facing", spawnFacing);
            addRow(row++, "Export X/Y", exportX, exportY);
            addRow(row++, "Export Width/Height", exportWidth, exportHeight);
            addRow(row++, "Chunk Size (tiles)", chunkSize);
            addRow(row++, "Height Scale", heightScale);
            addRow(row++, "Runtime Root", runtimeRoot);
            addRow(row++, "Shared RPAK Path", assetPack);
            addRow(row++, "RBMAP Output Path", mapOutput);
        }

        private void addRow(int row, String label, java.awt.Component field) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.WEST;
            labelConstraints.insets = new Insets(2, 2, 2, 8);
            panel.add(new JLabel(label), labelConstraints);

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.weightx = 1.0;
            fieldConstraints.insets = new Insets(2, 2, 2, 2);
            panel.add(field, fieldConstraints);
        }

        private void addRow(int row, String label, java.awt.Component first, java.awt.Component second) {
            addRow(row, label, first);
            GridBagConstraints secondConstraints = new GridBagConstraints();
            secondConstraints.gridx = 2;
            secondConstraints.gridy = row;
            secondConstraints.fill = GridBagConstraints.HORIZONTAL;
            secondConstraints.weightx = 0.3;
            secondConstraints.insets = new Insets(2, 2, 2, 2);
            panel.add(second, secondConstraints);
        }

        private void addRow(int row, String label, JTextField field, JButton button) {
            addRow(row, label, field);
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.gridx = 2;
            buttonConstraints.gridy = row;
            buttonConstraints.insets = new Insets(2, 2, 2, 2);
            panel.add(button, buttonConstraints);
        }

        private JButton createBrowseButton(JTextField target, String extension) {
            JButton button = new JButton("Browse...");
            button.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("*." + extension, extension));
                if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                    target.setText(chooser.getSelectedFile().getPath());
                }
            });
            return button;
        }

        private boolean apply(ResortMapMetadata metadata) {
            try {
                metadata.mapId = mapId.getText().trim();
                metadata.displayName = displayName.getText().trim();
                metadata.tilePackSource = tilePackSource.getText().trim();
                metadata.defaultCamera = defaultCamera.getText().trim();

                ResortMapMetadata.SpawnEntry spawn = metadata.spawns.isEmpty()
                        ? new ResortMapMetadata.SpawnEntry()
                        : metadata.spawns.get(0);
                spawn.map[0] = Integer.parseInt(spawnMapX.getText().trim());
                spawn.map[1] = Integer.parseInt(spawnMapY.getText().trim());
                spawn.tile[0] = Integer.parseInt(spawnTileX.getText().trim());
                spawn.tile[1] = Integer.parseInt(spawnTileY.getText().trim());
                spawn.facing = (String) spawnFacing.getSelectedItem();
                if (metadata.spawns.isEmpty()) {
                    metadata.spawns.add(spawn);
                }

                metadata.bake.exportRegion.x = Integer.parseInt(exportX.getText().trim());
                metadata.bake.exportRegion.y = Integer.parseInt(exportY.getText().trim());
                metadata.bake.exportRegion.width = Integer.parseInt(exportWidth.getText().trim());
                metadata.bake.exportRegion.height = Integer.parseInt(exportHeight.getText().trim());
                metadata.bake.chunkSizeTiles = Integer.parseInt(chunkSize.getText().trim());
                metadata.bake.heightScale = Float.parseFloat(heightScale.getText().trim());
                metadata.bake.output.runtimeRoot = runtimeRoot.getText().trim();
                metadata.bake.output.assetPack = assetPack.getText().trim();
                metadata.bake.output.mapName = mapOutput.getText().trim();
                return true;
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Invalid numeric field.", "Resort Metadata",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }
}
