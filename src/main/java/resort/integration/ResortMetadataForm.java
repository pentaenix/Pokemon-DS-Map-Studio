package resort.integration;

import resort.formats.ResortMapMetadata;
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

/**
 * Editor fields for resort map metadata (baked into RBMAP {@code editorConfig}).
 */
public final class ResortMetadataForm {

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
    private final JLabel rpakPreview = new JLabel(" ");

    public ResortMetadataForm(ResortMapMetadata metadata) {
        load(metadata);
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
        addRow(row++, "RPAK (auto, beside RBMAP)", rpakPreview);
    }

    public JPanel getPanel() {
        return panel;
    }

    public void setRpakPreview(String text) {
        rpakPreview.setText(text);
    }

    public void load(ResortMapMetadata metadata) {
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
    }

    public boolean apply(ResortMapMetadata metadata) {
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
            metadata.bake.exportRegion.map[0] = spawn.map[0];
            metadata.bake.exportRegion.map[1] = spawn.map[1];
            return true;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(panel, "Invalid numeric field.", "Resort Metadata",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
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
}
