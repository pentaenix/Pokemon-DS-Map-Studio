package editor;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.GroupLayout;
import javax.swing.border.*;
import javax.swing.event.*;
import utils.DirectoryFriendlyExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.jogamp.opengl.GLContext;
import editor.about.AboutDialog;
import editor.game.patches.GamePatch;
import formats.animationeditor.AnimationEditorDialog;
import formats.backsound.BacksoundEditorDialog;
import formats.bdhc.BdhcEditorDialog;
import formats.bdhcam.BdhcamEditorDialog;
import editor.buildingeditor2.BuildingEditorChooser;
import formats.collisions.CollisionsEditorDialog;
import formats.collisions.bw.CollisionsEditorDialogBW;
import editor.converter.*;
import editor.game.Game;
import editor.gameselector.GameChangerDialog;
import editor.gameselector.GameSelectorDialog;
import editor.gameselector.GameTsetSelectorDialog2;
import editor.handler.MapData;
import editor.handler.MapEditorHandler;
import editor.heightselector.*;
import formats.imd.ExportImdDialog;
import formats.imd.ImdModel;
import formats.imd.ImdOutputInfoDialog;
import editor.keyboard.KeyboardInfoDialog2;
import editor.layerselector.*;
import editor.mapdisplay.*;
import editor.mapmatrix.*;
import formats.mapbin.ExportMapBinDialog;
import formats.mapbin.ExportMapBinInfoDialog;
import formats.nsbtx.NsbtxEditorDialog;
import formats.nsbtx2.Nsbtx2;
import formats.nsbtx2.NsbtxEditorDialog2;
import formats.nsbtx2.NsbtxLoader2;
import formats.obj.ExportMapObjDialog;
import formats.obj.ObjWriter;
import editor.settings.SettingsDialog;
import editor.smartdrawing.*;
import editor.state.MapLayerState;
import editor.state.StateHandler;
import editor.tileselector.*;
import editor.tileseteditor.*;
import net.miginfocom.swing.MigLayout;
import org.xml.sax.SAXException;
import tileset.*;
import utils.StartupTrace;
import utils.TilesetRendererPolicy;
import utils.Utils;
import resort.integration.ResortController;
import resort.integration.ResortTilesetBinding;
import resort.bake.RbmapGridImporter;
import resort.formats.RtpksReader;
import resort.runtimeformat.RbmapDocument;

/**
 * @author Trifindo, JackHack96
 */
public class MainFrame extends JFrame {
    MapEditorHandler handler;
    private ResortController resortController;
    private JLabel jlResortStatus;
    public static Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private static final List<String> recentMaps = new ArrayList<>();
    private boolean opened_map = false;

