package editor.mapdisplay;

import com.jogamp.opengl.GLContext;
import editor.handler.MapEditorHandler;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import utils.StartupTrace;

/**
 * Swing map preview with optional embedded {@link MapDisplayGL} when
 * {@code -Dpdsm.enableJoglMap=true} or legacy {@code -Dpdsm.enableJogl=true}.
 */
public class MapDisplay extends JPanel
        implements MouseListener, MouseMotionListener, KeyListener, MouseWheelListener {

    private final boolean joglEnabled;
    private MapDisplayGL glDisplay;

    protected MapEditorHandler handler;
    protected BufferedImage screenshot;
    protected boolean updateRequested = false;
    protected boolean screenshotRequested = false;
    protected BufferedImage backImage = null;
    protected boolean backImageEnabled = false;
    protected float backImageAlpha = 0.5f;
    protected boolean drawGridEnabled = true;
    protected boolean drawWireframeEnabled = false;
    protected boolean drawAreasEnabled = true;
    protected boolean drawGridBorderMaps = true;

    public enum EditMode {
        MODE_EDIT(new Cursor(Cursor.DEFAULT_CURSOR)),
        MODE_MOVE(new Cursor(Cursor.MOVE_CURSOR)),
        MODE_ZOOM(new Cursor(Cursor.DEFAULT_CURSOR)),
        MODE_CLEAR(new Cursor(Cursor.DEFAULT_CURSOR)),
        MODE_SMART_PAINT(new Cursor(Cursor.DEFAULT_CURSOR)),
        MODE_INV_SMART_PAINT(new Cursor(Cursor.DEFAULT_CURSOR));

        public final Cursor cursor;

        EditMode(Cursor cursor) {
            this.cursor = cursor;
        }
    }

    protected EditMode editMode = EditMode.MODE_EDIT;
    protected ViewMode viewMode = ViewMode.VIEW_ORTHO_MODE;

    public MapDisplay() {
        joglEnabled = Boolean.getBoolean("pdsm.enableJoglMap")
                || Boolean.getBoolean("pdsm.enableJogl");

        StartupTrace.log("MapDisplay wrapper: enter joglEnabled=" + joglEnabled
                + " thread=" + Thread.currentThread().getName());
        StartupTrace.log("MapDisplay wrapper: property pdsm.enableJoglMap="
                + System.getProperty("pdsm.enableJoglMap")
                + " pdsm.enableJogl=" + System.getProperty("pdsm.enableJogl"));

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(544, 544));
        setMinimumSize(new Dimension(256, 256));
        setSize(new Dimension(544, 544));
        setFocusable(true);

        if (joglEnabled) {
            try {
                StartupTrace.log("MapDisplay wrapper: before new MapDisplayGL");
                glDisplay = new MapDisplayGL();
                StartupTrace.log("MapDisplay wrapper: after new MapDisplayGL");

                glDisplay.setPreferredSize(new Dimension(544, 544));
                glDisplay.setMinimumSize(new Dimension(64, 64));
                add(glDisplay, BorderLayout.CENTER);
                StartupTrace.log("MapDisplay wrapper: added MapDisplayGL");
            } catch (Throwable t) {
                StartupTrace.log("MapDisplay wrapper: MapDisplayGL failed, falling back: " + t);
                t.printStackTrace();
                glDisplay = null;
                installFallbackListeners();
            }
        } else {
            installFallbackListeners();
        }

        StartupTrace.log("MapDisplay wrapper: glDisplay=" + glDisplay);
        StartupTrace.log("MapDisplay wrapper: exit");
    }

    private void installFallbackListeners() {
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        addMouseWheelListener(this);
    }

    @Override
    public void repaint() {
        super.repaint();
        if (glDisplay != null) {
            glDisplay.repaint();
        }
    }

    public void setHandler(MapEditorHandler handler) {
        this.handler = handler;
        if (glDisplay != null) {
            glDisplay.setHandler(handler);
        }
        repaint();
    }

    public MapEditorHandler getHandler() {
        if (glDisplay != null) {
            return glDisplay.getHandler();
        }
        return handler;
    }

    public EditMode getEditMode() {
        if (glDisplay != null) {
            return glDisplay.getEditMode();
        }
        return editMode;
    }

    public void requestUpdate() {
        if (glDisplay != null) {
            glDisplay.requestUpdate();
            return;
        }
        updateRequested = true;
        repaint();
    }

    public void requestScreenshot() {
        if (glDisplay != null) {
            glDisplay.requestScreenshot();
            return;
        }
        screenshotRequested = true;
        screenshot = new BufferedImage(
                Math.max(1, getWidth()),
                Math.max(1, getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = screenshot.createGraphics();
        paint(g);
        g.dispose();
    }

    public BufferedImage getScreenshot() {
        if (glDisplay != null) {
            BufferedImage img = glDisplay.getScreenshot();
            if (img == null) {
                glDisplay.requestScreenshot();
                glDisplay.display();
                img = glDisplay.getScreenshot();
            }
            return img;
        }
        if (screenshot == null) {
            requestScreenshot();
        }
        return screenshot;
    }

    public void display() {
        if (glDisplay != null) {
            glDisplay.display();
            return;
        }
        repaint();
    }

    public GLContext getContext() {
        if (glDisplay != null) {
            return glDisplay.getContext();
        }
        return null;
    }

    public void setContext(GLContext context, boolean destroyPrevious) {
        if (glDisplay != null) {
            glDisplay.setContext(context, destroyPrevious);
        }
    }

    public void updateMapLayersGL() {
        if (glDisplay != null) {
            glDisplay.updateMapLayersGL();
            return;
        }
        requestUpdate();
    }

    public void updateActiveMapLayerGL() {
        if (glDisplay != null) {
            glDisplay.updateActiveMapLayerGL();
            return;
        }
        requestUpdate();
    }

    public void updateMapLayerGL(int layerIndex) {
        if (glDisplay != null) {
            glDisplay.updateMapLayerGL(layerIndex);
            return;
        }
        requestUpdate();
    }

    public void set3DView() {
        if (glDisplay != null) {
            glDisplay.set3DView();
            return;
        }
        viewMode = ViewMode.VIEW_3D_MODE;
        repaint();
    }

    public void setOrthoView() {
        if (glDisplay != null) {
            glDisplay.setOrthoView();
            return;
        }
        viewMode = ViewMode.VIEW_ORTHO_MODE;
        repaint();
    }

    public void setHeightView() {
        if (glDisplay != null) {
            glDisplay.setHeightView();
            return;
        }
        viewMode = ViewMode.VIEW_HEIGHT_MODE;
        repaint();
    }

    public ViewMode getViewMode() {
        if (glDisplay != null) {
            return glDisplay.getViewMode();
        }
        return viewMode;
    }

    public void setCameraAtSelectedMap() {
        if (glDisplay != null) {
            glDisplay.setCameraAtSelectedMap();
            return;
        }
        repaint();
    }

    public void setCameraAtMap(Point mapCoords) {
        if (glDisplay != null) {
            glDisplay.setCameraAtMap(mapCoords);
            return;
        }
        repaint();
    }

    public void toggleGridView() {
        if (glDisplay != null) {
            glDisplay.toggleGridView();
            return;
        }
        drawGridEnabled = !drawGridEnabled;
        repaint();
    }

    public void disableGridView() {
        if (glDisplay != null) {
            glDisplay.disableGridView();
            return;
        }
        drawGridEnabled = false;
        repaint();
    }

    public boolean isGridEnabled() {
        if (glDisplay != null) {
            return glDisplay.isGridEnabled();
        }
        return drawGridEnabled;
    }

    public void setGridEnabled(boolean enabled) {
        if (glDisplay != null) {
            glDisplay.setGridEnabled(enabled);
            return;
        }
        drawGridEnabled = enabled;
        repaint();
    }

    public void setDrawWireframeEnabled(boolean enabled) {
        if (glDisplay != null) {
            glDisplay.setDrawWireframeEnabled(enabled);
            return;
        }
        drawWireframeEnabled = enabled;
        repaint();
    }

    public void setDrawAreasEnabled(boolean enabled) {
        if (glDisplay != null) {
            glDisplay.setDrawAreasEnabled(enabled);
            return;
        }
        drawAreasEnabled = enabled;
        repaint();
    }

    public void setDrawGridBorderMaps(boolean enabled) {
        if (glDisplay != null) {
            glDisplay.setDrawGridBorderMaps(enabled);
            return;
        }
        drawGridBorderMaps = enabled;
        repaint();
    }

    public void setEditMode(EditMode mode) {
        editMode = mode;
        if (glDisplay != null) {
            glDisplay.setEditMode(mode);
            return;
        }
        setCursor(mode.cursor);
    }

    public void setHeightMapAlpha(float value) {
        if (glDisplay != null) {
            glDisplay.setHeightMapAlpha(value);
            return;
        }
        repaint();
    }

    public void setBackImageAlpha(float value) {
        backImageAlpha = value;
        if (glDisplay != null) {
            glDisplay.setBackImageAlpha(value);
            return;
        }
        repaint();
    }

    public void setBackImage(BufferedImage image) {
        backImage = image;
        if (glDisplay != null) {
            glDisplay.setBackImage(image);
            return;
        }
        repaint();
    }

    public void setBackImageEnabled(boolean enabled) {
        backImageEnabled = enabled;
        if (glDisplay != null) {
            glDisplay.setBackImageEnabled(enabled);
            return;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (glDisplay != null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(32, 48, 64));
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (backImageEnabled && backImage != null) {
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(backImageAlpha));
            g2.drawImage(backImage, 0, 0, getWidth(), getHeight(), null);
            g2.setComposite(old);
        }

        g2.setColor(Color.WHITE);
        g2.drawString("OpenGL map preview disabled (Swing fallback mode)", 16, 24);
        g2.drawString("The editor window is running; JOGL MapDisplayGL is bypassed.", 16, 44);

        if (handler != null) {
            g2.drawString("Game/editor handler initialized.", 16, 64);
        }

        g2.dispose();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!hasFocus()) {
            requestFocusInWindow();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
