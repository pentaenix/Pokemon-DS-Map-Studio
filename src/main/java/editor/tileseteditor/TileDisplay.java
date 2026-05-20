package editor.tileseteditor;

import com.jogamp.opengl.GLContext;

import editor.handler.MapEditorHandler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import utils.StartupTrace;

/**
 * Swing tile preview with optional embedded {@link TileDisplayGL} when
 * {@code -Dpdsm.enableJoglTile=true} or legacy {@code -Dpdsm.enableJogl=true}.
 */
public class TileDisplay extends JPanel {

    private final boolean joglEnabled;
    private TileDisplayGL glDisplay;

    private MapEditorHandler handler;
    private boolean wireframe;
    private boolean updateRequested;
    private BufferedImage screenshot;

    private boolean backfaceCullingEnabled = true;
    private boolean texturesEnabled = true;
    private boolean lightingEnabled = false;
    private boolean normalsEnabled = false;

    public TileDisplay() {
        joglEnabled = Boolean.getBoolean("pdsm.enableJoglTile")
                || Boolean.getBoolean("pdsm.enableJogl");

        StartupTrace.log("TileDisplay wrapper: enter joglEnabled=" + joglEnabled
                + " thread=" + Thread.currentThread().getName());
        StartupTrace.log("TileDisplay wrapper: property pdsm.enableJoglTile="
                + System.getProperty("pdsm.enableJoglTile")
                + " pdsm.enableJogl=" + System.getProperty("pdsm.enableJogl"));

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(160, 160));
        setMinimumSize(new Dimension(96, 96));
        setFocusable(false);

        if (joglEnabled) {
            try {
                StartupTrace.log("TileDisplay wrapper: before new TileDisplayGL");
                glDisplay = new TileDisplayGL();
                StartupTrace.log("TileDisplay wrapper: after new TileDisplayGL");

                glDisplay.setPreferredSize(new Dimension(160, 160));
                glDisplay.setMinimumSize(new Dimension(64, 64));
                add(glDisplay, BorderLayout.CENTER);
                traceTileLayout("after add glDisplay");
                StartupTrace.log("TileDisplay wrapper: added TileDisplayGL");
            } catch (Throwable t) {
                StartupTrace.log("TileDisplay wrapper: TileDisplayGL failed, falling back: " + t);
                t.printStackTrace();
                glDisplay = null;
            }
        }

        StartupTrace.log("TileDisplay wrapper: glDisplay=" + glDisplay);
        StartupTrace.log("TileDisplay wrapper: exit");
    }

    private void traceTileLayout(String where) {
        if (!Boolean.getBoolean("pdsm.traceLayout")) {
            return;
        }
        System.out.println("[PDSM] tile-layout " + where
                + " wrapper.size=" + getSize()
                + " wrapper.pref=" + getPreferredSize()
                + " parent.size=" + (getParent() == null ? null : getParent().getSize())
                + " gl.size=" + (glDisplay == null ? null : glDisplay.getSize())
                + " gl.pref=" + (glDisplay == null ? null : glDisplay.getPreferredSize()));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        traceTileLayout("addNotify");
        if (glDisplay != null) {
            glDisplay.repaint();
        }
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
            glDisplay.requestUpdate();
            glDisplay.repaint();
            traceTileLayout("setHandler");
            return;
        }
        repaint();
    }

    public MapEditorHandler getHandler() {
        if (glDisplay != null) {
            return glDisplay.getHandler();
        }
        return handler;
    }

    public void setWireframe(boolean wireframe) {
        this.wireframe = wireframe;
        if (glDisplay != null) {
            glDisplay.setWireframe(wireframe);
            return;
        }
        repaint();
    }

    public boolean isWireframe() {
        if (glDisplay != null) {
            return glDisplay.isWireframe();
        }
        return wireframe;
    }

    public void requestUpdate() {
        if (glDisplay != null) {
            glDisplay.requestUpdate();
            glDisplay.repaint();
            return;
        }
        updateRequested = true;
        repaint();
    }

    public void updateTileGL() {
        if (glDisplay != null) {
            glDisplay.updateTileGL();
            return;
        }
        requestUpdate();
    }

    public void updateGL() {
        if (glDisplay != null) {
            glDisplay.updateGL();
            return;
        }
        requestUpdate();
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

    public void requestScreenshot() {
        if (glDisplay != null) {
            screenshot = new BufferedImage(
                    Math.max(1, getWidth()),
                    Math.max(1, getHeight()),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = screenshot.createGraphics();
            printAll(g);
            g.dispose();
            return;
        }
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
            if (screenshot == null) {
                requestScreenshot();
            }
            return screenshot;
        }
        if (screenshot == null) {
            requestScreenshot();
        }
        return screenshot;
    }

    public void swapVBOs(int index1, int index2) {
        if (glDisplay != null) {
            glDisplay.swapVBOs(index1, index2);
            return;
        }
        repaint();
    }

    public void swapTextures(int index1, int index2) {
        if (glDisplay != null) {
            glDisplay.swapTextures(index1, index2);
            return;
        }
        repaint();
    }

    public void setBackfaceCulling(boolean enabled) {
        this.backfaceCullingEnabled = enabled;
        if (glDisplay != null) {
            glDisplay.setBackfaceCulling(enabled);
            return;
        }
        repaint();
    }

    public void setLightingEnabled(boolean lightingEnabled) {
        this.lightingEnabled = lightingEnabled;
        if (glDisplay != null) {
            glDisplay.setLightingEnabled(lightingEnabled);
            return;
        }
        repaint();
    }

    public boolean isLightingEnabled() {
        if (glDisplay != null) {
            return glDisplay.isLightingEnabled();
        }
        return lightingEnabled;
    }

    public void setTexturesEnabled(boolean texturesEnabled) {
        this.texturesEnabled = texturesEnabled;
        if (glDisplay != null) {
            glDisplay.setTexturesEnabled(texturesEnabled);
            return;
        }
        repaint();
    }

    public boolean isTexturesEnabled() {
        if (glDisplay != null) {
            return glDisplay.isTexturesEnabled();
        }
        return texturesEnabled;
    }

    public void setNormalsEnabled(boolean normalsEnabled) {
        this.normalsEnabled = normalsEnabled;
        if (glDisplay != null) {
            glDisplay.setNormalsEnabled(normalsEnabled);
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
        g2.setColor(new Color(48, 48, 56));
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.WHITE);
        g2.drawString("Tile preview disabled", 12, 22);
        g2.drawString("Swing fallback mode", 12, 42);

        if (handler != null) {
            g2.drawString("Handler initialized", 12, 62);
        }

        updateRequested = false;
        g2.dispose();
    }
}