    public static void main(String[] args) {
        StartupTrace.log("main: enter");
        System.out.println("[PDSM] java.version=" + System.getProperty("java.version"));
        System.out.println("[PDSM] os.name=" + System.getProperty("os.name"));
        System.out.println("[PDSM] pdsm.enableJoglMap=" + System.getProperty("pdsm.enableJoglMap"));
        System.out.println("[PDSM] pdsm.enableJogl=" + System.getProperty("pdsm.enableJogl"));
        System.out.println("[PDSM] pdsm.enableJoglTilesetRenderer=" + System.getProperty("pdsm.enableJoglTilesetRenderer"));
        System.out.println("[PDSM] pdsm.enableJoglTilesetRendererStartup=" + System.getProperty("pdsm.enableJoglTilesetRendererStartup"));
        System.out.println("[PDSM] pdsm.enableJoglTilesetRendererRuntime=" + System.getProperty("pdsm.enableJoglTilesetRendererRuntime"));
        System.out.println("[PDSM] pdsm.enableJoglTile=" + System.getProperty("pdsm.enableJoglTile"));
        System.out.println("[PDSM] pdsm.useNativeFileDialog=" + System.getProperty("pdsm.useNativeFileDialog"));
        System.out.println("[PDSM] main thread=" + Thread.currentThread().getName());
        String os = System.getProperty("os.name", "");
        boolean macosx = os.startsWith("Mac") || os.contains("Darwin");
        StartupTrace.log("main: os=" + os + " macosx=" + macosx);
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Pokemon DS Map Studio needs a graphical desktop (java.awt.headless=true). "
                    + "Run from Terminal.app on the Mac desktop, not over SSH without a display.");
            System.exit(2);
        }
        if (macosx) {
            // Java2D Metal + JOGL NSOpenGL overlap can crash on macOS 14+; keep Java2D off Metal.
            System.setProperty("sun.java2d.metal", "false");
            System.setProperty("apple.awt.UIElement", "false");
            System.setProperty("apple.laf.useScreenMenuBar", "false");
            System.setProperty("apple.awt.application.name", "Pokemon DS Map Studio");
        }

        StartupTrace.log("main: before LaF / prefs");
        try {
            String theme = prefs.get("Theme", "Native");
            StartupTrace.log("main: theme=" + theme);
            switch (theme) {
                case "Native":
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
                case "FlatLaf":
                    UIManager.setLookAndFeel(new FlatLightLaf());
                    break;
                case "FlatLaf Dark":
                    UIManager.setLookAndFeel(new FlatDarculaLaf());
                    break;
            }
            loadRecentMaps();
            StartupTrace.log("main: LaF + loadRecentMaps done");
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }

        Runnable startUi = () -> {
            try {
                MainFrame mainFrame = new MainFrame();
                mainFrame.setVisible(true);
                StartupTrace.log("UI: setVisible(true) bounds=" + mainFrame.getBounds());
                try {
                    mainFrame.toFront();
                    mainFrame.setAlwaysOnTop(true);
                    mainFrame.setAlwaysOnTop(false);
                } catch (Throwable ignored) {
                }
                openStartupFiles(mainFrame, args);
                StartupTrace.log("UI: startup complete");
                mainFrame.traceMapLayout("after startup");
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        };

        // Normal Swing/AWT: build and show the frame on the EDT (including JOGL inside Swing).
        StartupTrace.log("main: EventQueue.invokeLater(startUi)");
        java.awt.EventQueue.invokeLater(startUi);
        StartupTrace.log("main: invokeLater queued; main thread continuing");
    }

    private static void openStartupFiles(MainFrame mainFrame, String[] args) {
        if (args.length <= 0) {
            return;
        }
        try {
            if (args[0].endsWith(MapMatrix.fileExtension)) {
                mainFrame.openMap(args[0]);
            } else if (args[0].endsWith(Tileset.fileExtension)) {
                mainFrame.openTileset(args[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void traceInit(String message) {
        System.out.println("[PDSM] " + System.currentTimeMillis()
                + " tid=" + Thread.currentThread().getId()
                + " " + Thread.currentThread().getName()
                + " | initComponents: " + message);
        System.out.flush();
        System.err.flush();
    }

    public MainFrame() {
        StartupTrace.log("MainFrame.<init>: enter");
        System.out.println("[PDSM] MainFrame.<init> before initComponents thread=" + Thread.currentThread().getName());
        initComponents();
        System.out.println("[PDSM] MainFrame.<init> after initComponents thread=" + Thread.currentThread().getName());

        updateRecentMapsMenu();

        jscTileList.getVerticalScrollBar().setUnitIncrement(16);
        jscSmartDrawing.getVerticalScrollBar().setUnitIncrement(16);
        jScrollPaneMapMatrix.getHorizontalScrollBar().setUnitIncrement(16);
        jScrollPaneMapMatrix.getVerticalScrollBar().setUnitIncrement(16);

        setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icons/programIcon.png")));
        setLocationRelativeTo(null);

        //Tileset
        Tileset tileset = new Tileset();
        tileset.getSmartGridArray().add(new SmartGrid());

        if (TilesetRendererPolicy.isStartupEnabled()) {
            StartupTrace.log("MainFrame: TilesetRenderer startup enabled");
            TilesetRenderer tr = new TilesetRenderer(tileset);
            try {
                tr.renderTiles();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            StartupTrace.log("MainFrame: skipping TilesetRenderer startup");
        }

        //Border maps tileset
        Tileset borderMapsTileset = new Tileset();

        handler = new MapEditorHandler(this);
        handler.setTileset(tileset);
        handler.setBorderMapsTileset(borderMapsTileset);

        mapDisplay.setHandler(handler);
        tileSelector.init(handler);
        heightSelector.init(handler);
        smartGridDisplay.init(handler, false);
        thumbnailLayerSelector.init(handler);
        updateViewGame();
        tileDisplay.setHandler(handler);
        tileDisplay.setWireframe(true);
        mapMatrixDisplay.init(handler);
        moveMapPanel.init(handler);

        setTitle(handler.getVersionName());

        handler.updateAllMapThumbnails();
        mapMatrixDisplay.updateMapsImage();

        jlResortStatus = new JLabel("RTPKS: (none)");
        jpStatusBar.add(jlResortStatus);
        resortController = new ResortController(this, handler);
        resortController.installMenu(getJMenuBar());
        updateResortStatus();

        applyInitialSplitAndMinSize();

        StartupTrace.log("MainFrame.<init>: exit");
    }

    private void applyInitialSplitAndMinSize() {
        try {
            setMinimumSize(new Dimension(640, 480));
            jspMainWindow.setDividerLocation(0.72);
        } catch (Throwable t) {
            StartupTrace.detail("applyInitialSplitAndMinSize: " + t);
        }
    }

    private void formWindowClosing(WindowEvent e) {
        int returnVal = JOptionPane.showConfirmDialog(this,
                "Do you want to exit Pokemon DS Map Studio?",
                "Closing Pokemon DS Map Studio", JOptionPane.YES_NO_OPTION);
        if (returnVal == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    private void jmiNewMapActionPerformed() {
        newMap();
    }

    private void jmiOpenMapActionPerformed(ActionEvent e) {
        openMapWithDialog();
    }

    private void jmiSaveMapActionPerformed(ActionEvent e) {
        if (!hasPersistableMapPath()) {
            saveMapWithDialog();
        } else {
            saveMap();
        }
    }

    private void jmiSaveMapAsActionPerformed(ActionEvent e) {
        saveMapWithDialog();
    }

    private void jmiSaveRbmapActionPerformed(ActionEvent e) {
        if (resortController != null) {
            resortController.saveAsRbmap();
        }
    }

    public void setTitleFromHandler() {
        String mapPath = handler.getMapMatrix().filePath;
        if (mapPath != null && mapPath.toLowerCase(java.util.Locale.ROOT).endsWith(".rbmap")) {
            String name = Utils.removeExtensionFromPath(new java.io.File(mapPath).getName());
            setTitle(name + " - " + handler.getVersionName());
        } else if (!handler.getMapMatrix().filePath.isEmpty()) {
            setTitle(handler.getMapName() + " - " + handler.getVersionName());
        }
    }

    private void jmiAddMapsActionPerformed(ActionEvent e) {
        addMapWithDialog();
    }

    private void jmiExportObjWithTextActionPerformed(ActionEvent e) {
        saveMapAsObjWithDialog(true);
    }

    private void jmiExportMapAsImdActionPerformed(ActionEvent e) {
        saveMapAsImdWithDialog();
    }

    private void jmiExportMapAsNsbActionPerformed(ActionEvent e) {
        saveMapAsNsbWithDialog();
    }

    private void jmiExportMapBtxActionPerformed(ActionEvent e) {
        saveMapBtxWithDialog();
    }

    private void jmiImportTilesetActionPerformed(ActionEvent e) {
        openTilesetWithDialog();
    }

    private void jmiExportTilesetActionPerformed(ActionEvent e) {
        saveTilesetWithDialog();
    }

    private void jmiExportAllTilesActionPerformed(ActionEvent e) {
        saveAllTilesAsObjWithDialog();
    }

    private void jmiDumpTilesetAsPngsActionPerformed(ActionEvent e) {
        dumpTilesetAsPngsWithDialog();
    }

    private void jmiUndoActionPerformed(ActionEvent e) {
        undoMapState();
    }

    private void jmiRedoActionPerformed(ActionEvent e) {
        redoMapState();
    }

    private void jmiClearLayerActionPerformed(ActionEvent e) {
        handler.clearLayer(handler.getActiveLayerIndex());
    }

    private void jmiClearAllLayersActionPerformed(ActionEvent e) {
        handler.getGrid().clearAllLayers();
        thumbnailLayerSelector.drawAllLayerThumbnails();
        thumbnailLayerSelector.repaint();
        mapDisplay.updateMapLayersGL();
        mapDisplay.repaint();
    }

    private void jmiCopyLayerActionPerformed(ActionEvent e) {
        if (handler.getTileset().size() > 0) {
            handler.copySelectedLayer();
        }
    }

    private void jmiPasteLayerActionPerformed(ActionEvent e) {
        handler.pasteLayer(handler.getActiveLayerIndex());
    }

    private void jmiPasteLayerTilesActionPerformed(ActionEvent e) {
        handler.pasteLayerTiles(handler.getActiveLayerIndex());
    }

    private void jmiPasteLayerHeightsActionPerformed(ActionEvent e) {
        handler.pasteLayerHeights(handler.getActiveLayerIndex());
    }

    private void jmi3dViewActionPerformed(ActionEvent e) {
        mapDisplay.set3DView();
        mapDisplay.repaint();
    }

    private void jmiTopViewActionPerformed(ActionEvent e) {
        mapDisplay.setOrthoView();
        mapDisplay.repaint();
    }

    private void jmiHeightViewActionPerformed(ActionEvent e) {
        mapDisplay.setHeightView();
        mapDisplay.repaint();
    }

    private void jmiToggleGridActionPerformed(ActionEvent e) {
        mapDisplay.toggleGridView();
        mapDisplay.repaint();
    }

    private void jmiLoadBackImgActionPerformed(ActionEvent e) {
        openBackImgWithDialog();
    }

    private void jcbUseBackImageActionPerformed(ActionEvent e) {
        mapDisplay.setBackImageEnabled(jcbUseBackImage.isSelected());
        mapDisplay.repaint();
    }

    private void jmiTilesetEditorActionPerformed(ActionEvent e) {
        openTilesetEditor();
    }

    private void jmiCollisionEditorActionPerformed(ActionEvent e) {
        openCollisionsEditor();
    }

    private void jmiBdhcEditorActionPerformed(ActionEvent e) {
        openBdhcEditor();
    }

    private void jmiBDHCAMActionPerformed(ActionEvent e) {openBdhcamEditor(); }

    private void jmiNsbtxEditorActionPerformed(ActionEvent e) {
        openNsbtxEditor();
    }

    private void jMenuItem1ActionPerformed(ActionEvent e) {
        openBuildingEditor2();
    }

    private void jmiAnimationEditorActionPerformed(ActionEvent e) {
        openAnimationEditor();
    }

    private void jmiKeyboardInfoActionPerformed(ActionEvent e) {
        openKeyboardInfoDialog();
    }

    private void jmiAboutActionPerformed(ActionEvent e) {
        openAboutDialog();
    }

    private void jbNewMapActionPerformed(ActionEvent e) {
        newMap();
    }

    private void jbOpenMapActionPerformed(ActionEvent e) {
        openMapWithDialog();
    }

    private void jbSaveMapActionPerformed(ActionEvent e) {
        if (!hasPersistableMapPath()) {
            saveMapWithDialog();
        } else {
            saveMap();
        }
    }

    private void jbAddMapsActionPerformed(ActionEvent e) {
        addMapWithDialog();
    }

    private void jbExportObjActionPerformed(ActionEvent e) {
        saveMapAsObjWithDialog(true);
    }

    private void jbExportImdActionPerformed(ActionEvent e) {
        saveMapsAsImdWithDialog();
    }

    private void jbExportNsbActionPerformed(ActionEvent e) {
        saveMapsAsNsbWithDialog();
    }

    private void jbExportBinActionPerformed(ActionEvent e) {saveMapAsBinWithDialog();}

    private void jbExportNsb1ActionPerformed(ActionEvent e) {
        saveMapBtxWithDialog();
    }

    private void jbExportNsb2ActionPerformed(ActionEvent e) {
        saveAreasAsBtxWithDialog();
    }

    private void jbUndoActionPerformed(ActionEvent e) {
        undoMapState();
    }

    private void jbRedoActionPerformed(ActionEvent e) {
        redoMapState();
    }

    private void jbTilelistEditorActionPerformed(ActionEvent e) {
        openTilesetEditor();
    }

    private void jbCollisionsEditorActionPerformed(ActionEvent e) {
        openCollisionsEditor();
    }

    private void jbBdhcEditorActionPerformed(ActionEvent e) {
        openBdhcEditor();
    }

    private void jbBacksoundEditorActionPerformed(ActionEvent e) {
        openBacksoundEditor();
    }

    private void jbBdhcamEditorActionPerformed(ActionEvent e) {openBdhcamEditor(); }

    private void jbNsbtxEditor1ActionPerformed(ActionEvent e) {
        openNsbtxEditor2();
    }

    private void jbBuildingEditorActionPerformed(ActionEvent e) {
        openBuildingEditor2();
    }

    private void jbAnimationEditorActionPerformed(ActionEvent e) {
        openAnimationEditor();
    }

    private void jbKeboardInfoActionPerformed(ActionEvent e) {
        openKeyboardInfoDialog();
    }

    private void jbHelpActionPerformed(ActionEvent e) {
        openAboutDialog();
    }

    private void tileSelectorMousePressed(MouseEvent e) {
        repaintTileDisplay();
    }

    private void jlGameIconMousePressed(MouseEvent e) {
        changeGame();
    }



    private void mapDisplayContainerComponentResized(ComponentEvent e) {
        traceMapLayout("before updateMapDisplaySize");
        updateMapDisplaySize();
        traceMapLayout("after updateMapDisplaySize");
    }

    private void traceMapLayout(String where) {
        if (!Boolean.getBoolean("pdsm.traceLayout")) {
            return;
        }
        if (mapDisplayContainer == null || mapDisplay == null || jspMainWindow == null) {
            return;
        }
        System.out.println("[PDSM] layout " + where
                + " container.size=" + mapDisplayContainer.getSize()
                + " container.pref=" + mapDisplayContainer.getPreferredSize()
                + " map.size=" + mapDisplay.getSize()
                + " map.pref=" + mapDisplay.getPreferredSize()
                + " split.divider=" + jspMainWindow.getDividerLocation());
    }

    private void jtbView3DActionPerformed(ActionEvent e) {
        mapDisplay.set3DView();
        mapDisplay.repaint();
    }

    private void jtbViewOrthoActionPerformed(ActionEvent e) {
        mapDisplay.setOrthoView();
        mapDisplay.repaint();
    }

    private void jtbViewHeightActionPerformed(ActionEvent e) {
        mapDisplay.setHeightView();
        mapDisplay.repaint();
    }

    private void jtbViewGridActionPerformed(ActionEvent e) {
        mapDisplay.setGridEnabled(jtbViewGrid.isSelected());
        mapDisplay.repaint();
    }

    private void jtbViewWireframeActionPerformed(ActionEvent e) {
        mapDisplay.setDrawWireframeEnabled(jtbViewWireframe.isSelected());
        mapDisplay.repaint();
    }

    private void jtbModeEditActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_EDIT);
    }

    private void jtbModeClearActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_CLEAR);
    }

    private void jtbModeSmartPaintActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_SMART_PAINT);
    }

    private void jtbModeInvSmartPaintActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_INV_SMART_PAINT);
    }

    private void jtbModeMoveActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_MOVE);
    }

    private void jtbModeZoomActionPerformed(ActionEvent e) {
        mapDisplay.setEditMode(MapDisplay.EditMode.MODE_ZOOM);
    }

    private void jbFitCameraToMapActionPerformed(ActionEvent e) {
        mapDisplay.setCameraAtSelectedMap();
        mapDisplay.repaint();
    }

    private void jsSelectedAreaStateChanged(ChangeEvent e) {
        try {
            handler.getMapData().setAreaIndex((Integer) jsSelectedArea.getValue());
            handler.getMapMatrix().updateBordersData();
            mapMatrixDisplay.updateMapsImage();
            mapMatrixDisplay.repaint();
            mapDisplay.repaint();

            jPanelAreaColor.setBackground(handler.getMapMatrix().getAreaColors().get(handler.getMapData().getAreaIndex()));
            jPanelAreaColor.repaint();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jsHeightMapAlphaStateChanged(ChangeEvent e) {
        mapDisplay.setHeightMapAlpha(jsHeightMapAlpha.getValue() / 100f);
        mapDisplay.repaint();
    }

    private void jsBackImageAlphaStateChanged(ChangeEvent e) {
        mapDisplay.setBackImageAlpha(jsBackImageAlpha.getValue() / 100f);
        mapDisplay.repaint();
    }

    private void jcbRealTimePolyGroupingActionPerformed(ActionEvent e) {
        handler.setRealTimePostProcessing(jcbRealTimePolyGrouping.isSelected());
        handler.getMapMatrix().updateAllLayersGL();
        mapDisplay.repaint();
        updateViewGeometryCount();
    }

    private void jcbViewAreasActionPerformed(ActionEvent e) {
        mapDisplay.setDrawAreasEnabled(jcbViewAreas.isSelected());
        mapDisplay.repaint();
    }

    private void jbMoveMapUpActionPerformed(ActionEvent e) {
        moveTilesUp();
    }

    private void jbMoveMapLeftActionPerformed(ActionEvent e) {
        moveTilesLeft();
    }

    private void jbMoveMapRightActionPerformed(ActionEvent e) {
        moveTilesRight();
    }

    private void jbMoveMapDownActionPerformed(ActionEvent e) {
        moveTilesDown();
    }

    private void jbMoveMapUpZActionPerformed(ActionEvent e) {
        moveTilesUpZ();
    }

    private void jbMoveMapDownZActionPerformed(ActionEvent e) {
        moveTilesDownZ();
    }

    private void jcbViewGridsBordersActionPerformed(ActionEvent e) {
        mapDisplay.setDrawGridBorderMaps(jcbViewGridsBorders.isSelected());
        mapDisplay.repaint();
    }

    private void jmiNewMapActionPerformed(ActionEvent e) {
        newMap();
    }

    private void menuItem1ActionPerformed(ActionEvent e) {
        showPreferences();
    }

    private void jbSettingsActionPerformed(ActionEvent e) {
        showPreferences();
    }

    private void jmiClearHistoryActionPerformed(ActionEvent e) {
        clearRecentMaps();
    }

    private void jbHelp2ActionPerformed(ActionEvent e) {
        CollisionsEditorDialogBW dialog = new CollisionsEditorDialogBW(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        mapDisplay.requestUpdate();
        mapDisplay.repaint();
    }

    public void showPreferences() {
        SettingsDialog settingsDialog = new SettingsDialog(this);
        settingsDialog.setVisible(true);
    }

    public void openMap(String path) {
        try {
            String folderPath = new File(path).getParent();
            String fileName = new File(path).getName();
            handler.setLastMapDirectoryUsed(folderPath);

            handler.getMapMatrix().loadGridsFromFile(path);
            handler.getMapMatrix().filePath = path;
            handler.setDefaultMapSelected();

            setTitle(handler.getMapName() + " - " + handler.getVersionName());

            handler.resetMapStateHandler();
            jbUndo.setEnabled(false);
            jbRedo.setEnabled(false);

            try {
                Tileset tileset = TilesetIO.readTilesetFromFile(handler.getMapMatrix().tilesetFilePath);
                handler.setTileset(tileset);
                System.out.println("Textures loaded from path: " + new File(path).getParent());

                ensureTilesetThumbnails();

                handler.setIndexTileSelected(0);
                handler.setSmartGridIndexSelected(0);

                handler.getMapMatrix().updateAllLayersGL();
                handler.getMapMatrix().updateBordersData();
                handler.updateAllMapThumbnails();
                mapMatrixDisplay.updateSize();
                updateMapMatrixDisplay();
                updateViewMapInfo();

                tileSelector.updateLayout();
                tileSelector.repaint();
                mapDisplay.requestUpdate();
                mapDisplay.setCameraAtSelectedMap();
                mapDisplay.repaint();
                tileDisplay.requestUpdate();
                tileDisplay.repaint();

                smartGridDisplay.updateSize();
                smartGridDisplay.repaint();
                thumbnailLayerSelector.drawAllLayerThumbnails();
                thumbnailLayerSelector.repaint();
            } catch (IOException | TextureNotFoundException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error opening map", JOptionPane.ERROR_MESSAGE);
            }

            handler.getMapMatrix().loadBDHCsFromFile(folderPath, fileName);
            handler.getMapMatrix().loadBdhcamsFromFile(folderPath, fileName);
            handler.getMapMatrix().loadBacksoundsFromFile(folderPath, fileName);
            handler.getMapMatrix().loadCollisionsFromFile(folderPath, fileName);
            handler.getMapMatrix().loadBuildingsFromFile(folderPath, fileName);

            updateViewGame();

            repaintHeightSelector();
            repaintTileSelector();
            repaintMapDisplay();

            opened_map = true;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Can't open file", "Error opening map", JOptionPane.ERROR_MESSAGE);
        }
    }

    public RbmapGridImporter.ImportResult openRbmap(Path rbmapPath,
                                                    RbmapDocument document,
                                                    RtpksReader.LoadResult rtpks) throws Exception {
        String folderPath = rbmapPath.getParent() != null
                ? rbmapPath.getParent().toString()
                : "";
        handler.setLastMapDirectoryUsed(folderPath);

        handler.setTileset(rtpks.tileset);
        handler.getMapMatrix().tilesetFilePath = rtpks.rtpksPath.toString();
        handler.setLastTilesetDirectoryUsed(
                rtpks.rtpksPath.getParent() != null ? rtpks.rtpksPath.getParent().toString() : "");
        handler.setResortTilesetBinding(new ResortTilesetBinding(
                rtpks.rtpksPath, rtpks.manifest, rtpks.tileIndex));

        if (document.editorConfig != null) {
            handler.setResortMapMetadata(document.editorConfig);
        }

        // Thumbnails must exist before setMapSelected / updateAllMapThumbnails (they draw layer previews).
        ensureTilesetThumbnails();

        Point mapCoord = new Point(0, 0);
        if (document.bake != null && document.bake.mapCoordinate != null
                && document.bake.mapCoordinate.length >= 2) {
            mapCoord = new Point(document.bake.mapCoordinate[0], document.bake.mapCoordinate[1]);
        }

        handler.getMapMatrix().getMatrix().clear();
        MapData mapData = handler.getMapMatrix().getMapAndCreate(mapCoord);
        RbmapGridImporter.ImportResult importResult =
                RbmapGridImporter.apply(mapData.getGrid(), document, rtpks.tileset);

        handler.getMapMatrix().filePath = rbmapPath.toString();

        String titleName = document.displayName != null && !document.displayName.isEmpty()
                ? document.displayName
                : document.mapId != null ? document.mapId
                : Utils.removeExtensionFromPath(rbmapPath.getFileName().toString());
        setTitle(titleName + " - " + handler.getVersionName());

        handler.resetMapStateHandler();
        jbUndo.setEnabled(false);
        jbRedo.setEnabled(false);
        handler.setIndexTileSelected(0);
        handler.setSmartGridIndexSelected(0);

        handler.getMapMatrix().updateAllLayersGL();
        handler.getMapMatrix().updateBordersData();
        handler.setMapSelected(mapCoord);
        handler.updateAllMapThumbnails();
        mapMatrixDisplay.updateSize();
        updateMapMatrixDisplay();
        updateViewMapInfo();

        tileSelector.updateLayout();
        tileSelector.repaint();
        mapDisplay.requestUpdate();
        mapDisplay.setCameraAtSelectedMap();
        mapDisplay.repaint();
        tileDisplay.requestUpdate();
        tileDisplay.repaint();

        smartGridDisplay.updateSize();
        smartGridDisplay.repaint();
        thumbnailLayerSelector.drawAllLayerThumbnails();
        thumbnailLayerSelector.repaint();

        repaintHeightSelector();
        repaintTileSelector();
        repaintMapDisplay();
        updateResortStatus();

        opened_map = true;
        return importResult;
    }

    private JFileChooser createProjectFileChooser(String purpose) {
        JFileChooser fc = new JFileChooser();

        fc.setFileHidingEnabled(false);
        fc.setAcceptAllFileFilterUsed(true);

        System.out.println("[PDSM] JFileChooser " + purpose
                + " cwd=" + fc.getCurrentDirectory()
                + " fileSelectionMode=" + fc.getFileSelectionMode()
                + " acceptAll=" + fc.isAcceptAllFileFilterUsed()
                + " hiding=" + fc.isFileHidingEnabled());

        return fc;
    }

    public void openMapWithDialog() {
        System.setProperty("apple.awt.fileDialogForDirectories", "false");

        String osName = System.getProperty("os.name", "");
        boolean mac = osName.contains("Mac") || osName.contains("Darwin");
        if (mac && Boolean.getBoolean("pdsm.useNativeFileDialog")) {
            String path = openMapWithNativeFileDialog();
            if (path != null) {
                path = Utils.addExtensionToPath(path, MapMatrix.fileExtension);
                addRecentMap(path);
                updateRecentMaps();
                updateRecentMapsMenu();
                openMap(path);
            }
            return;
        }

        final JFileChooser fc = new JFileChooser();

        if (handler.getLastMapDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        } else {
            fc.setCurrentDirectory(new File(System.getProperty("user.home"), "Downloads"));
        }

        fc.setFileHidingEnabled(false);
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Pokemon DS map (*.pdsmap)",
                MapMatrix.fileExtension));
        fc.setApproveButtonText("Open");
        fc.setDialogTitle("Open Map");

        File cwd = fc.getCurrentDirectory();
        StartupTrace.log("openMapWithDialog: cwd=" + cwd
                + " exists=" + cwd.exists()
                + " readable=" + cwd.canRead()
                + " selectionMode=" + fc.getFileSelectionMode()
                + " hiding=" + fc.isFileHidingEnabled()
                + " acceptAll=" + fc.isAcceptAllFileFilterUsed()
                + " filter=" + fc.getFileFilter());

        File[] cwdItems = cwd.listFiles();
        StartupTrace.log("openMapWithDialog: cwd item count="
                + (cwdItems == null ? "null" : cwdItems.length));
        if (cwdItems != null) {
            Arrays.sort(cwdItems, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (int i = 0; i < Math.min(cwdItems.length, 50); i++) {
                File f = cwdItems[i];
                StartupTrace.log("openMapWithDialog: cwd item "
                        + (f.isDirectory() ? "[dir] " : "[file] ")
                        + f.getName());
            }
        }

        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            StartupTrace.log("openMapWithDialog: selected=" + fc.getSelectedFile());
            File selected = fc.getSelectedFile();
            if (selected == null) {
                return;
            }

            if (selected.isDirectory()) {
                fc.setCurrentDirectory(selected);
                return;
            }

            String path = Utils.addExtensionToPath(selected.getPath(), MapMatrix.fileExtension);
            addRecentMap(path);
            updateRecentMaps();
            updateRecentMapsMenu();
            openMap(path);
        }
    }

    private String openMapWithNativeFileDialog() {
        FileDialog dialog = new FileDialog(this, "Open Map", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith("." + MapMatrix.fileExtension));

        if (handler.getLastMapDirectoryUsed() != null) {
            dialog.setDirectory(handler.getLastMapDirectoryUsed());
        } else {
            dialog.setDirectory(new File(System.getProperty("user.home"), "Downloads").getAbsolutePath());
        }

        dialog.setVisible(true);

        String file = dialog.getFile();
        String dir = dialog.getDirectory();

        StartupTrace.log("openMapWithNativeFileDialog: dir=" + dir + " file=" + file);

        if (file == null || dir == null) {
            return null;
        }

        return new File(dir, file).getAbsolutePath();
    }

    public void addMapWithDialog() {
        final JFileChooser fc = createProjectFileChooser("Add Maps from PDSMAP file");
        if (handler.getLastMapDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        }

        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Pokemon DS map (*.pdsmap)",
                MapMatrix.fileExtension));
        fc.setApproveButtonText("Open");
        fc.setDialogTitle("Add Maps from PDSMAP file");
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            if (selected == null) {
                return;
            }
            if (selected.isDirectory()) {
                fc.setCurrentDirectory(selected);
                return;
            }
            if (selected.exists()) {
                handler.setLastMapDirectoryUsed(selected.getParent());
                try {
                    HashMap<Point, MapData> maps = MapMatrix.getGridsFromFile(selected.getPath(), handler);

                    final MapMatrixImportDialog dialog = new MapMatrixImportDialog(this);
                    dialog.init(handler, selected.getPath(), maps);
                    dialog.setLocationRelativeTo(this);
                    dialog.setVisible(true);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "There was a problem importing the maps",
                            "Can't add maps", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

    }

    public void openTilesetEditor() {
        final TilesetEditorDialog dialog = new TilesetEditorDialog(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (handler.getTileset().size() > 0) {
            handler.getTileset().removeUnusedTextures();
            dialog.fixIndices();
            tileSelector.updateLayout();
            handler.getMapMatrix().updateAllLayersGL();
            handler.getMapMatrix().updateBordersData();
            handler.updateAllMapThumbnails();
            mapMatrixDisplay.updateSize();
            updateMapMatrixDisplay();
            mapDisplay.requestUpdate();
            mapDisplay.repaint();
            tileDisplay.requestUpdate();
            tileDisplay.repaint();
            smartGridDisplay.updateSize();
            smartGridDisplay.repaint();
            thumbnailLayerSelector.drawAllLayerThumbnails();
            thumbnailLayerSelector.repaint();
        }

        repaint();
    }

    public void openCollisionsEditor() {
        boolean gridEnabled = mapDisplay.isGridEnabled();
        BufferedImage mapBackground = captureOrthoMapBackgroundForEditor();
        final CollisionsEditorDialog dialog = new CollisionsEditorDialog(this);
        dialog.init(handler, mapBackground);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        mapDisplay.setGridEnabled(gridEnabled);
        mapDisplay.display();
    }

    public void openBdhcEditor() {
        if(Game.isGenV(handler.getGameIndex())){
            CollisionsEditorDialogBW dialog = new CollisionsEditorDialogBW(this);
            dialog.init(handler);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);

            mapDisplay.requestUpdate();
            mapDisplay.repaint();
        }else {
            boolean useGrid = mapDisplay.isGridEnabled();
            BufferedImage mapBackground = captureOrthoMapBackgroundForEditor();
            final BdhcEditorDialog dialog = new BdhcEditorDialog(this);
            dialog.init(handler, mapBackground);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            mapDisplay.setGridEnabled(useGrid);
            mapDisplay.requestUpdate();
            mapDisplay.display();
        }
    }

    private BufferedImage captureOrthoMapBackgroundForEditor() {
        mapDisplay.requestScreenshot();
        mapDisplay.setOrthoView();
        mapDisplay.setCameraAtSelectedMap();
        mapDisplay.disableGridView();
        mapDisplay.updateMapLayersGL();
        mapDisplay.display();
        return mapDisplay.getScreenshot();
    }

    public void openBacksoundEditor() {
        if (handler.getGame().gameSelected == Game.HEART_GOLD || handler.getGame().gameSelected == Game.SOUL_SILVER) {
            boolean useGrid = mapDisplay.isGridEnabled();
            BufferedImage mapBackground = captureOrthoMapBackgroundForEditor();
            final BacksoundEditorDialog dialog = new BacksoundEditorDialog(this);
            dialog.init(handler, mapBackground);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            mapDisplay.setGridEnabled(useGrid);
            mapDisplay.display();
        } else {
            JOptionPane.showMessageDialog(this, "Only HGSS have Backsound files",
                    "Backsound Editor not available", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void openBdhcamEditor(){
        if (handler.getGame().gameSelected > Game.PEARL && handler.getGame().gameSelected < Game.BLACK) {
            boolean useGrid = mapDisplay.isGridEnabled();
            BufferedImage mapBackground = captureOrthoMapBackgroundForEditor();
            final BdhcamEditorDialog dialog = new BdhcamEditorDialog(this);
            dialog.init(handler, mapBackground);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            mapDisplay.setGridEnabled(useGrid);
            mapDisplay.requestUpdate();
            mapDisplay.repaint();

        } else {
            JOptionPane.showMessageDialog(this, "Only Platinum and HGSS have BDCAM files available",
                    "BDHCAM editor is not available", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void openNsbtxEditor() {
        final NsbtxEditorDialog dialog = new NsbtxEditorDialog(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void openNsbtxEditor2() {
        final NsbtxEditorDialog2 dialog = new NsbtxEditorDialog2(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void openBuildingEditor2() {
        BuildingEditorChooser.loadGame(handler);
    }

    public void openAnimationEditor() {
        final AnimationEditorDialog dialog = new AnimationEditorDialog(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void openKeyboardInfoDialog() {
        final KeyboardInfoDialog2 dialog = new KeyboardInfoDialog2(this);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void openTileset(String path) {
        String folderPath = new File(path).getParent();

        handler.setLastTilesetDirectoryUsed(folderPath);
        try {
            Tileset tileset = TilesetIO.readTilesetFromFile(path);
            handler.getMapMatrix().tilesetFilePath = path;
            handler.setTileset(tileset);
            if (!path.toLowerCase().endsWith(".rtpks")) {
                if (resortController != null) {
                    resortController.onTilesetOpenedFromPlainPdsts();
                } else {
                    handler.clearResortTilesetBinding();
                }
            }
            System.out.println("Textures loaded from path: " + new File(path).getParent());

            refreshEditorAfterTilesetChange();
        } catch (TextureNotFoundException | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error opening tilset", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshEditorAfterTilesetChange() {
        ensureTilesetThumbnails();

        handler.setIndexTileSelected(0);
        handler.setSmartGridIndexSelected(0);

        tileSelector.updateLayout();
        tileSelector.repaint();
        smartGridDisplay.updateSize();
        smartGridDisplay.repaint();
        mapDisplay.requestUpdate();
        mapDisplay.repaint();
        tileDisplay.requestUpdate();
        tileDisplay.repaint();
        thumbnailLayerSelector.drawAllLayerThumbnails();
        thumbnailLayerSelector.repaint();

        repaintHeightSelector();
        repaintTileSelector();
        repaintMapDisplay();
        updateResortStatus();
    }

    public java.awt.image.BufferedImage getTilesetThumbnailImage() {
        return tileSelector.getTilesetImage();
    }

    public void updateResortStatus() {
        if (jlResortStatus != null && resortController != null) {
            jlResortStatus.setText(resortController.getStatusText());
        }
    }

    public void openTilesetWithDialog() {
        final JFileChooser fc = createProjectFileChooser("Open Tileset");
        if (handler.getLastTilesetDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastTilesetDirectoryUsed()));
        }
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Pokemon DS Tileset (*.pdsts)",
                Tileset.fileExtension));
        fc.setApproveButtonText("Open");
        fc.setDialogTitle("Open");
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            if (selected == null || selected.isDirectory()) {
                if (selected != null) {
                    fc.setCurrentDirectory(selected);
                }
                return;
            }
            String path = selected.getPath();
            openTileset(path);
        }
    }

    private void openBackImgWithDialog() {
        final JFileChooser fc = createProjectFileChooser("Open Background Image");
        if (handler.getLastMapDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        }
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new DirectoryFriendlyExtensionFilter("PNG (*.png)", "png"));
        fc.setApproveButtonText("Open");
        fc.setDialogTitle("Open Background Image");
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            if (selected == null || selected.isDirectory()) {
                if (selected != null) {
                    fc.setCurrentDirectory(selected);
                }
                return;
            }
            try {
                BufferedImage img = ImageIO.read(selected);

                mapDisplay.setBackImage(img);
                mapDisplay.setBackImageEnabled(true);
                jcbUseBackImage.setSelected(true); //Redundant

                mapDisplay.repaint();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Can't open file", "Error opening image", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void newMap() {
        int returnVal = JOptionPane.showConfirmDialog(this, "Do you want to close the current map?", "Create new map", JOptionPane.YES_NO_OPTION);
        if (returnVal == JOptionPane.YES_OPTION) {
            final GameTsetSelectorDialog2 dialog = new GameTsetSelectorDialog2(this);
            dialog.init(handler);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);

            if (dialog.getReturnValue() == GameTsetSelectorDialog2.ACCEPTED) {
                handler.setIndexTileSelected(0);
                handler.setSmartGridIndexSelected(0);

                handler.setMapMatrix(new MapMatrix(handler));
                handler.setMapSelected(new Point(0, 0));

                /*
                handler.setCollisions(new Collisions(handler.getGameIndex()));
                handler.setBdhc(new Bdhc());
                handler.setBacksound(new Backsound());
                handler.setBuildings(new BuildFile());
                handler.setGrid(new MapGrid(handler));*/
                handler.resetMapStateHandler();
                jbUndo.setEnabled(false);
                jbRedo.setEnabled(false);

                //handler.setTileset(new Tileset());
                //handler.getSmartGridArray().add(new SmartGrid());
                tileSelector.updateLayout();
                tileSelector.repaint();

                smartGridDisplay.updateSize();
                smartGridDisplay.repaint();

                mapDisplay.requestUpdate();
                mapDisplay.setCameraAtSelectedMap();
                repaintMapDisplay();
                tileDisplay.requestUpdate();
                tileDisplay.repaint();
                thumbnailLayerSelector.drawAllLayerThumbnails();
                thumbnailLayerSelector.repaint();

                handler.updateAllMapThumbnails();
                mapMatrixDisplay.updateSize();
                updateMapMatrixDisplay();

                updateViewGame();

                setTitle(handler.getVersionName());
            }
        }
    }

    private boolean hasPersistableMapPath() {
        String filePath = handler.getMapMatrix().filePath;
        return filePath != null
                && !filePath.isEmpty()
                && filePath.toLowerCase(Locale.ROOT).endsWith("." + MapMatrix.fileExtension);
    }

    private File defaultMapSaveFile() {
        String directory = handler.getLastMapDirectoryUsed();
        String filePath = handler.getMapMatrix().filePath;
        String baseName = "untitled";
        if (filePath != null && !filePath.isEmpty()) {
            File source = new File(filePath);
            if (source.getParent() != null) {
                directory = source.getParent();
            }
            baseName = Utils.removeExtensionFromPath(source.getName());
        }
        if (directory == null || directory.isEmpty()) {
            directory = System.getProperty("user.home");
        }
        return new File(directory, baseName + "." + MapMatrix.fileExtension);
    }

    private void saveMap() {
        try {
            handler.getMapMatrix().saveGridsToFile(handler.getMapMatrix().filePath);

            setTitle(handler.getMapName() + " - " + handler.getVersionName());

            saveTileset();
            handler.getMapMatrix().saveCollisions();
            handler.getMapMatrix().saveBacksounds();
            handler.getMapMatrix().saveBDHCs();
            handler.getMapMatrix().saveBdhcams();
            handler.getMapMatrix().saveBuildings();

            //handler.getMapMatrix().saveBinaryMaps();
            //saveBdhc();
            //saveBacksound();
            //saveCollisions();
            //saveBuildings();

            saveMapThumbnail();
        } catch (ParserConfigurationException | TransformerException | IOException ex) {
            JOptionPane.showMessageDialog(this, "There was a problem saving all the map files",
                    "Error saving map files", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveMapWithDialog() {
        final JFileChooser fc = createProjectFileChooser("Save Map");
        File defaultFile = defaultMapSaveFile();
        if (defaultFile.getParentFile() != null) {
            fc.setCurrentDirectory(defaultFile.getParentFile());
        } else if (handler.getLastMapDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        }
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                "Pokemon DS map (*.pdsmap)",
                MapMatrix.fileExtension));
        fc.setSelectedFile(defaultFile);
        fc.setApproveButtonText("Save");
        fc.setDialogTitle("Save Map");
        int returnVal = fc.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            if (selected == null || selected.isDirectory()) {
                if (selected != null) {
                    fc.setCurrentDirectory(selected);
                }
                return;
            }
            handler.setLastMapDirectoryUsed(selected.getParent());
            try {
                String path = Utils.addExtensionToPath(selected.getPath(), MapMatrix.fileExtension);
                handler.getMapMatrix().saveGridsToFile(path);
                handler.getMapMatrix().filePath = path;
                setTitle(handler.getMapName() + " - " + handler.getVersionName());

                saveTileset();

                handler.getMapMatrix().saveCollisions();
                handler.getMapMatrix().saveBacksounds();
                handler.getMapMatrix().saveBDHCs();
                handler.getMapMatrix().saveBdhcams();
                handler.getMapMatrix().saveBuildings();

                //handler.getMapMatrix().saveBinaryMaps();
                //saveCollisions();
                //saveBacksound();
                //saveBdhc();
                //saveBuildings();

                saveMapThumbnail();

                addRecentMap(Utils.addExtensionToPath(path, MapMatrix.fileExtension));
                updateRecentMaps();
                updateRecentMapsMenu();
            } catch (ParserConfigurationException | TransformerException | IOException ex) {
                JOptionPane.showMessageDialog(this, "There was a problem saving all the map files",
                        "Error saving map files", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveTilesetWithDialog() {
        if (handler.getTileset().size() > 0) {
            final JFileChooser fc = createProjectFileChooser("Save Tileset");
            if (handler.getLastTilesetDirectoryUsed() != null) {
                fc.setCurrentDirectory(new File(handler.getLastTilesetDirectoryUsed()));
            }
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileFilter(new DirectoryFriendlyExtensionFilter(
                    "Pokemon DS tileset (*.pdsts)",
                    Tileset.fileExtension));
            fc.setApproveButtonText("Save");
            fc.setDialogTitle("Save Tileset");
            int returnVal = fc.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                if (file == null || file.isDirectory()) {
                    if (file != null) {
                        fc.setCurrentDirectory(file);
                    }
                    return;
                }
                handler.setLastMapDirectoryUsed(file.getParent());
                try {
                    String path = file.getParent();
                    String filename = Utils.removeExtensionFromPath(file.getName()) + "." + Tileset.fileExtension;
                    TilesetIO.saveTilesetToFile(path + File.separator + filename, handler.getTileset());
                    handler.getTileset().saveImagesToFile(path);

                    saveTilesetThumbnail(path + File.separator + "TilesetThumbnail.png");

                    JOptionPane.showMessageDialog(this, "Tileset succesfully exported.", "Tileset saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Can't save file", "Error saving tileset", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "The tileset is empty", "Error saving tileset", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void dumpTilesetAsPngsWithDialog() {
        Tileset tileset = handler.getTileset();
        if (tileset.size() == 0) {
            JOptionPane.showMessageDialog(this, "The tileset is empty", "Error exporting tiles", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final JFileChooser fc = new JFileChooser();
        fc.setFileHidingEnabled(false);
        fc.setAcceptAllFileFilterUsed(true);
        if (handler.getLastTileObjDirectoryUsed() != null) {
            fc.setCurrentDirectory(new File(handler.getLastTileObjDirectoryUsed()));
        }
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setApproveButtonText("Select");
        fc.setDialogTitle("Select folder for tile PNG export");
        int returnVal = fc.showOpenDialog(this);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File folder = fc.getSelectedFile();
        handler.setLastTileObjDirectoryUsed(folder.getPath());

        try {
            exportTilesetAsPngs(tileset, folder);
            JOptionPane.showMessageDialog(this,
                    "Exported " + tileset.size() + " tile images to:\n" + folder.getPath(),
                    "Tiles exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Can't save tile images", "Error exporting tiles", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportTilesetAsPngs(Tileset tileset, File folder) throws IOException {
        ArrayList<String> usedNames = new ArrayList<>();
        TilesetRenderer renderer = null;
        GLContext previousContext = mapDisplay.getContext();

        try {
            renderer = new TilesetRenderer(tileset);
            if (!renderer.isInitialized()) {
                renderer.destroy();
                renderer = null;
            }
            for (int i = 0; i < tileset.size(); i++) {
                Tile tile = tileset.get(i);
                BufferedImage img = null;
                if (renderer != null) {
                    img = renderer.renderTileImage(i, true);
                }
                if (img == null) {
                    img = tile.getThumbnail();
                }
                if (img == null) {
                    throw new IOException("Could not render tile " + i + " (OpenGL unavailable and no thumbnail cached)");
                }
                ImageIO.write(img, "png", new File(folder, uniqueTilePngFilename(i, tile, usedNames)));
            }
        } finally {
            if (renderer != null) {
                renderer.destroy();
                mapDisplay.setContext(previousContext, false);
            }
        }
    }

    private static String uniqueTilePngFilename(int index, Tile tile, ArrayList<String> usedNames) {
        String baseName = tile.getObjFilename();
        if (baseName == null || baseName.isEmpty()) {
            baseName = "tile";
        } else {
            baseName = Utils.removeExtensionFromPath(new File(baseName).getName());
        }
        String filename = String.format(Locale.ROOT, "%03d_%s.png", index, baseName);
        int counter = 1;
        String nameNoExtension = Utils.removeExtensionFromPath(filename);
        while (usedNames.contains(filename)) {
            filename = nameNoExtension + "_" + counter + ".png";
            counter++;
        }
        usedNames.add(filename);
        return filename;
    }

    public void saveAllTilesAsObjWithDialog() {
        if (handler.getTileset().size() > 0) {
            final ExportTileDialog exportTileDialog = new ExportTileDialog(handler.getMainFrame(), "Export Tile Settings");
            exportTileDialog.setLocationRelativeTo(this);
            exportTileDialog.setVisible(true);
            if (exportTileDialog.getReturnValue() == AddTileDialog.APPROVE_OPTION) {
                float scale = exportTileDialog.getScale();
                boolean flip = exportTileDialog.flip();
                boolean includeVertexColors = exportTileDialog.includeVertexColors();

                final JFileChooser fc = new JFileChooser();
                fc.setFileHidingEnabled(false);
                fc.setAcceptAllFileFilterUsed(true);
                if (handler.getLastTileObjDirectoryUsed() != null) {
                    fc.setCurrentDirectory(new File(handler.getLastTileObjDirectoryUsed()));
                }
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setApproveButtonText("Save");
                fc.setDialogTitle("Select folder for saving all tiles as OBJ");
                int returnVal = fc.showOpenDialog(this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    handler.setLastTileObjDirectoryUsed(fc.getSelectedFile().getPath());
                    try {
                        ObjWriter objWriter = new ObjWriter(handler.getTileset(),
                                handler.getGrid(), fc.getSelectedFile().getPath(),
                                handler.getGameIndex(), true, includeVertexColors, 1.0f);
                        objWriter.writeAllTilesObj(scale, flip);
                        JOptionPane.showMessageDialog(this, "Tiles succesfully exported.", "Tiles saved", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Can't save tiles", "Error saving tiles", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "The tileset is empty", "Error saving tiles", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveMapAsObjWithDialog(boolean saveTextures) {
        final ExportMapObjDialog exportMapDialog = new ExportMapObjDialog(this, "Export OBJ Map Settings");
        exportMapDialog.setLocationRelativeTo(this);
        exportMapDialog.setVisible(true);

        if (exportMapDialog.getReturnValue() == ExportMapObjDialog.APPROVE_OPTION) {
            boolean includeVertexColors = exportMapDialog.includeVertexColors();
            boolean exportAllMapsSeparately = exportMapDialog.exportAllMapsSeparately();
            boolean exportAllMapsJoined = exportMapDialog.exportAllMapsJoined();
            float tileUpscale = exportMapDialog.getTileUpscaling();

            final JFileChooser fc = createProjectFileChooser("Save map as OBJ");
            fc.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath)));
            if (handler.getLastMapDirectoryUsed() != null) {
                fc.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
            }
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileFilter(new DirectoryFriendlyExtensionFilter("OBJ (*.obj)", "obj"));
            fc.setApproveButtonText("Save");
            fc.setDialogTitle("Select a name for saving the maps as OBJ");
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File selected = fc.getSelectedFile();
                if (selected == null || selected.isDirectory()) {
                    if (selected != null) {
                        fc.setCurrentDirectory(selected);
                    }
                    return;
                }
                handler.setLastMapDirectoryUsed(selected.getParent());
                try {
                    String path = selected.getPath();
                    if (exportAllMapsSeparately) {
                        path = Utils.removeMapCoordsFromName(path);
                        handler.getMapMatrix().saveMapsAsObj(path, saveTextures, includeVertexColors, tileUpscale);
                        JOptionPane.showMessageDialog(this, "OBJ maps succesfully exported.", "Maps saved", JOptionPane.INFORMATION_MESSAGE);
                    } else if (exportAllMapsJoined) {
                        path = Utils.removeMapCoordsFromName(path);
                        handler.getMapMatrix().saveMapsAsObjJoined(path, saveTextures, includeVertexColors, tileUpscale);
                        JOptionPane.showMessageDialog(this, "OBJ map succesfully exported.", "Map saved", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        handler.getGrid().saveMapToOBJ(handler.getTileset(), path, saveTextures, includeVertexColors, tileUpscale);
                        JOptionPane.showMessageDialog(this, "OBJ map succesfully exported.", "Map saved", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (FileNotFoundException ex) {
                    JOptionPane.showMessageDialog(this, "Can't save file.", "Error saving map", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void saveMapAsBinWithDialog(){
        if(handler.getGame().gameSelected >= Game.BLACK){
            JOptionPane.showMessageDialog(this, "Can't save Gen V binary files yet", "Error saving bin map", JOptionPane.ERROR_MESSAGE);
        }else{
            final ExportMapBinDialog exportBinDialog = new ExportMapBinDialog(this, "Export Bin Map Settings");
            exportBinDialog.setLocationRelativeTo(this);
            exportBinDialog.setVisible(true);

            if (exportBinDialog.getReturnValue() == ExportMapBinDialog.APPROVE_OPTION) {
                HashSet<Point> maps = new HashSet<>();
                if(exportBinDialog.exportCurrentMapBin()){
                    maps.add(handler.getMapSelected());
                }else if(exportBinDialog.exportAllMapsBin()){
                    maps.addAll(handler.getMapMatrix().getMatrix().keySet());
                }else{
                    return;//Nothing selected
                }

                ExportMapBinInfoDialog exportInfoDialog = new ExportMapBinInfoDialog(this);
                exportInfoDialog.init(handler, maps, new File(handler.getMapMatrix().filePath).getParent());
                exportInfoDialog.setLocationRelativeTo(this);
                exportInfoDialog.setVisible(true);
            }
        }
    }

    public void saveTileset() throws FileNotFoundException, ParserConfigurationException, TransformerException, IOException {
        File file = new File(handler.getMapMatrix().filePath);
        String path = file.getParent();

        String filename = Utils.removeExtensionFromPath(file.getName()) + "." + Tileset.fileExtension;
        TilesetIO.saveTilesetToFile(path + File.separator + filename, handler.getTileset());
        handler.getTileset().saveImagesToFile(path);

        saveTilesetThumbnail(path + File.separator + "TilesetThumbnail.png");
    }

    public void saveTilesetThumbnail(String path) throws IOException {
        BufferedImage img = tileSelector.getTilesetImage();
        if (img != null) {
            File file = new File(path);
            ImageIO.write(img, "png", file);
        }
    }

    public void saveMapThumbnail() throws IOException {
        mapDisplay.requestScreenshot();
        mapDisplay.updateMapLayersGL();
        mapDisplay.display();

        BufferedImage screenshot = mapDisplay.getScreenshot();
        if (screenshot == null) {
            return;
        }

        String path = new File(handler.getMapMatrix().filePath).getParent();
        File file = new File(path + File.separator + "MapThumbnail.png");
        ImageIO.write(screenshot, "png", file);
    }

    /*
    public void saveBdhc() throws IOException {
        File file = new File(handler.getMapMatrix().filePath);
        String path = file.getParent();
        String filename = Utils.removeExtensionFromPath(file.getName()) + "." + Bdhc.fileExtension;

        int game = handler.getGameIndex();
        if (game == Game.DIAMOND || game == Game.PEARL) {
            BdhcWriterDP.writeBdhc(handler.getBdhc(), path + File.separator + filename);
        } else {
            BdhcWriterHGSS.writeBdhc(handler.getBdhc(), path + File.separator + filename);
        }

    }

    public void saveBacksound() throws IOException {
        int game = handler.getGameIndex();
        if (game == Game.HEART_GOLD || game == Game.SOUL_SILVER) {
            File file = new File(handler.getMapMatrix().filePath);
            String path = file.getParent();
            String filename = Utils.removeExtensionFromPath(file.getName()) + "." + Backsound.fileExtension;

            System.out.println("Backsound OUT: " + filename);

            handler.getBacksound().writeToFile(path + File.separator + filename);
        }
    }

    public void saveCollisions() throws IOException {
        File file = new File(handler.getMapMatrix().filePath);
        String path = file.getParent();
        String filename = Utils.removeExtensionFromPath(file.getName()) + "." + Collisions.fileExtension;
        handler.getCollisions().saveToFile(path + File.separator + filename);
    }

    public void saveBuildings() throws IOException {
        File file = new File(handler.getMapMatrix().filePath);
        String path = file.getParent();
        String filename = Utils.removeExtensionFromPath(file.getName()) + "." + BuildFile.fileExtension;
        handler.getBuildings().saveToFile(path + File.separator + filename);
    }*/
    public void saveMapsAsImdWithDialog() {
        if (handler.getTileset().size() == 0) {
            JOptionPane.showMessageDialog(this,
                    "There is no tileset loaded.\n"
                            + "The IMD can be exported but the materials will be set to default.\n",
                    "No tileset loaded",
                    JOptionPane.WARNING_MESSAGE);
        }

        final ExportImdDialog configDialog = new ExportImdDialog(this);
        configDialog.init(handler);
        configDialog.setLocationRelativeTo(this);
        configDialog.setVisible(true);

        if (configDialog.getReturnValue() == ExportImdDialog.APPROVE_OPTION) {
            ArrayList<String> fileNames = configDialog.getSelectedObjNames();
            String objFolderPath = configDialog.getObjFolderPath();
            String imdFolderPath = configDialog.getImdFolderPath();

            final ImdOutputInfoDialog outputDialog = new ImdOutputInfoDialog(this);
            outputDialog.init(handler, fileNames, objFolderPath, imdFolderPath);
            outputDialog.setLocationRelativeTo(this);
            outputDialog.setVisible(true);

        }
    }

    public void saveMapAsImdWithDialog() {
        if (handler.getTileset().size() == 0) {
            JOptionPane.showMessageDialog(this,
                    "There is no tileset loaded.\n"
                            + "The IMD can be exported but the materials will be set to default.\n",
                    "No tileset loaded",
                    JOptionPane.WARNING_MESSAGE);
        }

        final JFileChooser fcOpen = createProjectFileChooser("Open OBJ for IMD export");
        fcOpen.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath) + ".obj"));
        if (handler.getLastMapDirectoryUsed() != null) {
            fcOpen.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        }
        fcOpen.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fcOpen.setFileFilter(new DirectoryFriendlyExtensionFilter("OBJ (*.obj)", "obj"));
        fcOpen.setApproveButtonText("Open");
        fcOpen.setDialogTitle("Open OBJ Map for converting into IMD");
        int returnValOpen = fcOpen.showOpenDialog(this);
        if (returnValOpen == JFileChooser.APPROVE_OPTION) {
            File openSel = fcOpen.getSelectedFile();
            if (openSel == null || openSel.isDirectory()) {
                if (openSel != null) {
                    fcOpen.setCurrentDirectory(openSel);
                }
            } else if (openSel.exists()) {
                String pathOpen = openSel.getPath();

                final JFileChooser fcSave = createProjectFileChooser("Save IMD export");
                fcSave.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath)));
                fcSave.setCurrentDirectory(openSel.getParentFile());
                fcSave.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fcSave.setFileFilter(new DirectoryFriendlyExtensionFilter("IMD (*.imd)", "imd"));
                fcSave.setApproveButtonText("Save");
                fcSave.setDialogTitle("Save");
                int returnValSave = fcSave.showOpenDialog(this);
                if (returnValSave == JFileChooser.APPROVE_OPTION) {
                    File saveSel = fcSave.getSelectedFile();
                    if (saveSel == null || saveSel.isDirectory()) {
                        if (saveSel != null) {
                            fcSave.setCurrentDirectory(saveSel);
                        }
                    } else {
                    String pathSave = saveSel.getPath();

                    try {
                        ImdModel model = new ImdModel(pathOpen, pathSave, handler.getTileset().getMaterials());
                        final int numVertices = model.getNumVertices();
                        final int numPolygons = model.getNumPolygons();
                        final int numTris = model.getNumTris();
                        final int numQuads = model.getNumQuads();
                        JOptionPane.showMessageDialog(this, "IMD map succesfully exported.\n\n"
                                        + "Number of Materials: " + String.valueOf(model.getNumMaterials()) + "\n"
                                        + "Number of Vertices: " + String.valueOf(numVertices) + "\n"
                                        + "Number of Polygons: " + String.valueOf(numPolygons) + "\n"
                                        + "Number of Triangles: " + String.valueOf(numTris) + "\n"
                                        + "Number of Quads: " + String.valueOf(numQuads),
                                "Map saved", JOptionPane.INFORMATION_MESSAGE);
                        final int maxNumPolygons = 1800;
                        final int maxNumTris = 1200;
                        if (numTris > maxNumTris) {
                            JOptionPane.showMessageDialog(this, "The map might not work properly in game.\n\n"
                                            + "The map contains " + String.valueOf(numTris) + " triangles" + "\n"
                                            + "Try to use less than " + maxNumTris + " triangles" + "\n"
                                            + "Or try to use quads instead of triangles" + "\n",
                                    "Too many triangles", JOptionPane.INFORMATION_MESSAGE);
                        } else if (numPolygons > maxNumPolygons) {
                            JOptionPane.showMessageDialog(this, "The map may not work properly in game.\n\n"
                                            + "The map contains " + String.valueOf(numPolygons) + " polygons" + "\n"
                                            + "Try to use less than " + maxNumPolygons + " polygons",
                                    "Too many polygons", JOptionPane.WARNING_MESSAGE);
                        }
                    } catch (ParserConfigurationException | TransformerException ex) {
                        JOptionPane.showMessageDialog(this,
                                "There was a problem parsing the XML data of the IMD",
                                "Can't export IMD",
                                JOptionPane.ERROR_MESSAGE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "There was a problem exporting the IMD",
                                "Can't export IMD",
                                JOptionPane.ERROR_MESSAGE);
                    } catch (TextureNotFoundException | NormalsNotFoundException ex) {
                        JOptionPane.showMessageDialog(this,
                                ex.getMessage(),
                                "Can't export IMD",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "The selected OBJ file could not be opened",
                        "Can't open OBJ",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void saveMapsAsNsbWithDialog() {
        final ExportNsbmdDialog configDialog = new ExportNsbmdDialog(this, true);
        configDialog.init(handler);
        configDialog.setLocationRelativeTo(this);
        configDialog.setVisible(true);

        if (configDialog.getReturnValue() == ExportNsbmdDialog.APPROVE_OPTION) {
            ArrayList<String> fileNames = configDialog.getSelectedImdNames();
            String imdFolderPath = configDialog.getImdFolderPath();
            String nsbFolderPath = configDialog.getNsbFolderPath();

            final NsbmdOutputInfoDialog outputDialog = new NsbmdOutputInfoDialog(this, true);
            outputDialog.init(handler, fileNames, imdFolderPath, nsbFolderPath, configDialog.includeNsbtxInNsbmd());
            outputDialog.setLocationRelativeTo(this);
            outputDialog.setVisible(true);
        }
    }

    public void saveMapAsNsbWithDialog() {
        final ConverterDialog convDialog = new ConverterDialog(this);
        convDialog.setLocationRelativeTo(this);
        convDialog.setVisible(true);
        if (convDialog.getReturnValue() == ConverterDialog.APPROVE_OPTION) {
            boolean includeNsbtx = convDialog.includeNsbtxInNsbmd();
            final JFileChooser fcOpen = createProjectFileChooser("Open IMD for NSBMD");
            fcOpen.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath) + ".imd"));
            if (handler.getLastMapDirectoryUsed() != null) {
                fcOpen.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
            }
            fcOpen.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fcOpen.setFileFilter(new DirectoryFriendlyExtensionFilter("IMD (*.imd)", "imd"));
            fcOpen.setApproveButtonText("Open");
            fcOpen.setDialogTitle("Open IMD Map for converting into NSBMD");
            int returnVal = fcOpen.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File openSel = fcOpen.getSelectedFile();
                if (openSel == null || openSel.isDirectory()) {
                    if (openSel != null) {
                        fcOpen.setCurrentDirectory(openSel);
                    }
                } else {
                    String absoluteImd = openSel.getAbsolutePath();
                    final JFileChooser fcSave = createProjectFileChooser("Save NSBMD");
                    fcSave.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath)));
                    fcSave.setCurrentDirectory(openSel.getParentFile());
                    fcSave.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    fcSave.setFileFilter(new DirectoryFriendlyExtensionFilter("NSBMD (*.nsbmd)", "nsbmd"));
                    fcSave.setApproveButtonText("Save");
                    fcSave.setDialogTitle("Save");
                    int returnValSave = fcSave.showOpenDialog(this);

                    if (returnValSave == JFileChooser.APPROVE_OPTION) {
                        File saveSel = fcSave.getSelectedFile();
                        if (saveSel == null || saveSel.isDirectory()) {
                            if (saveSel != null) {
                                fcSave.setCurrentDirectory(saveSel);
                            }
                        } else {
                        String nsbPath = saveSel.getPath();
                        String filename = new File(nsbPath).getName();

                        try {
                            Process p = G3dcvtr.processBuilderForModel(absoluteImd, filename, includeNsbtx).start();

                            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                            String outputString = "";
                            String line = null;
                            while ((line = stdError.readLine()) != null) {
                                outputString += line + "\n";
                            }

                            p.waitFor();
                            p.destroy();

                            if (!filename.endsWith("nsbmd")) {
                                filename += ".nsbmd";
                            }
                            if (!nsbPath.endsWith("nsbmd")) {
                                nsbPath += ".nsbmd";
                            }

                            System.out.println(System.getProperty("user.dir"));
                            File srcFile = new File(System.getProperty("user.dir") + File.separator + filename);
                            File dstFile = new File(nsbPath);
                            if (srcFile.exists()) {
                                try {
                                    Files.move(srcFile.toPath(), dstFile.toPath(),
                                            StandardCopyOption.REPLACE_EXISTING);

                                    try {
                                        byte[] nsbmdData = Files.readAllBytes(dstFile.toPath());

                                        ExportNsbmdResultDialog resultDialog = new ExportNsbmdResultDialog(this);
                                        resultDialog.init(nsbmdData);
                                        resultDialog.setLocationRelativeTo(this);
                                        resultDialog.setVisible(true);
                                    } catch (IOException ex) {
                                        JOptionPane.showMessageDialog(this, "NSBMD succesfully exported.",
                                                "NSBMD saved", JOptionPane.INFORMATION_MESSAGE);
                                    }
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(this,
                                            "File was not moved to the save directory. \n"
                                                    + "Reopen Pokemon DS Map Studio and try again.",
                                            "Problem saving generated file",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            } else {
                                ConverterErrorDialog dialog = new ConverterErrorDialog(this);
                                dialog.init("There was a problem creating the NSBMD file. \n"
                                                + "The output from the converter is:",
                                        outputString);
                                dialog.setTitle("Problem generating file");
                                dialog.setLocationRelativeTo(this);
                                dialog.setVisible(true);
                            }
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(this,
                                    "Could not run g3dcvtr.exe.\nPut it under converter/ (with DLLs).\n"
                                            + "On macOS/Linux you need Wine on your PATH.",
                                    "Converter not found",
                                    JOptionPane.ERROR_MESSAGE);
                        } catch (InterruptedException ex) {
                            JOptionPane.showMessageDialog(this,
                                    "The model was not converted",
                                    "Problem converting the model",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                        }
                    }
                }
            }
        }
    }

    public void saveMapBtxWithDialog() {
        final JFileChooser fcOpen = createProjectFileChooser("Open IMD for NSBTX");
        fcOpen.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath) + ".imd"));
        if (handler.getLastMapDirectoryUsed() != null) {
            fcOpen.setCurrentDirectory(new File(handler.getLastMapDirectoryUsed()));
        }
        fcOpen.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fcOpen.setFileFilter(new DirectoryFriendlyExtensionFilter("IMD (*.imd)", "imd"));
        fcOpen.setApproveButtonText("Open");
        fcOpen.setDialogTitle("Open IMD Map for converting into NSBTX");
        int returnVal = fcOpen.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File openSel = fcOpen.getSelectedFile();
            if (openSel == null || openSel.isDirectory()) {
                if (openSel != null) {
                    fcOpen.setCurrentDirectory(openSel);
                }
            } else {
                final JFileChooser fcSave = createProjectFileChooser("Save NSBTX");
                fcSave.setSelectedFile(new File(Utils.removeExtensionFromPath(handler.getMapMatrix().filePath)));
                fcSave.setCurrentDirectory(openSel.getParentFile());
                fcSave.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fcSave.setFileFilter(new DirectoryFriendlyExtensionFilter("NSBTX (*.nsbtx)", "nsbtx"));
                fcSave.setApproveButtonText("Save");
                fcSave.setDialogTitle("Save");
                int returnValSave = fcSave.showOpenDialog(this);

                if (returnValSave == JFileChooser.APPROVE_OPTION) {
                    File saveSel = fcSave.getSelectedFile();
                    if (saveSel == null || saveSel.isDirectory()) {
                        if (saveSel != null) {
                            fcSave.setCurrentDirectory(saveSel);
                        }
                    } else {
                String nsbPath = saveSel.getPath();
                String filename = new File(nsbPath).getName();
                String imdAbs = openSel.getAbsolutePath();

                try {
                    Process p = G3dcvtr.processBuilderForTexture(imdAbs, filename).start();

                    BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                    StringBuilder outputString = new StringBuilder();
                    String line;
                    while ((line = stdError.readLine()) != null) {
                        outputString.append(line).append("\n");
                    }

                    p.waitFor();
                    p.destroy();

                    if (!filename.endsWith("nsbtx")) {
                        filename += ".nsbtx";
                    }
                    if (!nsbPath.endsWith("nsbtx")) {
                        nsbPath += ".nsbtx";
                    }

                    File srcFile = new File(System.getProperty("user.dir") + File.separator + filename);
                    File dstFile = new File(nsbPath);
                    if (srcFile.exists()) {
                        try {
                            Files.move(srcFile.toPath(), dstFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING);
                            try {
                                byte[] nsbtxData = Files.readAllBytes(dstFile.toPath());
                                Nsbtx2 nsbtx = NsbtxLoader2.loadNsbtx(nsbtxData);

                                ExportNsbtxResultDialog resultDialog = new ExportNsbtxResultDialog(this, true);
                                resultDialog.init(nsbtx);
                                resultDialog.setLocationRelativeTo(this);
                                resultDialog.setVisible(true);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(this, "NSBTX succesfully exported.",
                                        "NSBTX saved", JOptionPane.INFORMATION_MESSAGE);
                            }
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(this,
                                    "File was not moved to the save directory. \n"
                                            + "Reopen Pokemon DS Map Studio and try again.",
                                    "Problem saving generated file",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        ConverterErrorDialog dialog = new ConverterErrorDialog(this);
                        dialog.init("There was a problem creating the NSBTX file. \n"
                                        + "The output from the converter is:",
                                outputString.toString());
                        dialog.setTitle("Problem generating file");
                        dialog.setLocationRelativeTo(this);
                        dialog.setVisible(true);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                            "The program \"g3dcvtr.exe\" is not found in the \"converter\" folder.\n"
                                    + "Put the program and its *.dll files in the folder and try again.\n\n"
                                    + "On macOS/Linux you also need Wine in your PATH to run the converter.\n"
                                    + "Example: brew install --cask wine-crossover",
                            "Converter not found",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException ex) {
                    JOptionPane.showMessageDialog(this,
                            "The model was not converted",
                            "Problem converting the model",
                            JOptionPane.ERROR_MESSAGE);
                }
                    }
                }
            }
        }
    }

    public void saveAreasAsBtxWithDialog() {
        final ExportNsbtxDialog configDialog = new ExportNsbtxDialog(this, true);
        configDialog.init(handler);
        configDialog.setLocationRelativeTo(this);
        configDialog.setVisible(true);

        if (configDialog.getReturnValue() == ExportImdDialog.APPROVE_OPTION) {
            ArrayList<Integer> areaIndices = configDialog.getSelectedAreaIndices();
            String nsbtxFolderPath = configDialog.getNsbtxFolderPath();

            final NsbtxOutputInfoDialog outputDialog = new NsbtxOutputInfoDialog(this, true);
            outputDialog.init(handler, areaIndices, nsbtxFolderPath);
            outputDialog.setLocationRelativeTo(this);
            outputDialog.setVisible(true);
        }
    }

    public void changeGame() {
        final GameChangerDialog dialog = new GameChangerDialog(this);
        dialog.init(handler);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (dialog.getReturnValue() == GameSelectorDialog.ACEPTED) {
            updateViewGame();

            handler.getMapMatrix().updateAllLayersGL();
            mapDisplay.repaint();

            updateViewGeometryCount();
        }
    }

    public void openAboutDialog() {
        final AboutDialog dialog = new AboutDialog(this);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void repaintHeightSelector() {
        heightSelector.repaint();
    }

    public void repaintTileSelector() {
        tileSelector.repaint();
    }

    public void repaintTileDisplay() {
        tileDisplay.repaint();
    }

    public void updateTileSelectorScrollBar() {
        int y = tileSelector.getTileSelectedY() - jscTileList.getHeight() / 2;
        jscTileList.getVerticalScrollBar().setValue(y);
    }

    public void updateMapMatrixDisplayScrollBars() {
        Point min = handler.getMapMatrix().getMinCoords();
        Point p = handler.getMapSelected();

        int x = (int) ((p.x - min.x) * MapData.mapThumbnailSize * mapMatrixDisplay.getScale()) - jScrollPaneMapMatrix.getWidth() / 2;
        int y = (int) ((p.y - min.y) * MapData.mapThumbnailSize * mapMatrixDisplay.getScale()) - jScrollPaneMapMatrix.getHeight() / 2;

        jScrollPaneMapMatrix.getHorizontalScrollBar().setValue(x);
        jScrollPaneMapMatrix.getVerticalScrollBar().setValue(y);
    }

    public void repaintThumbnailLayerSelector() {
        thumbnailLayerSelector.repaint();
    }

    public void repaintMapDisplay() {
        mapDisplay.repaint();
    }

    public ThumbnailLayerSelector getThumbnailLayerSelector() {
        return thumbnailLayerSelector;
    }

    private void updateViewGame() {
        jlGameName.setText(Game.gameNames[handler.getGameIndex()]);
        jlGameIcon.setIcon(new ImageIcon(handler.getGame().gameIcons[handler.getGameIndex()]));
    }

    public void undoMapState() {
        StateHandler mapStateHandler = handler.getMapStateHandler();
        if (mapStateHandler.canGetPreviousState()) {
            MapLayerState state = (MapLayerState) mapStateHandler.getPreviousState(new MapLayerState("Map Edit", handler, true));
            state.revertState();
            jbRedo.setEnabled(true);
            if (!mapStateHandler.canGetPreviousState()) {
                jbUndo.setEnabled(false);
            }
            for (Point mapCoord : state.getKeySet()) {
                MapData mapData = handler.getMapMatrix().getMap(mapCoord);
                mapData.getGrid().updateMapLayerGL(state.getLayerIndex(), handler.useRealTimePostProcessing());
                mapData.updateMapThumbnail();
            }
            //mapDisplay.updateMapLayerGL(state.getLayerIndex());

            handler.getMapMatrix().removeUnusedMaps();
            if (!handler.mapSelectedExists()) {
                handler.setDefaultMapSelected();

                handler.getMainFrame().getThumbnailLayerSelector().drawAllLayerThumbnails();
                handler.getMainFrame().getThumbnailLayerSelector().repaint();
            }

            mapDisplay.repaint();
            updateMapMatrixDisplay();
            thumbnailLayerSelector.drawLayerThumbnail(state.getLayerIndex());
            thumbnailLayerSelector.repaint();
            updateViewMapInfo();
        }
    }

    public void redoMapState() {
        StateHandler mapStateHandler = handler.getMapStateHandler();
        if (mapStateHandler.canGetNextState()) {
            MapLayerState state = (MapLayerState) mapStateHandler.getNextState();
            state.revertState();
            jbUndo.setEnabled(true);
            for (Point mapCoord : state.getKeySet()) {
                MapData mapData = handler.getMapMatrix().getMap(mapCoord);
                mapData.getGrid().updateMapLayerGL(state.getLayerIndex(), handler.useRealTimePostProcessing());
                mapData.updateMapThumbnail();
            }
            handler.getMapMatrix().removeUnusedMaps();

            //mapDisplay.updateMapLayerGL(state.getLayerIndex());
            mapDisplay.repaint();
            updateMapMatrixDisplay();
            thumbnailLayerSelector.drawLayerThumbnail(state.getLayerIndex());
            thumbnailLayerSelector.repaint();
            if (!mapStateHandler.canGetNextState()) {
                jbRedo.setEnabled(false);
            }
            updateViewMapInfo();
        }
    }

    public void moveTilesUp() {
        handler.addMapState(new MapLayerState("Move tiles up", handler));
        handler.getGrid().moveTilesUp(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void moveTilesDown() {
        handler.addMapState(new MapLayerState("Move tiles down", handler));
        handler.getGrid().moveTilesDown(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void moveTilesLeft() {
        handler.addMapState(new MapLayerState("Move tiles left", handler));
        handler.getGrid().moveTilesLeft(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void moveTilesRight() {
        handler.addMapState(new MapLayerState("Move tiles right", handler));
        handler.getGrid().moveTilesRight(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void moveTilesUpZ() {
        handler.addMapState(new MapLayerState("Move tiles up Z", handler));
        handler.getGrid().moveTilesUpZ(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void moveTilesDownZ() {
        handler.addMapState(new MapLayerState("Move tiles down Z", handler));
        handler.getGrid().moveTilesDownZ(handler.getActiveLayerIndex());
        thumbnailLayerSelector.drawLayerThumbnail(handler.getActiveLayerIndex());
        thumbnailLayerSelector.repaint();
        mapDisplay.updateActiveMapLayerGL();
        mapDisplay.repaint();
    }

    public void updateViewMapInfo() {
        getjPanelAreaColor().setBackground(handler.getMapMatrix().getAreaColors().get(handler.getCurrentMap().getAreaIndex()));
        getjPanelAreaColor().repaint();

        getJsSelectedArea().setValue(handler.getCurrentMap().getAreaIndex());

        updateViewGeometryCount();

        Point coords = handler.getMapSelected();
        jlMapCoords.setText("(" + coords.x + ", " + coords.y + ")");
    }

    public void updateViewGeometryCount() {
        try {
            jlNumPolygons.setText(String.valueOf(handler.getGrid().getNumPolygons()));
            jlNumMaterials.setText(String.valueOf(handler.getGrid().getNumMaterials()));
        } catch (Exception ex) {
            jlNumPolygons.setText("");
            jlNumMaterials.setText("");
        }
    }

    public JButton getUndoButton() {
        return jbUndo;
    }

    public JButton getRedoButton() {
        return jbRedo;
    }

    public MapDisplay getMapDisplay() {
        return mapDisplay;
    }

    public TileDisplay getTileDisplay() {
        return tileDisplay;
    }

    public MapMatrixDisplay getMapMatrixDisplay() {
        return mapMatrixDisplay;
    }

    public void updateMapMatrixDisplay() {
        Dimension size = jScrollPaneMapMatrix.getSize();
        mapMatrixDisplay.updateSize();
        mapMatrixDisplay.revalidate();
        mapMatrixDisplay.updateMapsImage();

        jScrollPaneMapMatrix.setPreferredSize(size);
        jScrollPaneMapMatrix.revalidate();
    }

    public void renderTilesetThumbnails() {
        if (!TilesetRendererPolicy.isRuntimeEnabled()) {
            StartupTrace.log("MainFrame.renderTilesetThumbnails: skipping TilesetRenderer runtime");
            return;
        }
        generateTilesetThumbnailsWithRenderer();
    }

    /** Always generates GL thumbnails when missing (e.g. after Open RBMAP / RTPKS). */
    public void ensureTilesetThumbnails() {
        if (handler.getTileset() == null || handler.getTileset().size() == 0) {
            return;
        }
        if (!tilesetNeedsThumbnails(handler.getTileset())) {
            return;
        }
        StartupTrace.log("MainFrame.ensureTilesetThumbnails: generating missing thumbnails");
        generateTilesetThumbnailsWithRenderer();
    }

    private static boolean tilesetNeedsThumbnails(Tileset tileset) {
        for (int i = 0; i < tileset.size(); i++) {
            if (tileset.get(i).getSmallThumbnail() == null) {
                return true;
            }
        }
        return false;
    }

    private void generateTilesetThumbnailsWithRenderer() {
        GLContext context = mapDisplay.getContext();
        TilesetRenderer tr = new TilesetRenderer(handler.getTileset());
        try {
            tr.renderTiles();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        tr.destroy();
        mapDisplay.setContext(context, false);
    }

    public JToggleButton getJtbModeEdit() {
        return jtbModeEdit;
    }

    public JToggleButton getJtbModeClear() {
        return jtbModeClear;
    }

    public JToggleButton getJtbModeSmartPaint() {
        return jtbModeSmartPaint;
    }

    public JToggleButton getJtbModeInvSmartPaint() {
        return jtbModeInvSmartPaint;
    }

    public JToggleButton getJtbView3D() {
        return jtbView3D;
    }

    public JToggleButton getJtbViewOrtho() {
        return jtbViewOrtho;
    }

    public JToggleButton getJtbViewHeight() {
        return jtbViewHeight;
    }

    public JToggleButton getJtbViewGrid() {
        return jtbViewGrid;
    }

    public JPanel getjPanelAreaColor() {
        return jPanelAreaColor;
    }

    public JSpinner getJsSelectedArea() {
        return jsSelectedArea;
    }

    public JToggleButton getJtbViewWireframe() {
        return jtbViewWireframe;
    }

    public JCheckBox getJcbViewAreas() {
        return jcbViewAreas;
    }

    private static void addRecentMap(String path) {
        if (!recentMaps.contains(path)) {
            if (recentMaps.size() < 9)
                recentMaps.add(path);
            else
                recentMaps.add(0, path);
        }
    }

    private static void updateRecentMaps() {
        for (int i = 0; i < 9; i++) {
            if (i < recentMaps.size()) {
                prefs.put("recentMaps" + i, recentMaps.get(i));
            } else {
                prefs.remove("recentMaps" + i);
            }
        }
    }

    private void updateRecentMapsMenu() {
        jmiOpenRecentMap.removeAll();
        for (String item : recentMaps) {
            JMenuItem m = new JMenuItem();
            m.setText(recentMaps.get(recentMaps.indexOf(item)));
            m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1 + recentMaps.indexOf(item), InputEvent.CTRL_MASK));

            m.addActionListener(e -> {
                if (opened_map) {
                    int returnVal = JOptionPane.showConfirmDialog(this, "Do you want to close current map?", "Open recent", JOptionPane.YES_NO_OPTION);
                    if (returnVal == JOptionPane.YES_OPTION)
                        openMap(item);
                } else
                    openMap(item);
            });
            jmiOpenRecentMap.add(m);
        }
        jmiOpenRecentMap.addSeparator();
        jmiOpenRecentMap.add(jmiClearHistory);
    }

    private void clearRecentMaps() {
        jmiOpenRecentMap.removeAll();
        recentMaps.clear();
        for (int i = 0; i < 9; i++)
            prefs.put("recentMaps" + i, "");
        updateRecentMapsMenu();
    }

    public void updateMapDisplaySize() {
        if (mapDisplay == null || mapDisplayContainer == null) {
            return;
        }

        // Do not set preferred size here.
        // The parent layout owns the viewport size; the renderer must adapt.
        mapDisplay.repaint();
    }

    private static void loadRecentMaps() {
        for (int i = 0; i < 9; i++) {
            String value = prefs.get("recentMaps" + i, "");
            if (!value.equals("")) {
                recentMaps.add(value);
            } else {
                break;
            }
        }
    }

    public void updateViewAllMapData() {
        renderTilesetThumbnails();

        handler.setIndexTileSelected(0);
        handler.setSmartGridIndexSelected(0);

        handler.getMapMatrix().updateAllLayersGL();
        handler.getMapMatrix().updateBordersData();
        handler.updateAllMapThumbnails();
        mapMatrixDisplay.updateSize();
        updateMapMatrixDisplay();
        updateViewMapInfo();

        tileSelector.updateLayout();
        tileSelector.repaint();
        mapDisplay.requestUpdate();
        mapDisplay.setCameraAtSelectedMap();
        mapDisplay.repaint();
        tileDisplay.requestUpdate();
        tileDisplay.repaint();

        smartGridDisplay.updateSize();
        smartGridDisplay.repaint();
        thumbnailLayerSelector.drawAllLayerThumbnails();
        thumbnailLayerSelector.repaint();
    }

    private void initComponents() {
        traceInit("ENTER");
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        traceInit("before new JMenuBar (jmMainMenu)");
        jmMainMenu = new JMenuBar();
        traceInit("after new JMenuBar (jmMainMenu)");
        traceInit("before new JMenu (jmFile)");
        jmFile = new JMenu();
        traceInit("after new JMenu (jmFile)");
        traceInit("before new JMenuItem (jmiNewMap)");
        jmiNewMap = new JMenuItem();
        traceInit("after new JMenuItem (jmiNewMap)");
        traceInit("before new JMenuItem (jmiOpenMap)");
        jmiOpenMap = new JMenuItem();
        traceInit("after new JMenuItem (jmiOpenMap)");
        traceInit("before new JMenu (jmiOpenRecentMap)");
        jmiOpenRecentMap = new JMenu();
        traceInit("after new JMenu (jmiOpenRecentMap)");
        traceInit("before new JMenuItem (jmiClearHistory)");
        jmiClearHistory = new JMenuItem();
        traceInit("after new JMenuItem (jmiClearHistory)");
        traceInit("before new JMenuItem (jmiSaveMap)");
        jmiSaveMap = new JMenuItem();
        traceInit("after new JMenuItem (jmiSaveMap)");
        traceInit("before new JMenuItem (jmiSaveMapAs)");
        jmiSaveMapAs = new JMenuItem();
        traceInit("after new JMenuItem (jmiSaveMapAs)");
        traceInit("before new JMenuItem (jmiSaveRbmap)");
        jmiSaveRbmap = new JMenuItem();
        traceInit("after new JMenuItem (jmiSaveRbmap)");
        traceInit("before new JMenuItem (jmiAddMaps)");
        jmiAddMaps = new JMenuItem();
        traceInit("after new JMenuItem (jmiAddMaps)");
        traceInit("before new JMenuItem (jmiExportObjWithText)");
        jmiExportObjWithText = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportObjWithText)");
        traceInit("before new JMenuItem (jmiExportMapAsImd)");
        jmiExportMapAsImd = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportMapAsImd)");
        traceInit("before new JMenuItem (jmiExportMapAsNsb)");
        jmiExportMapAsNsb = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportMapAsNsb)");
        traceInit("before new JMenuItem (jmiExportMapBtx)");
        jmiExportMapBtx = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportMapBtx)");
        traceInit("before new JMenuItem (jmiImportTileset)");
        jmiImportTileset = new JMenuItem();
        traceInit("after new JMenuItem (jmiImportTileset)");
        traceInit("before new JMenuItem (jmiExportTileset)");
        jmiExportTileset = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportTileset)");
        traceInit("before new JMenuItem (jmiExportAllTiles)");
        jmiExportAllTiles = new JMenuItem();
        traceInit("after new JMenuItem (jmiExportAllTiles)");
        traceInit("before new JMenu (jmEdit)");
        jmEdit = new JMenu();
        traceInit("after new JMenu (jmEdit)");
        traceInit("before new JMenuItem (jmiUndo)");
        jmiUndo = new JMenuItem();
        traceInit("after new JMenuItem (jmiUndo)");
        traceInit("before new JMenuItem (jmiRedo)");
        jmiRedo = new JMenuItem();
        traceInit("after new JMenuItem (jmiRedo)");
        traceInit("before new JMenuItem (jmiClearLayer)");
        jmiClearLayer = new JMenuItem();
        traceInit("after new JMenuItem (jmiClearLayer)");
        traceInit("before new JMenuItem (jmiClearAllLayers)");
        jmiClearAllLayers = new JMenuItem();
        traceInit("after new JMenuItem (jmiClearAllLayers)");
        traceInit("before new JMenuItem (jmiCopyLayer)");
        jmiCopyLayer = new JMenuItem();
        traceInit("after new JMenuItem (jmiCopyLayer)");
        traceInit("before new JMenuItem (jmiPasteLayer)");
        jmiPasteLayer = new JMenuItem();
        traceInit("after new JMenuItem (jmiPasteLayer)");
        traceInit("before new JMenuItem (jmiPasteLayerTiles)");
        jmiPasteLayerTiles = new JMenuItem();
        traceInit("after new JMenuItem (jmiPasteLayerTiles)");
        traceInit("before new JMenuItem (jmiPasteLayerHeights)");
        jmiPasteLayerHeights = new JMenuItem();
        traceInit("after new JMenuItem (jmiPasteLayerHeights)");
        traceInit("before new JMenuItem (menuItem1)");
        menuItem1 = new JMenuItem();
        traceInit("after new JMenuItem (menuItem1)");
        traceInit("before new JMenu (jmView)");
        jmView = new JMenu();
        traceInit("after new JMenu (jmView)");
        traceInit("before new JMenuItem (jmi3dView)");
        jmi3dView = new JMenuItem();
        traceInit("after new JMenuItem (jmi3dView)");
        traceInit("before new JMenuItem (jmiTopView)");
        jmiTopView = new JMenuItem();
        traceInit("after new JMenuItem (jmiTopView)");
        traceInit("before new JMenuItem (jmiHeightView)");
        jmiHeightView = new JMenuItem();
        traceInit("after new JMenuItem (jmiHeightView)");
        traceInit("before new JMenuItem (jmiToggleGrid)");
        jmiToggleGrid = new JMenuItem();
        traceInit("after new JMenuItem (jmiToggleGrid)");
        traceInit("before new JMenuItem (jmiLoadBackImg)");
        jmiLoadBackImg = new JMenuItem();
        traceInit("after new JMenuItem (jmiLoadBackImg)");
        traceInit("before new JCheckBoxMenuItem (jcbUseBackImage)");
        jcbUseBackImage = new JCheckBoxMenuItem();
        traceInit("after new JCheckBoxMenuItem (jcbUseBackImage)");
        traceInit("before new JMenu (jmTools)");
        jmTools = new JMenu();
        traceInit("after new JMenu (jmTools)");
        traceInit("before new JMenuItem (jmiTilesetEditor)");
        jmiTilesetEditor = new JMenuItem();
        traceInit("after new JMenuItem (jmiTilesetEditor)");
        traceInit("before new JMenuItem (jmiCollisionEditor)");
        jmiCollisionEditor = new JMenuItem();
        traceInit("after new JMenuItem (jmiCollisionEditor)");
        traceInit("before new JMenuItem (jmiBdhcEditor)");
        jmiBdhcEditor = new JMenuItem();
        traceInit("after new JMenuItem (jmiBdhcEditor)");
        traceInit("before new JMenuItem (jmiBDHCAM)");
        jmiBDHCAM = new JMenuItem();
        traceInit("after new JMenuItem (jmiBDHCAM)");
        traceInit("before new JMenuItem (jmiNsbtxEditor)");
        jmiNsbtxEditor = new JMenuItem();
        traceInit("after new JMenuItem (jmiNsbtxEditor)");
        traceInit("before new JMenuItem (jMenuItem1)");
        jMenuItem1 = new JMenuItem();
        traceInit("after new JMenuItem (jMenuItem1)");
        traceInit("before new JMenuItem (jmiAnimationEditor)");
        jmiAnimationEditor = new JMenuItem();
        traceInit("after new JMenuItem (jmiAnimationEditor)");
        traceInit("before new JMenuItem (jmiDumpTilesetAsPngs)");
        jmiDumpTilesetAsPngs = new JMenuItem();
        traceInit("after new JMenuItem (jmiDumpTilesetAsPngs)");
        traceInit("before new JMenu (jmHelp)");
        jmHelp = new JMenu();
        traceInit("after new JMenu (jmHelp)");
        traceInit("before new JMenuItem (jmiKeyboardInfo)");
        jmiKeyboardInfo = new JMenuItem();
        traceInit("after new JMenuItem (jmiKeyboardInfo)");
        traceInit("before new JMenuItem (jmiAbout)");
        jmiAbout = new JMenuItem();
        traceInit("after new JMenuItem (jmiAbout)");
        traceInit("before new JToolBar (jtMainToolbar)");
        jtMainToolbar = new JToolBar();
        traceInit("after new JToolBar (jtMainToolbar)");
        traceInit("before new JButton (jbNewMap)");
        jbNewMap = new JButton();
        traceInit("after new JButton (jbNewMap)");
        traceInit("before new JButton (jbOpenMap)");
        jbOpenMap = new JButton();
        traceInit("after new JButton (jbOpenMap)");
        traceInit("before new JButton (jbSaveMap)");
        jbSaveMap = new JButton();
        traceInit("after new JButton (jbSaveMap)");
        traceInit("before new JButton (jbAddMaps)");
        jbAddMaps = new JButton();
        traceInit("after new JButton (jbAddMaps)");
        traceInit("before new JButton (jbUndo)");
        jbUndo = new JButton();
        traceInit("after new JButton (jbUndo)");
        traceInit("before new JButton (jbRedo)");
        jbRedo = new JButton();
        traceInit("after new JButton (jbRedo)");
        traceInit("before new JButton (jbExportObj)");
        jbExportObj = new JButton();
        traceInit("after new JButton (jbExportObj)");
        traceInit("before new JButton (jbExportImd)");
        jbExportImd = new JButton();
        traceInit("after new JButton (jbExportImd)");
        traceInit("before new JButton (jbExportNsb)");
        jbExportNsb = new JButton();
        traceInit("after new JButton (jbExportNsb)");
        traceInit("before new JButton (jbExportBin)");
        jbExportBin = new JButton();
        traceInit("after new JButton (jbExportBin)");
        traceInit("before new JButton (jbExportNsb1)");
        jbExportNsb1 = new JButton();
        traceInit("after new JButton (jbExportNsb1)");
        traceInit("before new JButton (jbExportNsb2)");
        jbExportNsb2 = new JButton();
        traceInit("after new JButton (jbExportNsb2)");
        traceInit("before new JButton (jbTilelistEditor)");
        jbTilelistEditor = new JButton();
        traceInit("after new JButton (jbTilelistEditor)");
        traceInit("before new JButton (jbCollisionsEditor)");
        jbCollisionsEditor = new JButton();
        traceInit("after new JButton (jbCollisionsEditor)");
        traceInit("before new JButton (jbBdhcEditor)");
        jbBdhcEditor = new JButton();
        traceInit("after new JButton (jbBdhcEditor)");
        traceInit("before new JButton (jbBdhcamEditor)");
        jbBdhcamEditor = new JButton();
        traceInit("after new JButton (jbBdhcamEditor)");
        traceInit("before new JButton (jbBacksoundEditor)");
        jbBacksoundEditor = new JButton();
        traceInit("after new JButton (jbBacksoundEditor)");
        traceInit("before new JButton (jbNsbtxEditor1)");
        jbNsbtxEditor1 = new JButton();
        traceInit("after new JButton (jbNsbtxEditor1)");
        traceInit("before new JButton (jbBuildingEditor)");
        jbBuildingEditor = new JButton();
        traceInit("after new JButton (jbBuildingEditor)");
        traceInit("before new JButton (jbAnimationEditor)");
        jbAnimationEditor = new JButton();
        traceInit("after new JButton (jbAnimationEditor)");
        traceInit("before new JButton (jbSettings)");
        jbSettings = new JButton();
        traceInit("after new JButton (jbSettings)");
        traceInit("before new JButton (jbKeboardInfo)");
        jbKeboardInfo = new JButton();
        traceInit("after new JButton (jbKeboardInfo)");
        traceInit("before new JButton (jbHelp)");
        jbHelp = new JButton();
        traceInit("after new JButton (jbHelp)");
        traceInit("before new JPanel (jpGameInfo)");
        jpGameInfo = new JPanel();
        traceInit("after new JPanel (jpGameInfo)");
        traceInit("before new JLabel (jlGame)");
        jlGame = new JLabel();
        traceInit("after new JLabel (jlGame)");
        traceInit("before new JLabel (jlGameIcon)");
        jlGameIcon = new JLabel();
        traceInit("after new JLabel (jlGameIcon)");
        traceInit("before new JLabel (jlGameName)");
        jlGameName = new JLabel();
        traceInit("after new JLabel (jlGameName)");
        traceInit("before new JSplitPane (jspMainWindow)");
        jspMainWindow = new JSplitPane();
        traceInit("after new JSplitPane (jspMainWindow)");
        traceInit("before new JPanel (jpMainWindow)");
        jpMainWindow = new JPanel();
        traceInit("after new JPanel (jpMainWindow)");
        traceInit("before new JPanel (jpLayer)");
        jpLayer = new JPanel();
        traceInit("after new JPanel (jpLayer)");
        traceInit("before new ThumbnailLayerSelector (thumbnailLayerSelector)");
        thumbnailLayerSelector = new ThumbnailLayerSelector();
        traceInit("after new ThumbnailLayerSelector (thumbnailLayerSelector)");
        traceInit("before new JPanel (mapDisplayContainer)");
        mapDisplayContainer = new JPanel();
        traceInit("after new JPanel (mapDisplayContainer)");
        traceInit("before new MapDisplay (mapDisplay)");
        mapDisplay = new MapDisplay();
        traceInit("after new MapDisplay (mapDisplay)");
        traceInit("before new JPanel (jpZ)");
        jpZ = new JPanel();
        traceInit("after new JPanel (jpZ)");
        traceInit("before new HeightSelector (heightSelector)");
        heightSelector = new HeightSelector();
        traceInit("after new HeightSelector (heightSelector)");
        traceInit("before new JPanel (jpTileList)");
        jpTileList = new JPanel();
        traceInit("after new JPanel (jpTileList)");
        traceInit("before new JScrollPane (jscTileList)");
        jscTileList = new JScrollPane();
        traceInit("after new JScrollPane (jscTileList)");
        traceInit("before new TileSelector (tileSelector)");
        tileSelector = new TileSelector();
        traceInit("after new TileSelector (tileSelector)");
        traceInit("before new JPanel (jpSmartDrawing)");
        jpSmartDrawing = new JPanel();
        traceInit("after new JPanel (jpSmartDrawing)");
        traceInit("before new JScrollPane (jscSmartDrawing)");
        jscSmartDrawing = new JScrollPane();
        traceInit("after new JScrollPane (jscSmartDrawing)");
        traceInit("before new SmartGridDisplay (smartGridDisplay)");
        smartGridDisplay = new SmartGridDisplay();
        traceInit("after new SmartGridDisplay (smartGridDisplay)");
        traceInit("before new JPanel (jpButtons)");
        jpButtons = new JPanel();
        traceInit("after new JPanel (jpButtons)");
        traceInit("before new JPanel (jpView)");
        jpView = new JPanel();
        traceInit("after new JPanel (jpView)");
        traceInit("before new JToolBar (jtView)");
        jtView = new JToolBar();
        traceInit("after new JToolBar (jtView)");
        traceInit("before new JToggleButton (jtbView3D)");
        jtbView3D = new JToggleButton();
        traceInit("after new JToggleButton (jtbView3D)");
        traceInit("before new JToggleButton (jtbViewOrtho)");
        jtbViewOrtho = new JToggleButton();
        traceInit("after new JToggleButton (jtbViewOrtho)");
        traceInit("before new JToggleButton (jtbViewHeight)");
        jtbViewHeight = new JToggleButton();
        traceInit("after new JToggleButton (jtbViewHeight)");
        traceInit("before new JToggleButton (jtbViewGrid)");
        jtbViewGrid = new JToggleButton();
        traceInit("after new JToggleButton (jtbViewGrid)");
        traceInit("before new JToggleButton (jtbViewWireframe)");
        jtbViewWireframe = new JToggleButton();
        traceInit("after new JToggleButton (jtbViewWireframe)");
        traceInit("before new JPanel (jpTools)");
        jpTools = new JPanel();
        traceInit("after new JPanel (jpTools)");
        traceInit("before new JToolBar (jtTools)");
        jtTools = new JToolBar();
        traceInit("after new JToolBar (jtTools)");
        traceInit("before new JToggleButton (jtbModeEdit)");
        jtbModeEdit = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeEdit)");
        traceInit("before new JToggleButton (jtbModeClear)");
        jtbModeClear = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeClear)");
        traceInit("before new JToggleButton (jtbModeSmartPaint)");
        jtbModeSmartPaint = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeSmartPaint)");
        traceInit("before new JToggleButton (jtbModeInvSmartPaint)");
        jtbModeInvSmartPaint = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeInvSmartPaint)");
        traceInit("before new JToggleButton (jtbModeMove)");
        jtbModeMove = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeMove)");
        traceInit("before new JToggleButton (jtbModeZoom)");
        jtbModeZoom = new JToggleButton();
        traceInit("after new JToggleButton (jtbModeZoom)");
        traceInit("before new JButton (jbFitCameraToMap)");
        jbFitCameraToMap = new JButton();
        traceInit("after new JButton (jbFitCameraToMap)");
        traceInit("before new JPanel (jpRightPanel)");
        jpRightPanel = new JPanel();
        traceInit("after new JPanel (jpRightPanel)");
        traceInit("before new JTabbedPane (jtRightPanel)");
        jtRightPanel = new JTabbedPane();
        traceInit("after new JTabbedPane (jtRightPanel)");
        traceInit("before new JPanel (jPanelMatrixInfo)");
        jPanelMatrixInfo = new JPanel();
        traceInit("after new JPanel (jPanelMatrixInfo)");
        traceInit("before new JSplitPane (jspMatrix)");
        jspMatrix = new JSplitPane();
        traceInit("after new JSplitPane (jspMatrix)");
        traceInit("before new JPanel (jpAreaTools)");
        jpAreaTools = new JPanel();
        traceInit("after new JPanel (jpAreaTools)");
        traceInit("before new JScrollPane (jScrollPaneMapMatrix)");
        jScrollPaneMapMatrix = new JScrollPane();
        traceInit("after new JScrollPane (jScrollPaneMapMatrix)");
        traceInit("before new MapMatrixDisplay (mapMatrixDisplay)");
        mapMatrixDisplay = new MapMatrixDisplay();
        traceInit("after new MapMatrixDisplay (mapMatrixDisplay)");
        traceInit("before new JPanel (jpArea)");
        jpArea = new JPanel();
        traceInit("after new JPanel (jpArea)");
        traceInit("before new JLabel (jlArea)");
        jlArea = new JLabel();
        traceInit("after new JLabel (jlArea)");
        traceInit("before new JSpinner (jsSelectedArea)");
        jsSelectedArea = new JSpinner();
        traceInit("after new JSpinner (jsSelectedArea)");
        traceInit("before new JPanel (jPanelAreaColor)");
        jPanelAreaColor = new JPanel();
        traceInit("after new JPanel (jPanelAreaColor)");
        traceInit("before new JPanel (jpMoveMap)");
        jpMoveMap = new JPanel();
        traceInit("after new JPanel (jpMoveMap)");
        traceInit("before new MoveMapPanel (moveMapPanel)");
        moveMapPanel = new MoveMapPanel();
        traceInit("after new MoveMapPanel (moveMapPanel)");
        traceInit("before new JPanel (jpTileSelected)");
        jpTileSelected = new JPanel();
        traceInit("after new JPanel (jpTileSelected)");
        traceInit("before new TileDisplay (tileDisplay)");
        tileDisplay = new TileDisplay();
        traceInit("after new TileDisplay (tileDisplay)");
        traceInit("before new JPanel (jPanelMapTools)");
        jPanelMapTools = new JPanel();
        traceInit("after new JPanel (jPanelMapTools)");
        traceInit("before new JPanel (jpHeightMapAlpha)");
        jpHeightMapAlpha = new JPanel();
        traceInit("after new JPanel (jpHeightMapAlpha)");
        traceInit("before new JSlider (jsHeightMapAlpha)");
        jsHeightMapAlpha = new JSlider();
        traceInit("after new JSlider (jsHeightMapAlpha)");
        traceInit("before new JPanel (jpBackImageAlpha)");
        jpBackImageAlpha = new JPanel();
        traceInit("after new JPanel (jpBackImageAlpha)");
        traceInit("before new JSlider (jsBackImageAlpha)");
        jsBackImageAlpha = new JSlider();
        traceInit("after new JSlider (jsBackImageAlpha)");
        traceInit("before new JPanel (jpMoveLayer)");
        jpMoveLayer = new JPanel();
        traceInit("after new JPanel (jpMoveLayer)");
        traceInit("before new JPanel (jpDirectionalPad)");
        jpDirectionalPad = new JPanel();
        traceInit("after new JPanel (jpDirectionalPad)");
        traceInit("before new JButton (jbMoveMapUp)");
        jbMoveMapUp = new JButton();
        traceInit("after new JButton (jbMoveMapUp)");
        traceInit("before new JButton (jbMoveMapLeft)");
        jbMoveMapLeft = new JButton();
        traceInit("after new JButton (jbMoveMapLeft)");
        traceInit("before new JButton (jbMoveMapRight)");
        jbMoveMapRight = new JButton();
        traceInit("after new JButton (jbMoveMapRight)");
        traceInit("before new JButton (jbMoveMapDown)");
        jbMoveMapDown = new JButton();
        traceInit("after new JButton (jbMoveMapDown)");
        traceInit("before new JPanel (jpZPad)");
        jpZPad = new JPanel();
        traceInit("after new JPanel (jpZPad)");
        traceInit("before new JButton (jbMoveMapUpZ)");
        jbMoveMapUpZ = new JButton();
        traceInit("after new JButton (jbMoveMapUpZ)");
        traceInit("before new JButton (jbMoveMapDownZ)");
        jbMoveMapDownZ = new JButton();
        traceInit("after new JButton (jbMoveMapDownZ)");
        traceInit("before new JCheckBox (jcbRealTimePolyGrouping)");
        jcbRealTimePolyGrouping = new JCheckBox();
        traceInit("after new JCheckBox (jcbRealTimePolyGrouping)");
        traceInit("before new JCheckBox (jcbViewAreas)");
        jcbViewAreas = new JCheckBox();
        traceInit("after new JCheckBox (jcbViewAreas)");
        traceInit("before new JCheckBox (jcbViewGridsBorders)");
        jcbViewGridsBorders = new JCheckBox();
        traceInit("after new JCheckBox (jcbViewGridsBorders)");
        traceInit("before new JPanel (jpStatusBar)");
        jpStatusBar = new JPanel();
        traceInit("after new JPanel (jpStatusBar)");
        traceInit("before new JLabel (jLabel4)");
        jLabel4 = new JLabel();
        traceInit("after new JLabel (jLabel4)");
        traceInit("before new JLabel (jLabel6)");
        jLabel6 = new JLabel();
        traceInit("after new JLabel (jLabel6)");
        traceInit("before new JLabel (jlMapCoords)");
        jlMapCoords = new JLabel();
        traceInit("after new JLabel (jlMapCoords)");
        traceInit("before new JLabel (jLabel2)");
        jLabel2 = new JLabel();
        traceInit("after new JLabel (jLabel2)");
        traceInit("before new JLabel (jlNumPolygons)");
        jlNumPolygons = new JLabel();
        traceInit("after new JLabel (jlNumPolygons)");
        traceInit("before new JLabel (jLabel5)");
        jLabel5 = new JLabel();
        traceInit("after new JLabel (jLabel5)");
        traceInit("before new JLabel (jlNumMaterials)");
        jlNumMaterials = new JLabel();
        traceInit("after new JLabel (jlNumMaterials)");


        traceInit("component field declarations complete");
        //======== this ========
        traceInit("before setDefaultCloseOperation / setTitle");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Pokemon DS Map Studio");
        traceInit("before addWindowListener(WindowAdapter)");
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                formWindowClosing(e);
            }
        });
        traceInit("after addWindowListener");
        Container contentPane = getContentPane();
        traceInit("before contentPane.setLayout(MigLayout)");
        contentPane.setLayout(new MigLayout(
            "insets 0,hidemode 3,gap 5 5",
            // columns
            "[grow,fill]",
            // rows
            "[fill]" +
            "[grow,fill]" +
            "[fill]"));
        traceInit("after contentPane.setLayout(MigLayout)");

        traceInit("before building jmMainMenu tree");
        //======== jmMainMenu ========
        {

            //======== jmFile ========
            {
                jmFile.setText("File");
                jmFile.setMnemonic('F');

                //---- jmiNewMap ----
                jmiNewMap.setIcon(new ImageIcon(getClass().getResource("/icons/newMapIcon_s.png")));
                jmiNewMap.setText("New Map...");
                jmiNewMap.setMnemonic('N');
                jmiNewMap.addActionListener(e -> jmiNewMapActionPerformed(e));
                jmFile.add(jmiNewMap);
                jmFile.addSeparator();

                //---- jmiOpenMap ----
                jmiOpenMap.setIcon(new ImageIcon(getClass().getResource("/icons/openMapIcon_s.png")));
                jmiOpenMap.setText("Open Map...");
                jmiOpenMap.setMnemonic('O');
                jmiOpenMap.addActionListener(e -> jmiOpenMapActionPerformed(e));
                jmFile.add(jmiOpenMap);

                //======== jmiOpenRecentMap ========
                {
                    jmiOpenRecentMap.setText("Open Recent Map...");
                    jmiOpenRecentMap.setIcon(new ImageIcon(getClass().getResource("/icons/openRecentMapIcon_s.png")));
                    jmiOpenRecentMap.setMnemonic('R');
                    jmiOpenRecentMap.addSeparator();

                    //---- jmiClearHistory ----
                    jmiClearHistory.setText("Clear History");
                    jmiClearHistory.setMnemonic('H');
                    jmiClearHistory.addActionListener(e -> jmiClearHistoryActionPerformed(e));
                    jmiOpenRecentMap.add(jmiClearHistory);
                }
                jmFile.add(jmiOpenRecentMap);
                jmFile.addSeparator();

                //---- jmiSaveMap ----
                jmiSaveMap.setIcon(new ImageIcon(getClass().getResource("/icons/saveMapIconSmall.png")));
                jmiSaveMap.setText("Save Map...");
                jmiSaveMap.setMnemonic('S');
                jmiSaveMap.addActionListener(e -> jmiSaveMapActionPerformed(e));
                jmFile.add(jmiSaveMap);

                //---- jmiSaveMapAs ----
                jmiSaveMapAs.setIcon(new ImageIcon(getClass().getResource("/icons/saveMapIconSmall.png")));
                jmiSaveMapAs.setText("Save Map as...");
                jmiSaveMapAs.setMnemonic('A');
                jmiSaveMapAs.addActionListener(e -> jmiSaveMapAsActionPerformed(e));
                jmFile.add(jmiSaveMapAs);

                //---- jmiSaveRbmap ----
                jmiSaveRbmap.setText("Save as RBMAP (bake)...");
                jmiSaveRbmap.addActionListener(e -> jmiSaveRbmapActionPerformed(e));
                jmFile.add(jmiSaveRbmap);
                jmFile.addSeparator();

                //---- jmiAddMaps ----
                jmiAddMaps.setIcon(new ImageIcon(getClass().getResource("/icons/AddMapIconSmall.png")));
                jmiAddMaps.setText("Add Maps...");
                jmiAddMaps.setMnemonic('D');
                jmiAddMaps.addActionListener(e -> jmiAddMapsActionPerformed(e));
                jmFile.add(jmiAddMaps);
                jmFile.addSeparator();

                //---- jmiExportObjWithText ----
                jmiExportObjWithText.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportObjWithText.setText("Export Map as OBJ with textures...");
                jmiExportObjWithText.addActionListener(e -> jmiExportObjWithTextActionPerformed(e));
                jmFile.add(jmiExportObjWithText);

                //---- jmiExportMapAsImd ----
                jmiExportMapAsImd.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportMapAsImd.setText("Export Map as IMD...");
                jmiExportMapAsImd.addActionListener(e -> jmiExportMapAsImdActionPerformed(e));
                jmFile.add(jmiExportMapAsImd);

                //---- jmiExportMapAsNsb ----
                jmiExportMapAsNsb.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportMapAsNsb.setText("Export Map as NSBMD...");
                jmiExportMapAsNsb.addActionListener(e -> jmiExportMapAsNsbActionPerformed(e));
                jmFile.add(jmiExportMapAsNsb);

                //---- jmiExportMapBtx ----
                jmiExportMapBtx.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportMapBtx.setText("Export Map's NSBTX...");
                jmiExportMapBtx.addActionListener(e -> jmiExportMapBtxActionPerformed(e));
                jmFile.add(jmiExportMapBtx);
                jmFile.addSeparator();

                //---- jmiImportTileset ----
                jmiImportTileset.setIcon(new ImageIcon(getClass().getResource("/icons/ImportTileIcon.png")));
                jmiImportTileset.setText("Import Tileset...");
                jmiImportTileset.addActionListener(e -> jmiImportTilesetActionPerformed(e));
                jmFile.add(jmiImportTileset);

                //---- jmiExportTileset ----
                jmiExportTileset.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportTileset.setText("Export Tileset...");
                jmiExportTileset.addActionListener(e -> jmiExportTilesetActionPerformed(e));
                jmFile.add(jmiExportTileset);

                //---- jmiExportAllTiles ----
                jmiExportAllTiles.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiExportAllTiles.setText("Export All Tiles as OBJ...");
                jmiExportAllTiles.addActionListener(e -> jmiExportAllTilesActionPerformed(e));
                jmFile.add(jmiExportAllTiles);
            }
            jmMainMenu.add(jmFile);

            //======== jmEdit ========
            {
                jmEdit.setText("Edit");
                jmEdit.setMnemonic('E');

                //---- jmiUndo ----
                jmiUndo.setIcon(new ImageIcon(getClass().getResource("/icons/undoIconSmall.png")));
                jmiUndo.setText("Undo");
                jmiUndo.setMnemonic('U');
                jmiUndo.addActionListener(e -> jmiUndoActionPerformed(e));
                jmEdit.add(jmiUndo);

                //---- jmiRedo ----
                jmiRedo.setIcon(new ImageIcon(getClass().getResource("/icons/redoIconSmall.png")));
                jmiRedo.setText("Redo");
                jmiRedo.setMnemonic('R');
                jmiRedo.addActionListener(e -> jmiRedoActionPerformed(e));
                jmEdit.add(jmiRedo);
                jmEdit.addSeparator();

                //---- jmiClearLayer ----
                jmiClearLayer.setIcon(new ImageIcon(getClass().getResource("/icons/RemoveIcon.png")));
                jmiClearLayer.setText("Clear Layer");
                jmiClearLayer.setMnemonic('L');
                jmiClearLayer.addActionListener(e -> jmiClearLayerActionPerformed(e));
                jmEdit.add(jmiClearLayer);

                //---- jmiClearAllLayers ----
                jmiClearAllLayers.setText("Clear All Layers");
                jmiClearAllLayers.setEnabled(false);
                jmiClearAllLayers.addActionListener(e -> jmiClearAllLayersActionPerformed(e));
                jmEdit.add(jmiClearAllLayers);
                jmEdit.addSeparator();

                //---- jmiCopyLayer ----
                jmiCopyLayer.setIcon(new ImageIcon(getClass().getResource("/icons/copyIcon.png")));
                jmiCopyLayer.setText("Copy Layer");
                jmiCopyLayer.setMnemonic('C');
                jmiCopyLayer.addActionListener(e -> jmiCopyLayerActionPerformed(e));
                jmEdit.add(jmiCopyLayer);

                //---- jmiPasteLayer ----
                jmiPasteLayer.setIcon(new ImageIcon(getClass().getResource("/icons/pasteIcon.png")));
                jmiPasteLayer.setText("Paste Layer");
                jmiPasteLayer.setMnemonic('P');
                jmiPasteLayer.addActionListener(e -> jmiPasteLayerActionPerformed(e));
                jmEdit.add(jmiPasteLayer);

                //---- jmiPasteLayerTiles ----
                jmiPasteLayerTiles.setIcon(new ImageIcon(getClass().getResource("/icons/pasteTileIcon.png")));
                jmiPasteLayerTiles.setText("Paste Layer Tiles");
                jmiPasteLayerTiles.setMnemonic('T');
                jmiPasteLayerTiles.addActionListener(e -> jmiPasteLayerTilesActionPerformed(e));
                jmEdit.add(jmiPasteLayerTiles);

                //---- jmiPasteLayerHeights ----
                jmiPasteLayerHeights.setIcon(new ImageIcon(getClass().getResource("/icons/pasteHeightIcon.png")));
                jmiPasteLayerHeights.setText("Paste Layer Heights");
                jmiPasteLayerHeights.setMnemonic('H');
                jmiPasteLayerHeights.addActionListener(e -> jmiPasteLayerHeightsActionPerformed(e));
                jmEdit.add(jmiPasteLayerHeights);
                jmEdit.addSeparator();

                //---- menuItem1 ----
                menuItem1.setText("Settings");
                menuItem1.setIcon(new ImageIcon(getClass().getResource("/icons/settingsIconSmall.png")));
                menuItem1.setMnemonic('S');
                menuItem1.addActionListener(e -> menuItem1ActionPerformed(e));
                jmEdit.add(menuItem1);
            }
            jmMainMenu.add(jmEdit);

            //======== jmView ========
            {
                jmView.setText("View");
                jmView.setMnemonic('V');

                //---- jmi3dView ----
                jmi3dView.setText("3D View");
                jmi3dView.setMnemonic('3');
                jmi3dView.addActionListener(e -> jmi3dViewActionPerformed(e));
                jmView.add(jmi3dView);

                //---- jmiTopView ----
                jmiTopView.setText("Top View");
                jmiTopView.setMnemonic('T');
                jmiTopView.addActionListener(e -> jmiTopViewActionPerformed(e));
                jmView.add(jmiTopView);

                //---- jmiHeightView ----
                jmiHeightView.setText("Height View");
                jmiHeightView.setMnemonic('H');
                jmiHeightView.addActionListener(e -> jmiHeightViewActionPerformed(e));
                jmView.add(jmiHeightView);
                jmView.addSeparator();

                //---- jmiToggleGrid ----
                jmiToggleGrid.setText("Toggle Grid");
                jmiToggleGrid.setMnemonic('G');
                jmiToggleGrid.addActionListener(e -> jmiToggleGridActionPerformed(e));
                jmView.add(jmiToggleGrid);
                jmView.addSeparator();

                //---- jmiLoadBackImg ----
                jmiLoadBackImg.setText("Open Background Image");
                jmiLoadBackImg.setMnemonic('O');
                jmiLoadBackImg.addActionListener(e -> jmiLoadBackImgActionPerformed(e));
                jmView.add(jmiLoadBackImg);

                //---- jcbUseBackImage ----
                jcbUseBackImage.setText("Use Background Image");
                jcbUseBackImage.addActionListener(e -> jcbUseBackImageActionPerformed(e));
                jmView.add(jcbUseBackImage);
            }
            jmMainMenu.add(jmView);

            //======== jmTools ========
            {
                jmTools.setText("Tools");
                jmTools.setMnemonic('T');

                //---- jmiTilesetEditor ----
                jmiTilesetEditor.setText("Tileset Editor");
                jmiTilesetEditor.setMnemonic('T');
                jmiTilesetEditor.addActionListener(e -> jmiTilesetEditorActionPerformed(e));
                jmTools.add(jmiTilesetEditor);

                //---- jmiDumpTilesetAsPngs ----
                jmiDumpTilesetAsPngs.setIcon(new ImageIcon(getClass().getResource("/icons/ExportIcon.png")));
                jmiDumpTilesetAsPngs.setText("Dump Tileset as PNGs...");
                jmiDumpTilesetAsPngs.addActionListener(e -> jmiDumpTilesetAsPngsActionPerformed(e));
                jmTools.add(jmiDumpTilesetAsPngs);

                //---- jmiCollisionEditor ----
                jmiCollisionEditor.setText("Collision Editor");
                jmiCollisionEditor.setMnemonic('C');
                jmiCollisionEditor.addActionListener(e -> jmiCollisionEditorActionPerformed(e));
                jmTools.add(jmiCollisionEditor);

                //---- jmiBdhcEditor ----
                jmiBdhcEditor.setText("Terrain Editor");
                jmiBdhcEditor.setMnemonic('B');
                jmiBdhcEditor.addActionListener(e -> jmiBdhcEditorActionPerformed(e));
                jmTools.add(jmiBdhcEditor);

                //---- jmiBDHCAM ----
                jmiBDHCAM.setText("Camera Editor");
                jmiBDHCAM.addActionListener(e -> jmiBDHCAMActionPerformed(e));
                jmTools.add(jmiBDHCAM);

                //---- jmiNsbtxEditor ----
                jmiNsbtxEditor.setText("NSBTX Editor");
                jmiNsbtxEditor.setMnemonic('N');
                jmiNsbtxEditor.addActionListener(e -> jmiNsbtxEditorActionPerformed(e));
                jmTools.add(jmiNsbtxEditor);

                //---- jMenuItem1 ----
                jMenuItem1.setText("Building Editor");
                jMenuItem1.setMnemonic('U');
                jMenuItem1.addActionListener(e -> jMenuItem1ActionPerformed(e));
                jmTools.add(jMenuItem1);

                //---- jmiAnimationEditor ----
                jmiAnimationEditor.setText("Animation Editor");
                jmiAnimationEditor.setMnemonic('A');
                jmiAnimationEditor.addActionListener(e -> jmiAnimationEditorActionPerformed(e));
                jmTools.add(jmiAnimationEditor);
            }
            jmMainMenu.add(jmTools);

            //======== jmHelp ========
            {
                jmHelp.setText("Help");
                jmHelp.setMnemonic('H');

                //---- jmiKeyboardInfo ----
                jmiKeyboardInfo.setText("Keyboard Shortcuts");
                jmiKeyboardInfo.setMnemonic('K');
                jmiKeyboardInfo.addActionListener(e -> jmiKeyboardInfoActionPerformed(e));
                jmHelp.add(jmiKeyboardInfo);

                //---- jmiAbout ----
                jmiAbout.setText("About");
                jmiAbout.setMnemonic('A');
                jmiAbout.addActionListener(e -> jmiAboutActionPerformed(e));
                jmHelp.add(jmiAbout);
            }
            jmMainMenu.add(jmHelp);
        }
        setJMenuBar(jmMainMenu);

        //======== jtMainToolbar ========
        {
            jtMainToolbar.setFloatable(false);
            jtMainToolbar.setRollover(true);
            jtMainToolbar.setMargin(null);
            jtMainToolbar.setMaximumSize(null);
            jtMainToolbar.setMinimumSize(null);
            jtMainToolbar.setPreferredSize(null);

            //---- jbNewMap ----
            jbNewMap.setIcon(new ImageIcon(getClass().getResource("/icons/newMapIcon.png")));
            jbNewMap.setToolTipText("New Map");
            jbNewMap.setBorderPainted(false);
            jbNewMap.setFocusable(false);
            jbNewMap.setHorizontalTextPosition(SwingConstants.CENTER);
            jbNewMap.setIconTextGap(0);
            jbNewMap.setMargin(new Insets(0, 0, 0, 0));
            jbNewMap.setMaximumSize(new Dimension(38, 38));
            jbNewMap.setMinimumSize(new Dimension(38, 38));
            jbNewMap.setPreferredSize(new Dimension(38, 38));
            jbNewMap.addActionListener(e -> jbNewMapActionPerformed(e));
            jtMainToolbar.add(jbNewMap);

            //---- jbOpenMap ----
            jbOpenMap.setIcon(new ImageIcon(getClass().getResource("/icons/openMapIcon.png")));
            jbOpenMap.setToolTipText("Open Map");
            jbOpenMap.setFocusable(false);
            jbOpenMap.setHorizontalTextPosition(SwingConstants.CENTER);
            jbOpenMap.setMaximumSize(new Dimension(38, 38));
            jbOpenMap.setMinimumSize(new Dimension(38, 38));
            jbOpenMap.setName("");
            jbOpenMap.setPreferredSize(new Dimension(38, 38));
            jbOpenMap.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbOpenMap.addActionListener(e -> jbOpenMapActionPerformed(e));
            jtMainToolbar.add(jbOpenMap);

            //---- jbSaveMap ----
            jbSaveMap.setIcon(new ImageIcon(getClass().getResource("/icons/saveMapIcon.png")));
            jbSaveMap.setToolTipText("Save Map");
            jbSaveMap.setFocusable(false);
            jbSaveMap.setHorizontalTextPosition(SwingConstants.CENTER);
            jbSaveMap.setMaximumSize(new Dimension(38, 38));
            jbSaveMap.setMinimumSize(new Dimension(38, 38));
            jbSaveMap.setName("");
            jbSaveMap.setPreferredSize(new Dimension(38, 38));
            jbSaveMap.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbSaveMap.addActionListener(e -> jbSaveMapActionPerformed(e));
            jtMainToolbar.add(jbSaveMap);

            //---- jbAddMaps ----
            jbAddMaps.setIcon(new ImageIcon(getClass().getResource("/icons/importMapIcon.png")));
            jbAddMaps.setToolTipText("Add Maps");
            jbAddMaps.setFocusable(false);
            jbAddMaps.setHorizontalTextPosition(SwingConstants.CENTER);
            jbAddMaps.setMaximumSize(new Dimension(38, 38));
            jbAddMaps.setMinimumSize(new Dimension(38, 38));
            jbAddMaps.setName("");
            jbAddMaps.setPreferredSize(new Dimension(38, 38));
            jbAddMaps.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbAddMaps.addActionListener(e -> jbAddMapsActionPerformed(e));
            jtMainToolbar.add(jbAddMaps);
            jtMainToolbar.addSeparator();

            //---- jbUndo ----
            jbUndo.setIcon(new ImageIcon(getClass().getResource("/icons/undoIcon.png")));
            jbUndo.setToolTipText("Undo (Ctrl+Z)");
            jbUndo.setDisabledIcon(new ImageIcon(getClass().getResource("/icons/undoDisabledIcon.png")));
            jbUndo.setEnabled(false);
            jbUndo.setFocusable(false);
            jbUndo.setHorizontalTextPosition(SwingConstants.CENTER);
            jbUndo.setMaximumSize(new Dimension(38, 38));
            jbUndo.setMinimumSize(new Dimension(38, 38));
            jbUndo.setName("");
            jbUndo.setPreferredSize(new Dimension(38, 38));
            jbUndo.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbUndo.addActionListener(e -> jbUndoActionPerformed(e));
            jtMainToolbar.add(jbUndo);

            //---- jbRedo ----
            jbRedo.setIcon(new ImageIcon(getClass().getResource("/icons/redoIcon.png")));
            jbRedo.setToolTipText("Redo (Ctrl+Y)");
            jbRedo.setDisabledIcon(new ImageIcon(getClass().getResource("/icons/redoDisabledIcon.png")));
            jbRedo.setEnabled(false);
            jbRedo.setFocusable(false);
            jbRedo.setHorizontalTextPosition(SwingConstants.CENTER);
            jbRedo.setMaximumSize(new Dimension(38, 38));
            jbRedo.setMinimumSize(new Dimension(38, 38));
            jbRedo.setName("");
            jbRedo.setPreferredSize(new Dimension(38, 38));
            jbRedo.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbRedo.addActionListener(e -> jbRedoActionPerformed(e));
            jtMainToolbar.add(jbRedo);
            jtMainToolbar.addSeparator();

            //---- jbExportObj ----
            jbExportObj.setIcon(new ImageIcon(getClass().getResource("/icons/exportObjIcon.png")));
            jbExportObj.setToolTipText("Export Map as OBJ with Textures");
            jbExportObj.setFocusable(false);
            jbExportObj.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportObj.setMaximumSize(new Dimension(38, 38));
            jbExportObj.setMinimumSize(new Dimension(38, 38));
            jbExportObj.setName("");
            jbExportObj.setPreferredSize(new Dimension(38, 38));
            jbExportObj.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportObj.addActionListener(e -> jbExportObjActionPerformed(e));
            jtMainToolbar.add(jbExportObj);

            //---- jbExportImd ----
            jbExportImd.setIcon(new ImageIcon(getClass().getResource("/icons/exportImdIcon.png")));
            jbExportImd.setToolTipText("Export Map as IMD");
            jbExportImd.setFocusable(false);
            jbExportImd.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportImd.setMaximumSize(new Dimension(38, 38));
            jbExportImd.setMinimumSize(new Dimension(38, 38));
            jbExportImd.setName("");
            jbExportImd.setPreferredSize(new Dimension(38, 38));
            jbExportImd.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportImd.addActionListener(e -> jbExportImdActionPerformed(e));
            jtMainToolbar.add(jbExportImd);

            //---- jbExportNsb ----
            jbExportNsb.setIcon(new ImageIcon(getClass().getResource("/icons/exportNsbIcon.png")));
            jbExportNsb.setToolTipText("Export Map as NSBMD");
            jbExportNsb.setFocusable(false);
            jbExportNsb.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportNsb.setMaximumSize(new Dimension(38, 38));
            jbExportNsb.setMinimumSize(new Dimension(38, 38));
            jbExportNsb.setName("");
            jbExportNsb.setPreferredSize(new Dimension(38, 38));
            jbExportNsb.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportNsb.addActionListener(e -> jbExportNsbActionPerformed(e));
            jtMainToolbar.add(jbExportNsb);

            //---- jbExportBin ----
            jbExportBin.setIcon(new ImageIcon(getClass().getResource("/icons/exportBinIcon.png")));
            jbExportBin.setToolTipText("Export Map as BIN");
            jbExportBin.setFocusable(false);
            jbExportBin.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportBin.setMaximumSize(new Dimension(38, 38));
            jbExportBin.setMinimumSize(new Dimension(38, 38));
            jbExportBin.setName("");
            jbExportBin.setPreferredSize(new Dimension(38, 38));
            jbExportBin.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportBin.addActionListener(e -> jbExportBinActionPerformed(e));
            jtMainToolbar.add(jbExportBin);
            jtMainToolbar.addSeparator();

            //---- jbExportNsb1 ----
            jbExportNsb1.setIcon(new ImageIcon(getClass().getResource("/icons/exportBtxIcon.png")));
            jbExportNsb1.setToolTipText("Export Map NSBTX");
            jbExportNsb1.setFocusable(false);
            jbExportNsb1.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportNsb1.setMaximumSize(new Dimension(38, 38));
            jbExportNsb1.setMinimumSize(new Dimension(38, 38));
            jbExportNsb1.setName("");
            jbExportNsb1.setPreferredSize(new Dimension(38, 38));
            jbExportNsb1.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportNsb1.addActionListener(e -> jbExportNsb1ActionPerformed(e));
            jtMainToolbar.add(jbExportNsb1);

            //---- jbExportNsb2 ----
            jbExportNsb2.setIcon(new ImageIcon(getClass().getResource("/icons/exportAreasIcon.png")));
            jbExportNsb2.setToolTipText("Export Area NSBTX");
            jbExportNsb2.setFocusable(false);
            jbExportNsb2.setHorizontalTextPosition(SwingConstants.CENTER);
            jbExportNsb2.setMaximumSize(new Dimension(38, 38));
            jbExportNsb2.setMinimumSize(new Dimension(38, 38));
            jbExportNsb2.setName("");
            jbExportNsb2.setPreferredSize(new Dimension(38, 38));
            jbExportNsb2.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbExportNsb2.addActionListener(e -> jbExportNsb2ActionPerformed(e));
            jtMainToolbar.add(jbExportNsb2);
            jtMainToolbar.addSeparator();

            //---- jbTilelistEditor ----
            jbTilelistEditor.setIcon(new ImageIcon(getClass().getResource("/icons/tilelistEditorIcon.png")));
            jbTilelistEditor.setToolTipText("Tile List Editor");
            jbTilelistEditor.setFocusable(false);
            jbTilelistEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbTilelistEditor.setMaximumSize(new Dimension(38, 38));
            jbTilelistEditor.setMinimumSize(new Dimension(38, 38));
            jbTilelistEditor.setName("");
            jbTilelistEditor.setPreferredSize(new Dimension(38, 38));
            jbTilelistEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbTilelistEditor.addActionListener(e -> jbTilelistEditorActionPerformed(e));
            jtMainToolbar.add(jbTilelistEditor);

            //---- jbCollisionsEditor ----
            jbCollisionsEditor.setIcon(new ImageIcon(getClass().getResource("/icons/collisionEditorIcon.png")));
            jbCollisionsEditor.setToolTipText("Collisions Editor");
            jbCollisionsEditor.setFocusable(false);
            jbCollisionsEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbCollisionsEditor.setMaximumSize(new Dimension(38, 38));
            jbCollisionsEditor.setMinimumSize(new Dimension(38, 38));
            jbCollisionsEditor.setName("");
            jbCollisionsEditor.setPreferredSize(new Dimension(38, 38));
            jbCollisionsEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbCollisionsEditor.addActionListener(e -> jbCollisionsEditorActionPerformed(e));
            jtMainToolbar.add(jbCollisionsEditor);

            //---- jbBdhcEditor ----
            jbBdhcEditor.setIcon(new ImageIcon(getClass().getResource("/icons/bdhcEditorIcon.png")));
            jbBdhcEditor.setToolTipText("Terrain Editor");
            jbBdhcEditor.setFocusable(false);
            jbBdhcEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbBdhcEditor.setMaximumSize(new Dimension(38, 38));
            jbBdhcEditor.setMinimumSize(new Dimension(38, 38));
            jbBdhcEditor.setName("");
            jbBdhcEditor.setPreferredSize(new Dimension(38, 38));
            jbBdhcEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbBdhcEditor.addActionListener(e -> jbBdhcEditorActionPerformed(e));
            jtMainToolbar.add(jbBdhcEditor);

            //---- jbBdhcamEditor ----
            jbBdhcamEditor.setIcon(new ImageIcon(getClass().getResource("/icons/bdhcamEditorIcon.png")));
            jbBdhcamEditor.setToolTipText("Bdhcam Editor");
            jbBdhcamEditor.setFocusable(false);
            jbBdhcamEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbBdhcamEditor.setMaximumSize(new Dimension(38, 38));
            jbBdhcamEditor.setMinimumSize(new Dimension(38, 38));
            jbBdhcamEditor.setName("");
            jbBdhcamEditor.setPreferredSize(new Dimension(38, 38));
            jbBdhcamEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbBdhcamEditor.addActionListener(e -> jbBdhcamEditorActionPerformed(e));
            jtMainToolbar.add(jbBdhcamEditor);

            //---- jbBacksoundEditor ----
            jbBacksoundEditor.setIcon(new ImageIcon(getClass().getResource("/icons/backsoundEditorIcon.png")));
            jbBacksoundEditor.setToolTipText("Backsound Editor");
            jbBacksoundEditor.setFocusable(false);
            jbBacksoundEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbBacksoundEditor.setMaximumSize(new Dimension(38, 38));
            jbBacksoundEditor.setMinimumSize(new Dimension(38, 38));
            jbBacksoundEditor.setName("");
            jbBacksoundEditor.setPreferredSize(new Dimension(38, 38));
            jbBacksoundEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbBacksoundEditor.addActionListener(e -> jbBacksoundEditorActionPerformed(e));
            jtMainToolbar.add(jbBacksoundEditor);

            //---- jbNsbtxEditor1 ----
            jbNsbtxEditor1.setIcon(new ImageIcon(getClass().getResource("/icons/nsbtxEditorIcon.png")));
            jbNsbtxEditor1.setToolTipText("NSBTX Editor");
            jbNsbtxEditor1.setFocusable(false);
            jbNsbtxEditor1.setHorizontalTextPosition(SwingConstants.CENTER);
            jbNsbtxEditor1.setMaximumSize(new Dimension(38, 38));
            jbNsbtxEditor1.setMinimumSize(new Dimension(38, 38));
            jbNsbtxEditor1.setName("");
            jbNsbtxEditor1.setPreferredSize(new Dimension(38, 38));
            jbNsbtxEditor1.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbNsbtxEditor1.addActionListener(e -> jbNsbtxEditor1ActionPerformed(e));
            jtMainToolbar.add(jbNsbtxEditor1);

            //---- jbBuildingEditor ----
            jbBuildingEditor.setIcon(new ImageIcon(getClass().getResource("/icons/buildingEditorIcon.png")));
            jbBuildingEditor.setToolTipText("Building Editor");
            jbBuildingEditor.setFocusable(false);
            jbBuildingEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbBuildingEditor.setMaximumSize(new Dimension(38, 38));
            jbBuildingEditor.setMinimumSize(new Dimension(38, 38));
            jbBuildingEditor.setName("");
            jbBuildingEditor.setPreferredSize(new Dimension(38, 38));
            jbBuildingEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbBuildingEditor.addActionListener(e -> jbBuildingEditorActionPerformed(e));
            jtMainToolbar.add(jbBuildingEditor);

            //---- jbAnimationEditor ----
            jbAnimationEditor.setIcon(new ImageIcon(getClass().getResource("/icons/animationEditorIcon.png")));
            jbAnimationEditor.setToolTipText("Animation Editor");
            jbAnimationEditor.setFocusable(false);
            jbAnimationEditor.setHorizontalTextPosition(SwingConstants.CENTER);
            jbAnimationEditor.setMaximumSize(new Dimension(38, 38));
            jbAnimationEditor.setMinimumSize(new Dimension(38, 38));
            jbAnimationEditor.setName("");
            jbAnimationEditor.setPreferredSize(new Dimension(38, 38));
            jbAnimationEditor.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbAnimationEditor.addActionListener(e -> jbAnimationEditorActionPerformed(e));
            jtMainToolbar.add(jbAnimationEditor);
            jtMainToolbar.addSeparator();

            //---- jbSettings ----
            jbSettings.setMaximumSize(new Dimension(38, 38));
            jbSettings.setMinimumSize(new Dimension(38, 38));
            jbSettings.setPreferredSize(new Dimension(38, 38));
            jbSettings.setIcon(new ImageIcon(getClass().getResource("/icons/settingsIcon.png")));
            jbSettings.addActionListener(e -> jbSettingsActionPerformed(e));
            jtMainToolbar.add(jbSettings);

            //---- jbKeboardInfo ----
            jbKeboardInfo.setIcon(new ImageIcon(getClass().getResource("/icons/keyboardInfoIcon.png")));
            jbKeboardInfo.setToolTipText("Keyboard Shortcuts");
            jbKeboardInfo.setFocusable(false);
            jbKeboardInfo.setHorizontalTextPosition(SwingConstants.CENTER);
            jbKeboardInfo.setMaximumSize(new Dimension(38, 38));
            jbKeboardInfo.setMinimumSize(new Dimension(38, 38));
            jbKeboardInfo.setName("");
            jbKeboardInfo.setPreferredSize(new Dimension(38, 38));
            jbKeboardInfo.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbKeboardInfo.addActionListener(e -> jbKeboardInfoActionPerformed(e));
            jtMainToolbar.add(jbKeboardInfo);

            //---- jbHelp ----
            jbHelp.setIcon(new ImageIcon(getClass().getResource("/icons/helpIcon.png")));
            jbHelp.setToolTipText("Help");
            jbHelp.setFocusable(false);
            jbHelp.setHorizontalTextPosition(SwingConstants.CENTER);
            jbHelp.setMaximumSize(new Dimension(38, 38));
            jbHelp.setMinimumSize(new Dimension(38, 38));
            jbHelp.setName("");
            jbHelp.setPreferredSize(new Dimension(38, 38));
            jbHelp.setVerticalTextPosition(SwingConstants.BOTTOM);
            jbHelp.addActionListener(e -> jbHelpActionPerformed(e));
            jtMainToolbar.add(jbHelp);
        }
        contentPane.add(jtMainToolbar, "cell 0 0");

        //======== jpGameInfo ========
        {
            jpGameInfo.setLayout(new GridBagLayout());
            ((GridBagLayout)jpGameInfo.getLayout()).columnWidths = new int[] {0, 0, 0};
            ((GridBagLayout)jpGameInfo.getLayout()).rowHeights = new int[] {0, 0, 0};
            ((GridBagLayout)jpGameInfo.getLayout()).columnWeights = new double[] {0.0, 1.0, 1.0E-4};
            ((GridBagLayout)jpGameInfo.getLayout()).rowWeights = new double[] {0.0, 0.0, 1.0E-4};

            //---- jlGame ----
            jlGame.setText("Map for: ");
            jpGameInfo.add(jlGame, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 5, 5), 0, 0));

            //---- jlGameIcon ----
            jlGameIcon.setText(" ");
            jlGameIcon.setMaximumSize(new Dimension(32, 32));
            jlGameIcon.setMinimumSize(new Dimension(32, 32));
            jlGameIcon.setPreferredSize(new Dimension(32, 32));
            jlGameIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            jlGameIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    jlGameIconMousePressed(e);
                }
            });
            jpGameInfo.add(jlGameIcon, new GridBagConstraints(1, 0, 1, 2, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 0), 0, 0));

            //---- jlGameName ----
            jlGameName.setFont(new Font("Tahoma", Font.BOLD, 11));
            jlGameName.setText("Game Name");
            jpGameInfo.add(jlGameName, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                new Insets(0, 0, 0, 5), 0, 0));
        }
        contentPane.add(jpGameInfo, "cell 0 0,alignx right,grow 0 100,gapx 5 5,gapy 5 5");

        traceInit("before jspMainWindow / jpMainWindow layout");
        //======== jspMainWindow ========
        {
            jspMainWindow.setResizeWeight(0.8);

            //======== jpMainWindow ========
            {
                jpMainWindow.setLayout(new MigLayout(
                    "hidemode 3",
                    // columns
                    "[fill]" +
                    "[fill]" +
                    "[grow,fill]" +
                    "[fill]" +
                    "[fill]" +
                    "[fill]",
                    // rows
                    "[grow,fill]"));

                //======== jpLayer ========
                {
                    jpLayer.setBorder(new TitledBorder(null, "Layer", TitledBorder.CENTER, TitledBorder.ABOVE_TOP, null, new Color(204, 102, 0)));

                    //======== thumbnailLayerSelector ========
                    {

                        GroupLayout thumbnailLayerSelectorLayout = new GroupLayout(thumbnailLayerSelector);
                        thumbnailLayerSelector.setLayout(thumbnailLayerSelectorLayout);
                        thumbnailLayerSelectorLayout.setHorizontalGroup(
                            thumbnailLayerSelectorLayout.createParallelGroup()
                                .addGap(0, 64, Short.MAX_VALUE)
                        );
                        thumbnailLayerSelectorLayout.setVerticalGroup(
                            thumbnailLayerSelectorLayout.createParallelGroup()
                                .addGap(0, 512, Short.MAX_VALUE)
                        );
                    }

                    GroupLayout jpLayerLayout = new GroupLayout(jpLayer);
                    jpLayer.setLayout(jpLayerLayout);
                    jpLayerLayout.setHorizontalGroup(
                        jpLayerLayout.createParallelGroup()
                            .addGroup(jpLayerLayout.createSequentialGroup()
                                .addComponent(thumbnailLayerSelector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                    );
                    jpLayerLayout.setVerticalGroup(
                        jpLayerLayout.createParallelGroup()
                            .addComponent(thumbnailLayerSelector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    );
                }
                jpMainWindow.add(jpLayer, "cell 0 0");

                traceInit("before mapDisplayContainer / mapDisplay wiring");
                //======== mapDisplayContainer ========
                {
                    mapDisplayContainer.addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentResized(ComponentEvent e) {
                            mapDisplayContainerComponentResized(e);
                        }
                    });
                    mapDisplayContainer.setLayout(new BorderLayout());
                    mapDisplayContainer.setPreferredSize(new Dimension(544, 544));
                    mapDisplayContainer.setMinimumSize(new Dimension(256, 256));

                    //======== mapDisplay ========
                    // GLCanvas is not a Container: border/layout apply to mapDisplayContainer / wrapper only.
                    {
                        traceInit("before mapDisplay container border (GLCanvas)");
                        mapDisplayContainer.setBorder(new LineBorder(new Color(102, 102, 102)));
                        traceInit("after mapDisplay container border");
                    }
                    traceInit("before mapDisplayContainer.add(mapDisplay)");
                    mapDisplayContainer.add(mapDisplay, BorderLayout.CENTER);
                    traceInit("after mapDisplayContainer.add(mapDisplay)");
                }
                jpMainWindow.add(mapDisplayContainer, "cell 2 0,grow,push");

                //======== jpZ ========
                {
                    jpZ.setBorder(new TitledBorder(null, "Z", TitledBorder.CENTER, TitledBorder.ABOVE_TOP, null, Color.blue));

                    //======== heightSelector ========
                    {
                        heightSelector.setPreferredSize(new Dimension(16, 496));

                        GroupLayout heightSelectorLayout = new GroupLayout(heightSelector);
                        heightSelector.setLayout(heightSelectorLayout);
                        heightSelectorLayout.setHorizontalGroup(
                            heightSelectorLayout.createParallelGroup()
                                .addGap(0, 16, Short.MAX_VALUE)
                        );
                        heightSelectorLayout.setVerticalGroup(
                            heightSelectorLayout.createParallelGroup()
                                .addGap(0, 496, Short.MAX_VALUE)
                        );
                    }

                    GroupLayout jpZLayout = new GroupLayout(jpZ);
                    jpZ.setLayout(jpZLayout);
                    jpZLayout.setHorizontalGroup(
                        jpZLayout.createParallelGroup()
                            .addGroup(jpZLayout.createSequentialGroup()
                                .addComponent(heightSelector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                    );
                    jpZLayout.setVerticalGroup(
                        jpZLayout.createParallelGroup()
                            .addGroup(jpZLayout.createSequentialGroup()
                                .addComponent(heightSelector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                    );
                }
                jpMainWindow.add(jpZ, "cell 3 0");

                //======== jpTileList ========
                {
                    jpTileList.setBorder(new TitledBorder(null, "Tile List", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));

                    //======== jscTileList ========
                    {
                        jscTileList.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                        jscTileList.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

                        //======== tileSelector ========
                        {
                            tileSelector.setPreferredSize(new Dimension(128, 0));
                            tileSelector.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mousePressed(MouseEvent e) {
                                    tileSelectorMousePressed(e);
                                }
                            });

                            GroupLayout tileSelectorLayout = new GroupLayout(tileSelector);
                            tileSelector.setLayout(tileSelectorLayout);
                            tileSelectorLayout.setHorizontalGroup(
                                tileSelectorLayout.createParallelGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                            );
                            tileSelectorLayout.setVerticalGroup(
                                tileSelectorLayout.createParallelGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                            );
                        }
                        jscTileList.setViewportView(tileSelector);
                    }

                    GroupLayout jpTileListLayout = new GroupLayout(jpTileList);
                    jpTileList.setLayout(jpTileListLayout);
                    jpTileListLayout.setHorizontalGroup(
                        jpTileListLayout.createParallelGroup()
                            .addComponent(jscTileList, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    );
                    jpTileListLayout.setVerticalGroup(
                        jpTileListLayout.createParallelGroup()
                            .addGroup(jpTileListLayout.createSequentialGroup()
                                .addComponent(jscTileList, GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                                .addGap(0, 0, 0))
                    );
                }
                jpMainWindow.add(jpTileList, "cell 4 0");

                //======== jpSmartDrawing ========
                {
                    jpSmartDrawing.setBorder(new TitledBorder(null, "Smart Drawing", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));

                    //======== jscSmartDrawing ========
                    {
                        jscSmartDrawing.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                        jscSmartDrawing.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

                        //======== smartGridDisplay ========
                        {

                            GroupLayout smartGridDisplayLayout = new GroupLayout(smartGridDisplay);
                            smartGridDisplay.setLayout(smartGridDisplayLayout);
                            smartGridDisplayLayout.setHorizontalGroup(
                                smartGridDisplayLayout.createParallelGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                            );
                            smartGridDisplayLayout.setVerticalGroup(
                                smartGridDisplayLayout.createParallelGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                            );
                        }
                        jscSmartDrawing.setViewportView(smartGridDisplay);
                    }

                    GroupLayout jpSmartDrawingLayout = new GroupLayout(jpSmartDrawing);
                    jpSmartDrawing.setLayout(jpSmartDrawingLayout);
                    jpSmartDrawingLayout.setHorizontalGroup(
                        jpSmartDrawingLayout.createParallelGroup()
                            .addGroup(jpSmartDrawingLayout.createSequentialGroup()
                                .addComponent(jscSmartDrawing, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                    );
                    jpSmartDrawingLayout.setVerticalGroup(
                        jpSmartDrawingLayout.createParallelGroup()
                            .addComponent(jscSmartDrawing, GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                    );
                }
                jpMainWindow.add(jpSmartDrawing, "cell 5 0");

                //======== jpButtons ========
                {
                    jpButtons.setLayout(new BoxLayout(jpButtons, BoxLayout.Y_AXIS));

                    //======== jpView ========
                    {
                        jpView.setBorder(new TitledBorder(null, "View", TitledBorder.CENTER, TitledBorder.ABOVE_TOP));

                        //======== jtView ========
                        {
                            jtView.setFloatable(false);
                            jtView.setOrientation(SwingConstants.VERTICAL);
                            jtView.setRollover(true);

                            //---- jtbView3D ----
                            jtbView3D.setIcon(new ImageIcon(getClass().getResource("/icons/3DViewIcon.png")));
                            jtbView3D.setToolTipText("3D View");
                            jtbView3D.setFocusable(false);
                            jtbView3D.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbView3D.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbView3D.addActionListener(e -> jtbView3DActionPerformed(e));
                            jtView.add(jtbView3D);

                            //---- jtbViewOrtho ----
                            jtbViewOrtho.setIcon(new ImageIcon(getClass().getResource("/icons/topViewIcon.png")));
                            jtbViewOrtho.setSelected(true);
                            jtbViewOrtho.setToolTipText("Top View");
                            jtbViewOrtho.setFocusable(false);
                            jtbViewOrtho.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbViewOrtho.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbViewOrtho.addActionListener(e -> jtbViewOrthoActionPerformed(e));
                            jtView.add(jtbViewOrtho);

                            //---- jtbViewHeight ----
                            jtbViewHeight.setIcon(new ImageIcon(getClass().getResource("/icons/heightViewIcon.png")));
                            jtbViewHeight.setToolTipText("Height View");
                            jtbViewHeight.setFocusable(false);
                            jtbViewHeight.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbViewHeight.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbViewHeight.addActionListener(e -> jtbViewHeightActionPerformed(e));
                            jtView.add(jtbViewHeight);
                            jtView.addSeparator();

                            //---- jtbViewGrid ----
                            jtbViewGrid.setIcon(new ImageIcon(getClass().getResource("/icons/gridViewIcon.png")));
                            jtbViewGrid.setSelected(true);
                            jtbViewGrid.setToolTipText("Grid");
                            jtbViewGrid.setFocusable(false);
                            jtbViewGrid.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbViewGrid.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbViewGrid.addActionListener(e -> jtbViewGridActionPerformed(e));
                            jtView.add(jtbViewGrid);

                            //---- jtbViewWireframe ----
                            jtbViewWireframe.setIcon(new ImageIcon(getClass().getResource("/icons/wireViewIcon.png")));
                            jtbViewWireframe.setToolTipText("Wireframe");
                            jtbViewWireframe.setFocusable(false);
                            jtbViewWireframe.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbViewWireframe.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbViewWireframe.addActionListener(e -> jtbViewWireframeActionPerformed(e));
                            jtView.add(jtbViewWireframe);
                        }

                        GroupLayout jpViewLayout = new GroupLayout(jpView);
                        jpView.setLayout(jpViewLayout);
                        jpViewLayout.setHorizontalGroup(
                            jpViewLayout.createParallelGroup()
                                .addComponent(jtView, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        );
                        jpViewLayout.setVerticalGroup(
                            jpViewLayout.createParallelGroup()
                                .addComponent(jtView, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        );
                    }
                    jpButtons.add(jpView);

                    //======== jpTools ========
                    {
                        jpTools.setBorder(new TitledBorder(null, "Tools", TitledBorder.CENTER, TitledBorder.ABOVE_TOP));

                        //======== jtTools ========
                        {
                            jtTools.setFloatable(false);
                            jtTools.setOrientation(SwingConstants.VERTICAL);
                            jtTools.setRollover(true);

                            //---- jtbModeEdit ----
                            jtbModeEdit.setIcon(new ImageIcon(getClass().getResource("/icons/CursorIcon.png")));
                            jtbModeEdit.setSelected(true);
                            jtbModeEdit.setToolTipText("Select Mode");
                            jtbModeEdit.setFocusable(false);
                            jtbModeEdit.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeEdit.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeEdit.addActionListener(e -> jtbModeEditActionPerformed(e));
                            jtTools.add(jtbModeEdit);

                            //---- jtbModeClear ----
                            jtbModeClear.setIcon(new ImageIcon(getClass().getResource("/icons/ClearTileIcon.png")));
                            jtbModeClear.setToolTipText("Clear Mode");
                            jtbModeClear.setFocusable(false);
                            jtbModeClear.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeClear.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeClear.addActionListener(e -> jtbModeClearActionPerformed(e));
                            jtTools.add(jtbModeClear);

                            //---- jtbModeSmartPaint ----
                            jtbModeSmartPaint.setIcon(new ImageIcon(getClass().getResource("/icons/SmartGridIcon.png")));
                            jtbModeSmartPaint.setToolTipText("Smart Drawing");
                            jtbModeSmartPaint.setFocusable(false);
                            jtbModeSmartPaint.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeSmartPaint.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeSmartPaint.addActionListener(e -> jtbModeSmartPaintActionPerformed(e));
                            jtTools.add(jtbModeSmartPaint);

                            //---- jtbModeInvSmartPaint ----
                            jtbModeInvSmartPaint.setIcon(new ImageIcon(getClass().getResource("/icons/SmartGridInvertedIcon.png")));
                            jtbModeInvSmartPaint.setToolTipText("Smart Drawing Inverted");
                            jtbModeInvSmartPaint.setFocusable(false);
                            jtbModeInvSmartPaint.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeInvSmartPaint.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeInvSmartPaint.addActionListener(e -> jtbModeInvSmartPaintActionPerformed(e));
                            jtTools.add(jtbModeInvSmartPaint);
                            jtTools.addSeparator();

                            //---- jtbModeMove ----
                            jtbModeMove.setIcon(new ImageIcon(getClass().getResource("/icons/MoveIcon.png")));
                            jtbModeMove.setToolTipText("Move Camera");
                            jtbModeMove.setFocusable(false);
                            jtbModeMove.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeMove.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeMove.addActionListener(e -> jtbModeMoveActionPerformed(e));
                            jtTools.add(jtbModeMove);

                            //---- jtbModeZoom ----
                            jtbModeZoom.setIcon(new ImageIcon(getClass().getResource("/icons/ZoomIcon.png")));
                            jtbModeZoom.setToolTipText("Zoom Camera");
                            jtbModeZoom.setFocusable(false);
                            jtbModeZoom.setHorizontalTextPosition(SwingConstants.CENTER);
                            jtbModeZoom.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jtbModeZoom.addActionListener(e -> jtbModeZoomActionPerformed(e));
                            jtTools.add(jtbModeZoom);

                            //---- jbFitCameraToMap ----
                            jbFitCameraToMap.setIcon(new ImageIcon(getClass().getResource("/icons/fitMapIcon.png")));
                            jbFitCameraToMap.setToolTipText("Fit Camera in Selected Map");
                            jbFitCameraToMap.setFocusable(false);
                            jbFitCameraToMap.setHorizontalTextPosition(SwingConstants.CENTER);
                            jbFitCameraToMap.setVerticalTextPosition(SwingConstants.BOTTOM);
                            jbFitCameraToMap.addActionListener(e -> jbFitCameraToMapActionPerformed(e));
                            jtTools.add(jbFitCameraToMap);
                        }

                        GroupLayout jpToolsLayout = new GroupLayout(jpTools);
                        jpTools.setLayout(jpToolsLayout);
                        jpToolsLayout.setHorizontalGroup(
                            jpToolsLayout.createParallelGroup()
                                .addComponent(jtTools, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        );
                        jpToolsLayout.setVerticalGroup(
                            jpToolsLayout.createParallelGroup()
                                .addComponent(jtTools, GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                        );
                    }
                    jpButtons.add(jpTools);
                }
                jpMainWindow.add(jpButtons, "cell 1 0");
            }
            jspMainWindow.setLeftComponent(jpMainWindow);

            //======== jpRightPanel ========
            {
                jpRightPanel.setMinimumSize(new Dimension(100, 336));
                jpRightPanel.setPreferredSize(new Dimension(250, 580));
                jpRightPanel.setLayout(new BoxLayout(jpRightPanel, BoxLayout.X_AXIS));

                //======== jtRightPanel ========
                {
                    jtRightPanel.setPreferredSize(new Dimension(250, 586));
                    jtRightPanel.setMinimumSize(new Dimension(100, 336));

                    //======== jPanelMatrixInfo ========
                    {
                        jPanelMatrixInfo.setLayout(new BoxLayout(jPanelMatrixInfo, BoxLayout.X_AXIS));

                        //======== jspMatrix ========
                        {
                            jspMatrix.setOrientation(JSplitPane.VERTICAL_SPLIT);
                            jspMatrix.setResizeWeight(0.5);

                            //======== jpAreaTools ========
                            {
                                jpAreaTools.setLayout(new MigLayout(
                                    "hidemode 3",
                                    // columns
                                    "[grow,fill]",
                                    // rows
                                    "[grow,fill]" +
                                    "[fill]" +
                                    "[]"));

                                //======== jScrollPaneMapMatrix ========
                                {
                                    jScrollPaneMapMatrix.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                                    jScrollPaneMapMatrix.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                                    jScrollPaneMapMatrix.setMaximumSize(null);

                                    //======== mapMatrixDisplay ========
                                    {
                                        mapMatrixDisplay.setPreferredSize(new Dimension(200, 200));
                                        mapMatrixDisplay.setLayout(new BoxLayout(mapMatrixDisplay, BoxLayout.X_AXIS));
                                    }
                                    jScrollPaneMapMatrix.setViewportView(mapMatrixDisplay);
                                }
                                jpAreaTools.add(jScrollPaneMapMatrix, "cell 0 0");

                                //======== jpArea ========
                                {
                                    jpArea.setLayout(new GridBagLayout());
                                    ((GridBagLayout)jpArea.getLayout()).columnWidths = new int[] {0, 131, 16, 0};
                                    ((GridBagLayout)jpArea.getLayout()).rowHeights = new int[] {16, 0};
                                    ((GridBagLayout)jpArea.getLayout()).columnWeights = new double[] {0.0, 1.0, 0.0, 1.0E-4};
                                    ((GridBagLayout)jpArea.getLayout()).rowWeights = new double[] {0.0, 1.0E-4};

                                    //---- jlArea ----
                                    jlArea.setText("Area:");
                                    jpArea.add(jlArea, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                        new Insets(0, 0, 0, 5), 0, 0));

                                    //---- jsSelectedArea ----
                                    jsSelectedArea.setModel(new SpinnerNumberModel(0, 0, null, 1));
                                    jsSelectedArea.setFocusable(false);
                                    jsSelectedArea.setPreferredSize(new Dimension(40, 20));
                                    jsSelectedArea.setRequestFocusEnabled(false);
                                    jsSelectedArea.addChangeListener(e -> jsSelectedAreaStateChanged(e));
                                    jpArea.add(jsSelectedArea, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                        new Insets(0, 0, 0, 5), 0, 0));

                                    //======== jPanelAreaColor ========
                                    {
                                        jPanelAreaColor.setBackground(new Color(51, 102, 255));
                                        jPanelAreaColor.setBorder(new BevelBorder(BevelBorder.RAISED));
                                        jPanelAreaColor.setPreferredSize(new Dimension(30, 30));

                                        GroupLayout jPanelAreaColorLayout = new GroupLayout(jPanelAreaColor);
                                        jPanelAreaColor.setLayout(jPanelAreaColorLayout);
                                        jPanelAreaColorLayout.setHorizontalGroup(
                                            jPanelAreaColorLayout.createParallelGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                        );
                                        jPanelAreaColorLayout.setVerticalGroup(
                                            jPanelAreaColorLayout.createParallelGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                        );
                                    }
                                    jpArea.add(jPanelAreaColor, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                        new Insets(0, 0, 0, 0), 0, 0));
                                }
                                jpAreaTools.add(jpArea, "cell 0 1");

                                //======== jpMoveMap ========
                                {
                                    jpMoveMap.setBorder(new TitledBorder(null, "Move Map", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));
                                    jpMoveMap.setMaximumSize(new Dimension(110, 110));
                                    jpMoveMap.setMinimumSize(null);
                                    jpMoveMap.setPreferredSize(null);
                                    jpMoveMap.setLayout(new BorderLayout());

                                    //---- moveMapPanel ----
                                    moveMapPanel.setMaximumSize(null);
                                    moveMapPanel.setMinimumSize(null);
                                    moveMapPanel.setPreferredSize(null);
                                    jpMoveMap.add(moveMapPanel, BorderLayout.CENTER);
                                }
                                jpAreaTools.add(jpMoveMap, "cell 0 2,alignx center,growx 0");
                            }
                            jspMatrix.setTopComponent(jpAreaTools);

                            //======== jpTileSelected ========
                            {
                                jpTileSelected.setBorder(new TitledBorder("Tile Selected:"));
                                jpTileSelected.setLayout(new BoxLayout(jpTileSelected, BoxLayout.Y_AXIS));

                                //======== tileDisplay ========
                                {
                                    tileDisplay.setFocusable(false);
                                    tileDisplay.setPreferredSize(new Dimension(140, 140));

                                    GroupLayout tileDisplayLayout = new GroupLayout(tileDisplay);
                                    tileDisplay.setLayout(tileDisplayLayout);
                                    tileDisplayLayout.setHorizontalGroup(
                                        tileDisplayLayout.createParallelGroup()
                                            .addGap(0, 0, Short.MAX_VALUE)
                                    );
                                    tileDisplayLayout.setVerticalGroup(
                                        tileDisplayLayout.createParallelGroup()
                                            .addGap(0, 219, Short.MAX_VALUE)
                                    );
                                }
                                jpTileSelected.add(tileDisplay);
                            }
                            jspMatrix.setBottomComponent(jpTileSelected);
                        }
                        jPanelMatrixInfo.add(jspMatrix);
                    }
                    jtRightPanel.addTab("Matrix", jPanelMatrixInfo);

                    //======== jPanelMapTools ========
                    {
                        jPanelMapTools.setLayout(new MigLayout(
                            "insets 5,hidemode 3,gap 5 5",
                            // columns
                            "[grow,fill]",
                            // rows
                            "[fill]" +
                            "[fill]" +
                            "[fill]" +
                            "[fill]" +
                            "[fill]" +
                            "[fill]"));

                        //======== jpHeightMapAlpha ========
                        {
                            jpHeightMapAlpha.setBorder(new TitledBorder(null, "Height Map Alpha", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));

                            //---- jsHeightMapAlpha ----
                            jsHeightMapAlpha.setValue(99);
                            jsHeightMapAlpha.setFocusable(false);
                            jsHeightMapAlpha.addChangeListener(e -> jsHeightMapAlphaStateChanged(e));

                            GroupLayout jpHeightMapAlphaLayout = new GroupLayout(jpHeightMapAlpha);
                            jpHeightMapAlpha.setLayout(jpHeightMapAlphaLayout);
                            jpHeightMapAlphaLayout.setHorizontalGroup(
                                jpHeightMapAlphaLayout.createParallelGroup()
                                    .addComponent(jsHeightMapAlpha, GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
                            );
                            jpHeightMapAlphaLayout.setVerticalGroup(
                                jpHeightMapAlphaLayout.createParallelGroup()
                                    .addComponent(jsHeightMapAlpha, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                            );
                        }
                        jPanelMapTools.add(jpHeightMapAlpha, "cell 0 0");

                        //======== jpBackImageAlpha ========
                        {
                            jpBackImageAlpha.setBorder(new TitledBorder(null, "Back Image Alpha", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));

                            //---- jsBackImageAlpha ----
                            jsBackImageAlpha.setFocusable(false);
                            jsBackImageAlpha.addChangeListener(e -> jsBackImageAlphaStateChanged(e));

                            GroupLayout jpBackImageAlphaLayout = new GroupLayout(jpBackImageAlpha);
                            jpBackImageAlpha.setLayout(jpBackImageAlphaLayout);
                            jpBackImageAlphaLayout.setHorizontalGroup(
                                jpBackImageAlphaLayout.createParallelGroup()
                                    .addComponent(jsBackImageAlpha, GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                            );
                            jpBackImageAlphaLayout.setVerticalGroup(
                                jpBackImageAlphaLayout.createParallelGroup()
                                    .addComponent(jsBackImageAlpha, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                            );
                        }
                        jPanelMapTools.add(jpBackImageAlpha, "cell 0 1");

                        //======== jpMoveLayer ========
                        {
                            jpMoveLayer.setBorder(new TitledBorder(null, "Move Layer", TitledBorder.LEADING, TitledBorder.ABOVE_TOP));
                            jpMoveLayer.setLayout(new MigLayout(
                                "insets 0,hidemode 3,gap 0 0",
                                // columns
                                "[fill]" +
                                "[fill]",
                                // rows
                                "[center]"));

                            //======== jpDirectionalPad ========
                            {
                                jpDirectionalPad.setLayout(new MigLayout(
                                    "insets 0,hidemode 3,gap 3 3",
                                    // columns
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]",
                                    // rows
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]"));

                                //---- jbMoveMapUp ----
                                jbMoveMapUp.setForeground(new Color(0, 153, 0));
                                jbMoveMapUp.setFocusable(false);
                                jbMoveMapUp.setIcon(new ImageIcon(getClass().getResource("/icons/upGreenIcon.png")));
                                jbMoveMapUp.addActionListener(e -> jbMoveMapUpActionPerformed(e));
                                jpDirectionalPad.add(jbMoveMapUp, "cell 1 0");

                                //---- jbMoveMapLeft ----
                                jbMoveMapLeft.setForeground(new Color(204, 0, 0));
                                jbMoveMapLeft.setFocusable(false);
                                jbMoveMapLeft.setIcon(new ImageIcon(getClass().getResource("/icons/leftRedIcon.png")));
                                jbMoveMapLeft.addActionListener(e -> jbMoveMapLeftActionPerformed(e));
                                jpDirectionalPad.add(jbMoveMapLeft, "cell 0 1");

                                //---- jbMoveMapRight ----
                                jbMoveMapRight.setForeground(new Color(204, 0, 0));
                                jbMoveMapRight.setFocusable(false);
                                jbMoveMapRight.setIcon(new ImageIcon(getClass().getResource("/icons/rightRedIcon.png")));
                                jbMoveMapRight.addActionListener(e -> jbMoveMapRightActionPerformed(e));
                                jpDirectionalPad.add(jbMoveMapRight, "cell 2 1");

                                //---- jbMoveMapDown ----
                                jbMoveMapDown.setForeground(new Color(0, 153, 0));
                                jbMoveMapDown.setToolTipText("");
                                jbMoveMapDown.setFocusable(false);
                                jbMoveMapDown.setIcon(new ImageIcon(getClass().getResource("/icons/downGreenIcon.png")));
                                jbMoveMapDown.addActionListener(e -> jbMoveMapDownActionPerformed(e));
                                jpDirectionalPad.add(jbMoveMapDown, "cell 1 2");
                            }
                            jpMoveLayer.add(jpDirectionalPad, "cell 2 0");

                            //======== jpZPad ========
                            {
                                jpZPad.setLayout(new GridLayout(2, 0, 0, 3));

                                //---- jbMoveMapUpZ ----
                                jbMoveMapUpZ.setForeground(Color.blue);
                                jbMoveMapUpZ.setFocusable(false);
                                jbMoveMapUpZ.setIcon(new ImageIcon(getClass().getResource("/icons/upBlueIcon.png")));
                                jbMoveMapUpZ.addActionListener(e -> jbMoveMapUpZActionPerformed(e));
                                jpZPad.add(jbMoveMapUpZ);

                                //---- jbMoveMapDownZ ----
                                jbMoveMapDownZ.setForeground(Color.blue);
                                jbMoveMapDownZ.setFocusable(false);
                                jbMoveMapDownZ.setIcon(new ImageIcon(getClass().getResource("/icons/downBlueIcon.png")));
                                jbMoveMapDownZ.addActionListener(e -> jbMoveMapDownZActionPerformed(e));
                                jpZPad.add(jbMoveMapDownZ);
                            }
                            jpMoveLayer.add(jpZPad, "cell 3 0");
                        }
                        jPanelMapTools.add(jpMoveLayer, "cell 0 2,alignx center,growx 0");

                        //---- jcbRealTimePolyGrouping ----
                        jcbRealTimePolyGrouping.setSelected(true);
                        jcbRealTimePolyGrouping.setText("Real-Time Poly Grouping");
                        jcbRealTimePolyGrouping.addActionListener(e -> jcbRealTimePolyGroupingActionPerformed(e));
                        jPanelMapTools.add(jcbRealTimePolyGrouping, "cell 0 3");

                        //---- jcbViewAreas ----
                        jcbViewAreas.setSelected(true);
                        jcbViewAreas.setText("View Area Contours");
                        jcbViewAreas.addActionListener(e -> jcbViewAreasActionPerformed(e));
                        jPanelMapTools.add(jcbViewAreas, "cell 0 4");

                        //---- jcbViewGridsBorders ----
                        jcbViewGridsBorders.setSelected(true);
                        jcbViewGridsBorders.setText("View Grids Borders");
                        jcbViewGridsBorders.addActionListener(e -> jcbViewGridsBordersActionPerformed(e));
                        jPanelMapTools.add(jcbViewGridsBorders, "cell 0 5");
                    }
                    jtRightPanel.addTab("Map Tools", jPanelMapTools);
                }
                jpRightPanel.add(jtRightPanel);
            }
            jspMainWindow.setRightComponent(jpRightPanel);
        }
        contentPane.add(jspMainWindow, "cell 0 1");

        //======== jpStatusBar ========
        {
            jpStatusBar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));

            //---- jLabel4 ----
            jLabel4.setFont(new Font("Tahoma", Font.BOLD, 11));
            jLabel4.setText("Selected Map Info:");
            jpStatusBar.add(jLabel4);

            //---- jLabel6 ----
            jLabel6.setText("Coordinates:");
            jpStatusBar.add(jLabel6);

            //---- jlMapCoords ----
            jlMapCoords.setText(" ");
            jlMapCoords.setPreferredSize(new Dimension(40, 14));
            jpStatusBar.add(jlMapCoords);

            //---- jLabel2 ----
            jLabel2.setText("# Polygons:");
            jpStatusBar.add(jLabel2);

            //---- jlNumPolygons ----
            jlNumPolygons.setHorizontalAlignment(SwingConstants.LEFT);
            jlNumPolygons.setText(" ");
            jlNumPolygons.setPreferredSize(new Dimension(40, 14));
            jpStatusBar.add(jlNumPolygons);

            //---- jLabel5 ----
            jLabel5.setText("# Materials:");
            jpStatusBar.add(jLabel5);

            //---- jlNumMaterials ----
            jlNumMaterials.setHorizontalAlignment(SwingConstants.LEFT);
            jlNumMaterials.setText(" ");
            jlNumMaterials.setPreferredSize(new Dimension(40, 14));
            jpStatusBar.add(jlNumMaterials);
        }
        contentPane.add(jpStatusBar, "cell 0 2");
        pack();
        setLocationRelativeTo(getOwner());

        //---- buttonGroupViewMode ----
        ButtonGroup buttonGroupViewMode = new ButtonGroup();
        buttonGroupViewMode.add(jtbView3D);
        buttonGroupViewMode.add(jtbViewOrtho);
        buttonGroupViewMode.add(jtbViewHeight);

        //---- buttonGroupDrawMode ----
        ButtonGroup buttonGroupDrawMode = new ButtonGroup();
        buttonGroupDrawMode.add(jtbModeEdit);
        buttonGroupDrawMode.add(jtbModeClear);
        buttonGroupDrawMode.add(jtbModeSmartPaint);
        buttonGroupDrawMode.add(jtbModeInvSmartPaint);
        buttonGroupDrawMode.add(jtbModeMove);
        buttonGroupDrawMode.add(jtbModeZoom);
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
        traceInit("EXIT");
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JMenuBar jmMainMenu;
    private JMenu jmFile;
    private JMenuItem jmiNewMap;
    private JMenuItem jmiOpenMap;
    private JMenu jmiOpenRecentMap;
    private JMenuItem jmiClearHistory;
    private JMenuItem jmiSaveMap;
    private JMenuItem jmiSaveMapAs;
    private JMenuItem jmiSaveRbmap;
    private JMenuItem jmiAddMaps;
    private JMenuItem jmiExportObjWithText;
    private JMenuItem jmiExportMapAsImd;
    private JMenuItem jmiExportMapAsNsb;
    private JMenuItem jmiExportMapBtx;
    private JMenuItem jmiImportTileset;
    private JMenuItem jmiExportTileset;
    private JMenuItem jmiExportAllTiles;
    private JMenu jmEdit;
    private JMenuItem jmiUndo;
    private JMenuItem jmiRedo;
    private JMenuItem jmiClearLayer;
    private JMenuItem jmiClearAllLayers;
    private JMenuItem jmiCopyLayer;
    private JMenuItem jmiPasteLayer;
    private JMenuItem jmiPasteLayerTiles;
    private JMenuItem jmiPasteLayerHeights;
    private JMenuItem menuItem1;
    private JMenu jmView;
    private JMenuItem jmi3dView;
    private JMenuItem jmiTopView;
    private JMenuItem jmiHeightView;
    private JMenuItem jmiToggleGrid;
    private JMenuItem jmiLoadBackImg;
    private JCheckBoxMenuItem jcbUseBackImage;
    private JMenu jmTools;
    private JMenuItem jmiTilesetEditor;
    private JMenuItem jmiDumpTilesetAsPngs;
    private JMenuItem jmiCollisionEditor;
    private JMenuItem jmiBdhcEditor;
    private JMenuItem jmiBDHCAM;
    private JMenuItem jmiNsbtxEditor;
    private JMenuItem jMenuItem1;
    private JMenuItem jmiAnimationEditor;
    private JMenu jmHelp;
    private JMenuItem jmiKeyboardInfo;
    private JMenuItem jmiAbout;
    private JToolBar jtMainToolbar;
    private JButton jbNewMap;
    private JButton jbOpenMap;
    private JButton jbSaveMap;
    private JButton jbAddMaps;
    private JButton jbUndo;
    private JButton jbRedo;
    private JButton jbExportObj;
    private JButton jbExportImd;
    private JButton jbExportNsb;
    private JButton jbExportBin;
    private JButton jbExportNsb1;
    private JButton jbExportNsb2;
    private JButton jbTilelistEditor;
    private JButton jbCollisionsEditor;
    private JButton jbBdhcEditor;
    private JButton jbBdhcamEditor;
    private JButton jbBacksoundEditor;
    private JButton jbNsbtxEditor1;
    private JButton jbBuildingEditor;
    private JButton jbAnimationEditor;
    private JButton jbSettings;
    private JButton jbKeboardInfo;
    private JButton jbHelp;
    private JPanel jpGameInfo;
    private JLabel jlGame;
    private JLabel jlGameIcon;
    private JLabel jlGameName;
    private JSplitPane jspMainWindow;
    private JPanel jpMainWindow;
    private JPanel jpLayer;
    private ThumbnailLayerSelector thumbnailLayerSelector;
    private JPanel mapDisplayContainer;
    private MapDisplay mapDisplay;
    private JPanel jpZ;
    private HeightSelector heightSelector;
    private JPanel jpTileList;
    private JScrollPane jscTileList;
    private TileSelector tileSelector;
    private JPanel jpSmartDrawing;
    private JScrollPane jscSmartDrawing;
    private SmartGridDisplay smartGridDisplay;
    private JPanel jpButtons;
    private JPanel jpView;
    private JToolBar jtView;
    private JToggleButton jtbView3D;
    private JToggleButton jtbViewOrtho;
    private JToggleButton jtbViewHeight;
    private JToggleButton jtbViewGrid;
    private JToggleButton jtbViewWireframe;
    private JPanel jpTools;
    private JToolBar jtTools;
    private JToggleButton jtbModeEdit;
    private JToggleButton jtbModeClear;
    private JToggleButton jtbModeSmartPaint;
    private JToggleButton jtbModeInvSmartPaint;
    private JToggleButton jtbModeMove;
    private JToggleButton jtbModeZoom;
    private JButton jbFitCameraToMap;
    private JPanel jpRightPanel;
    private JTabbedPane jtRightPanel;
    private JPanel jPanelMatrixInfo;
    private JSplitPane jspMatrix;
    private JPanel jpAreaTools;
    private JScrollPane jScrollPaneMapMatrix;
    private MapMatrixDisplay mapMatrixDisplay;
    private JPanel jpArea;
    private JLabel jlArea;
    private JSpinner jsSelectedArea;
    private JPanel jPanelAreaColor;
    private JPanel jpMoveMap;
    private MoveMapPanel moveMapPanel;
    private JPanel jpTileSelected;
    private TileDisplay tileDisplay;
    private JPanel jPanelMapTools;
    private JPanel jpHeightMapAlpha;
    private JSlider jsHeightMapAlpha;
    private JPanel jpBackImageAlpha;
    private JSlider jsBackImageAlpha;
    private JPanel jpMoveLayer;
    private JPanel jpDirectionalPad;
    private JButton jbMoveMapUp;
    private JButton jbMoveMapLeft;
    private JButton jbMoveMapRight;
    private JButton jbMoveMapDown;
    private JPanel jpZPad;
    private JButton jbMoveMapUpZ;
    private JButton jbMoveMapDownZ;
    private JCheckBox jcbRealTimePolyGrouping;
    private JCheckBox jcbViewAreas;
    private JCheckBox jcbViewGridsBorders;
    private JPanel jpStatusBar;
    private JLabel jLabel4;
    private JLabel jLabel6;
    private JLabel jlMapCoords;
    private JLabel jLabel2;
    private JLabel jlNumPolygons;
    private JLabel jLabel5;
    private JLabel jlNumMaterials;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
