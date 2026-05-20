
package editor.tileseteditor;

import static com.jogamp.opengl.GL.GL_BACK;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_CULL_FACE;
import static com.jogamp.opengl.GL.GL_CW;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_FRONT;
import static com.jogamp.opengl.GL.GL_GREATER;
import static com.jogamp.opengl.GL.GL_LEQUAL;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_NOTEQUAL;
import static com.jogamp.opengl.GL.GL_REPEAT;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_TRUE;
import static com.jogamp.opengl.GL2.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL2.GL_BLEND;
import static com.jogamp.opengl.GL2.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL2.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL2.GL_FLOAT;
import static com.jogamp.opengl.GL2.GL_FRONT_AND_BACK;
import static com.jogamp.opengl.GL2.GL_LEQUAL;
import static com.jogamp.opengl.GL2.GL_LESS;
import static com.jogamp.opengl.GL2.GL_LINES;
import static com.jogamp.opengl.GL2.GL_NEAREST;
import static com.jogamp.opengl.GL2.GL_ONE_MINUS_DST_ALPHA;
import static com.jogamp.opengl.GL2.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL2.GL_REPEAT;
import static com.jogamp.opengl.GL2.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL2.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL2.GL_TEXTURE0;
import static com.jogamp.opengl.GL2.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL2.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL2.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL2.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL2.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL2.GL_TRIANGLES;

import com.jogamp.opengl.GL2;

import static com.jogamp.opengl.GL2ES1.GL_ALPHA_TEST;
import static com.jogamp.opengl.GL2ES1.GL_LIGHT_MODEL_AMBIENT;
import static com.jogamp.opengl.GL2ES1.GL_LIGHT_MODEL_TWO_SIDE;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import static com.jogamp.opengl.GL2ES3.GL_QUADS;
import static com.jogamp.opengl.GL2GL3.GL_FILL;
import static com.jogamp.opengl.GL2GL3.GL_LINE;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_AMBIENT;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_DIFFUSE;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHT0;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHT1;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHT2;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHTING;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_NORMALIZE;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_POSITION;

import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.ImageUtil;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import editor.handler.MapEditorHandler;
import geometry.Generator;
import graphicslib3D.Matrix3D;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import tileset.Tile;
import tileset.Tileset;
import utils.GlUtils;
import utils.StartupTrace;
import utils.Utils;

/**
 * @author Trifindo
 */
public class TileDisplayGL extends GLCanvas implements GLEventListener, MouseListener, MouseMotionListener, KeyListener, MouseWheelListener {

    static {
        StartupTrace.log("TileDisplayGL: static init thread=" + Thread.currentThread().getName());
    }

    private static final AtomicBoolean TRACE_FIRST_GL_INIT = new AtomicBoolean();

    private boolean loggedFirstDisplay = false;

    /** Drawable (backing-store) GL viewport size from reshape; aspect only. */
    private int glDrawableViewportWidth = 1;
    private int glDrawableViewportHeight = 1;

    private final int[] inputViewportScratch = new int[4];
    private static final AtomicInteger TILE_VIEWPORT_LOG = new AtomicInteger();

    private static final boolean SKIP_GL_FINISH = Boolean.parseBoolean(System.getProperty("pdsm.skip.glFinish", "false"));

    //Map Editor Handler
    private MapEditorHandler handler;

    //OpenGL
    private GLU glu;
    private float[] grid;
    private float[] axis;
    private final float[] axisColors = {
            1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f};
    private ArrayList<Texture> textures = new ArrayList<>();

    //Scene
    private float cameraX, cameraY, cameraZ;
    private float modelRotX, modelRotY, modelRotZ;
    private boolean orthoEnabled = false;
    private boolean drawGridEnabled = true;

    //Mouse Events
    private boolean dragging = false;
    private int lastMouseX, lastMouseY;

    // Update Display
    private boolean updateRequested = false;

    //Display Mode
    private boolean wireframeEnabled = true;
    private boolean backfaceCullingEnabled = true;
    private boolean texturesEnabled = true;
    private boolean lightingEnabled = false;
    private boolean normalsEnabled = false;

    private float normalScale = 0.35f;

    public TileDisplayGL() {
        super(new GLCapabilities(GLProfile.get(GLProfile.GL2)));

        StartupTrace.log("TileDisplayGL.<init>: enter thread=" + Thread.currentThread().getName());

        //Add listeners
        addGLEventListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        addMouseWheelListener(this);

        //Set focusable for keyListener
        setFocusable(true);

        StartupTrace.log("TileDisplayGL.<init>: exit");
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        StartupTrace.log("TileDisplayGL.init: enter thread=" + Thread.currentThread().getName()
                + " handler=" + handler);

        if (TRACE_FIRST_GL_INIT.compareAndSet(false, true)) {
            StartupTrace.log("TileDisplay: GL init — first");
        }
        glu = new GLU();

        grid = Generator.generateCenteredGrid(Tile.maxTileSize, Tile.maxTileSize, 1.0f, -0.01f);
        axis = Generator.generateAxis(100.0f);

        drawable.getGL().getGL2().glClearColor(0.0f, 0.5f, 0.5f, 1.0f);

        cameraX = 0.0f;
        cameraY = 0.0f;
        cameraZ = 8.0f;

        modelRotX = -30.0f;
        modelRotY = 0.0f;
        modelRotZ = 0.0f;

        if (handler == null) {
            StartupTrace.log("TileDisplayGL.init: handler null; deferring tile GL setup");
            updateRequested = true;
            StartupTrace.log("TileDisplayGL.init: exit");
            return;
        }

        loadTexturesGL();

        StartupTrace.log("TileDisplayGL.init: exit");
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {

    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        if (handler == null) {
            gl.glClearColor(0.15f, 0.15f, 0.18f, 1.0f);
            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            return;
        }

        if (!loggedFirstDisplay) {
            StartupTrace.log("TileDisplayGL.display: first frame");
            loggedFirstDisplay = true;
        }

        gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (updateRequested) {
            //renderVboTile = new boolean[handler.getTileset().size() * vbosPerTile];

            //handler.getTileset().updateTextures(gl, "res/tileset");
            //handler.getTileset().loadTexturesGL();
            loadTexturesGL();

            //Load Textures into OpenGL
            //handler.getTileset().loadTextures("res/tileset");
            updateRequested = false;

        }

        //Draw grid
        drawGrid();

        //Draw axis
        drawAxis();

        //Draw tiles
        if (handler.getTileset().size() > 0) {
            if (lightingEnabled) {
                gl.glLoadIdentity();

                gl.glEnable(GL2.GL_LIGHTING);
                gl.glEnable(GL2.GL_LIGHT0);

                //float[] ambientLight = {1f, 1f, 1f, 0f};
                //gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambientLight, 0);

                //float[] specularLight = {1f, 1f, 1f, 1f};
                //gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, specularLight, 0);

                //float[] diffuseLight = {1f, 1f, 1f, 0f};
                //gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuseLight, 0);

                //float[] emissionLight = {1f, 1f, 1f, 0f};
                //gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_EMISSION, emissionLight, 0);

                gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, new float[]{1.0f, 1.0f, 1.0f, 0.0f}, 0);
                gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[]{-1.0f, 1.0f, -0.05f, 0.0f}, 0);
            }

            drawOpaque();
            drawTransparent();

            if(normalsEnabled){
                drawNormals(gl);
            }

            if (lightingEnabled) {
                gl.glDisable(GL2.GL_LIGHTING);
                gl.glDisable(GL2.GL_LIGHT0);
            }

            if (wireframeEnabled) {
                drawWireframe();
            }
        }

        if (SKIP_GL_FINISH) {
            gl.glFlush();
        } else {
            gl.glFinish();
        }
    }

    /** Logical letterbox matching {@link java.awt.event.MouseEvent} coordinates (HiDPI-safe). */
    private void fillInputViewportForMouse(int[] out) {
        int cw = Math.max(1, getWidth());
        int ch = Math.max(1, getHeight());
        int s = Math.max(1, Math.min(cw, ch));
        out[0] = Math.max(0, (cw - s) / 2);
        out[1] = Math.max(0, (ch - s) / 2);
        out[2] = s;
        out[3] = s;
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        StartupTrace.log("TileDisplayGL.reshape: drawable=" + width + "x" + height
                + " logical=" + getWidth() + "x" + getHeight());
        if (width <= 0 || height <= 0) {
            return;
        }
        int glSize = Math.max(1, Math.min(width, height));
        int glVx = Math.max(0, (width - glSize) / 2);
        int glVyTop = Math.max(0, (height - glSize) / 2);
        int glOglY = height - glVyTop - glSize;
        drawable.getGL().getGL2().glViewport(glVx, glOglY, glSize, glSize);
        glDrawableViewportWidth = glSize;
        glDrawableViewportHeight = glSize;
        if (TILE_VIEWPORT_LOG.getAndIncrement() < 5) {
            StartupTrace.log("TileDisplayGL.viewport: drawable " + glVx + "," + glOglY + " "
                    + glSize + "x" + glSize);
        }
    }

    private float getDrawableAspect() {
        if (glDrawableViewportWidth > 0 && glDrawableViewportHeight > 0) {
            return (float) glDrawableViewportWidth / (float) glDrawableViewportHeight;
        }
        int h = Math.max(1, getHeight());
        return (float) getWidth() / (float) h;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float normalizedMouseX(int mouseX) {
        fillInputViewportForMouse(inputViewportScratch);
        int iw = inputViewportScratch[2];
        if (iw <= 0) {
            return (float) mouseX / (float) Math.max(1, getWidth());
        }
        int rel = clampInt(mouseX - inputViewportScratch[0], 0, iw - 1);
        return rel / (float) iw;
    }

    private float normalizedMouseY(int mouseY) {
        fillInputViewportForMouse(inputViewportScratch);
        int ih = inputViewportScratch[3];
        if (ih <= 0) {
            return (float) mouseY / (float) Math.max(1, getHeight());
        }
        int rel = clampInt(mouseY - inputViewportScratch[1], 0, ih - 1);
        return rel / (float) ih;
    }

    private static double wheelEffectiveRotation(MouseWheelEvent e) {
        double p = e.getPreciseWheelRotation();
        if (p != 0.0) {
            return p;
        }
        return e.getWheelRotation();
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
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
        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
            float delta = 100.0f;
            modelRotZ += (normalizedMouseX(e.getX()) - normalizedMouseX(lastMouseX)) * delta;
            lastMouseX = e.getX();
            modelRotX += (normalizedMouseY(e.getY()) - normalizedMouseY(lastMouseY)) * delta;
            lastMouseY = e.getY();
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (orthoEnabled) {
                orthoEnabled = false;
            } else {
                orthoEnabled = true;
            }
            repaint();
        }

        if (e.getKeyCode() == KeyEvent.VK_G) {
            drawGridEnabled = !drawGridEnabled;
            repaint();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double eff = wheelEffectiveRotation(e);
        if (eff == 0.0) {
            return;
        }
        cameraZ *= Math.pow(1.1, eff);
        repaint();
    }

    public void requestUpdate() {
        updateRequested = true;
    }

    private void drawGrid() {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glLoadIdentity();

        if (orthoEnabled) {
            float v = 6.0f;
            gl.glOrtho(-v, v, -v, v, -100.0f, 100.0f);
        } else {
            float aspect = getDrawableAspect();
            glu.gluPerspective(60.0f, aspect, 1.0f, 1000.0f);
        }

        gl.glTranslatef(-cameraX, -cameraY, -cameraZ); // translate into the screen
        gl.glRotatef(modelRotX, 1.0f, 0.0f, 0.0f); // rotate about the x-axis
        gl.glRotatef(modelRotY, 0.0f, 1.0f, 0.0f); // rotate about the y-axis
        gl.glRotatef(modelRotZ, 0.0f, 0.0f, 1.0f); // rotate about the z-axis

        final int offset = Tile.maxTileSize / 2;
        gl.glTranslatef(offset, offset, 0.0f); // translate into the screen

        //Adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LEQUAL);
        gl.glLineWidth(1);

        gl.glDisable(GL_TEXTURE_2D);
        gl.glColor3f(1, 1, 1);

        final int coordsPerVertex = 3;
        final int vertexPerLine = 2;
        final int coordsPerLine = coordsPerVertex * vertexPerLine;
        gl.glBegin(GL_LINES);
        for (int i = 0; i < grid.length; i += coordsPerLine) {
            gl.glVertex3fv(grid, i);
            gl.glVertex3fv(grid, i + coordsPerVertex);
        }
        gl.glEnd();
    }

    private void drawAxis() {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glLoadIdentity();

        if (orthoEnabled) {
            float v = 6.0f;
            gl.glOrtho(-v, v, -v, v, -100.0f, 100.0f);
        } else {
            float aspect = getDrawableAspect();
            glu.gluPerspective(60.0f, aspect, 1.0f, 1000.0f);
        }

        gl.glTranslatef(-cameraX, -cameraY, -cameraZ); // translate into the screen
        gl.glRotatef(modelRotX, 1.0f, 0.0f, 0.0f); // rotate about the x-axis
        gl.glRotatef(modelRotY, 0.0f, 1.0f, 0.0f); // rotate about the y-axis
        gl.glRotatef(modelRotZ, 0.0f, 0.0f, 1.0f); // rotate about the z-axis

        gl.glDisable(GL_TEXTURE_2D);

        //Adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LEQUAL);

        gl.glBegin(GL_LINES);
        for (int i = 0; i < axis.length; i += 3) {
            gl.glColor3fv(axisColors, i);
            gl.glVertex3fv(axis, i);
        }

        gl.glEnd();
    }

    public void drawTile(boolean useWireframe) {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glLoadIdentity();
        if (orthoEnabled) {
            float v = 6.0f;
            gl.glOrtho(-v, v, -v, v, -100.0f, 100.0f);
        } else {
            float aspect = getDrawableAspect();
            glu.gluPerspective(60.0f, aspect, 1.0f, 1000.0f);
        }

        gl.glTranslatef(-cameraX, -cameraY, -cameraZ); // translate into the screen

        gl.glRotatef(modelRotX, 1.0f, 0.0f, 0.0f); // rotate about the x-axis
        gl.glRotatef(modelRotY, 0.0f, 1.0f, 0.0f); // rotate about the y-axis
        gl.glRotatef(modelRotZ, 0.0f, 0.0f, 1.0f); // rotate about the z-axis

        Tile tile = handler.getTileset().get(handler.getTileIndexSelected());

        drawQuads(gl, tile, useWireframe);
        drawTris(gl, tile, useWireframe);

    }

    private void drawQuads(GL2 gl, Tile tile, boolean useWireframe) {
        if (!(tile.getVCoordsQuad().length > 0 && tile.getTCoordsQuad().length > 0 && tile.getColorsQuad().length > 0)) {
            return;
        }

        for (int k = 0; k < tile.getTextureIDs().size(); k++) {
            // activate texture unit #0 and bind it to the brick texture object
            //gl.glActiveTexture(GL_TEXTURE0);
            if (texturesEnabled && !useWireframe) {
                //gl.glBindTexture(GL_TEXTURE_2D, tile.getTexture(k).getTextureObject());
                gl.glBindTexture(GL_TEXTURE_2D, textures.get(tile.getTextureIDs().get(k)).getTextureObject());

                gl.glEnable(GL_TEXTURE_2D);

                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            }

            //Draw polygons
            int start, end;
            final int vPerPolygon = 4;
            start = tile.getTexOffsetsQuad().get(k);
            if (k + 1 < tile.getTextureIDs().size()) {
                end = (tile.getTexOffsetsQuad().get(k + 1));
            } else {
                end = tile.getVCoordsQuad().length / (3 * vPerPolygon);
            }

            if (!useWireframe) {
                gl.glBegin(GL_QUADS);
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < vPerPolygon; j++) {
                        gl.glTexCoord2fv(tile.getTCoordsQuad(), (i * vPerPolygon + j) * 2);
                        gl.glNormal3fv(tile.getNCoordsQuad(), (i * vPerPolygon + j) * 3);
                        gl.glColor3fv(tile.getColorsQuad(), (i * vPerPolygon + j) * 3);
                        gl.glVertex3fv(tile.getVCoordsQuad(), (i * vPerPolygon + j) * 3);
                    }
                }
                gl.glEnd();
            } else {
                gl.glBegin(GL_QUADS);
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < vPerPolygon; j++) {
                        gl.glTexCoord2fv(tile.getTCoordsQuad(), (i * vPerPolygon + j) * 2);
                        gl.glVertex3fv(tile.getVCoordsQuad(), (i * vPerPolygon + j) * 3);
                    }
                }
                gl.glEnd();
            }
        }
    }

    private void drawTris(GL2 gl, Tile tile, boolean useWireframe) {
        if (!(tile.getVCoordsTri().length > 0 && tile.getTCoordsTri().length > 0 && tile.getColorsTri().length > 0)) {
            return;
        }

        for (int k = 0; k < tile.getTextureIDs().size(); k++) {
            // activate texture unit #0 and bind it to the brick texture object
            //gl.glActiveTexture(GL_TEXTURE0);
            if (texturesEnabled && !useWireframe) {
                //gl.glBindTexture(GL_TEXTURE_2D, tile.getTexture(k).getTextureObject());
                gl.glBindTexture(GL_TEXTURE_2D, textures.get(tile.getTextureIDs().get(k)).getTextureObject());

                gl.glEnable(GL_TEXTURE_2D);

                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            }

            //Draw polygons
            int start, end;
            final int vPerPolygon = 3;
            start = tile.getTexOffsetsTri().get(k);
            if (k + 1 < tile.getTextureIDs().size()) {
                end = (tile.getTexOffsetsTri().get(k + 1));
            } else {
                end = tile.getVCoordsTri().length / (3 * vPerPolygon);
            }

            if (!useWireframe) {
                gl.glBegin(GL_TRIANGLES);
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < vPerPolygon; j++) {
                        gl.glTexCoord2fv(tile.getTCoordsTri(), (i * vPerPolygon + j) * 2);
                        gl.glNormal3fv(tile.getNCoordsTri(), (i * vPerPolygon + j) * 3);
                        gl.glColor3fv(tile.getColorsTri(), (i * vPerPolygon + j) * 3);
                        gl.glVertex3fv(tile.getVCoordsTri(), (i * vPerPolygon + j) * 3);
                    }
                }
                gl.glEnd();
            } else {
                gl.glBegin(GL_TRIANGLES);
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < vPerPolygon; j++) {
                        gl.glTexCoord2fv(tile.getTCoordsTri(), (i * vPerPolygon + j) * 2);
                        gl.glVertex3fv(tile.getVCoordsTri(), (i * vPerPolygon + j) * 3);
                    }
                }
                gl.glEnd();
            }

        }

    }

    public void drawOpaque() {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glEnable(GL_BLEND);
        gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_DST_ALPHA);

        // adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LESS); //Less instead of equal for drawing the grid

        gl.glEnable(GL_ALPHA_TEST);
        gl.glAlphaFunc(GL_GREATER, 0.9f);

        gl.glColor3f(1.0f, 1.0f, 1.0f);

        if (backfaceCullingEnabled) {
            gl.glEnable(GL_CULL_FACE);
        }

        drawTile(false);

        if (backfaceCullingEnabled) {
            gl.glDisable(GL_CULL_FACE);
        }

    }

    public void drawTransparent() {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glEnable(GL_BLEND);
        gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LESS); //Less instead of equal for drawing the grid

        gl.glEnable(GL_ALPHA_TEST);
        gl.glAlphaFunc(GL_NOTEQUAL, 0.0f);

        gl.glColor3f(1.0f, 1.0f, 1.0f);

        if (backfaceCullingEnabled) {
            gl.glEnable(GL_CULL_FACE);
        }

        drawTile(false);

        if (backfaceCullingEnabled) {
            gl.glDisable(GL_CULL_FACE);
        }

    }

    public void drawWireframe() {
        GL2 gl = (GL2) GLContext.getCurrentGL();

        gl.glEnable(GL_BLEND);
        gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LEQUAL);

        gl.glColor3f(0.0f, 0.0f, 0.0f);

        gl.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);

        gl.glDisable(GL_TEXTURE_2D);

        gl.glLineWidth(1.5f);

        drawTile(true);

        gl.glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

    }

    public void drawNormals(GL2 gl){
        gl.glEnable(GL_BLEND);
        gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_DST_ALPHA);

        // adjust OpenGL settings and draw model
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LESS); //Less instead of equal for drawing the grid

        gl.glEnable(GL_ALPHA_TEST);
        gl.glAlphaFunc(GL_GREATER, 0.9f);

        gl.glDisable(GL_TEXTURE_2D);
        gl.glDisable(GL_LIGHTING);
        gl.glDisable(GL_LIGHT0);

        gl.glColor3f(1.0f, 0.6f, 0.5f);
        gl.glLineWidth(1.5f);

        gl.glLoadIdentity();
        if (orthoEnabled) {
            float v = 6.0f;
            gl.glOrtho(-v, v, -v, v, -100.0f, 100.0f);
        } else {
            float aspect = getDrawableAspect();
            glu.gluPerspective(60.0f, aspect, 1.0f, 1000.0f);
        }

        gl.glTranslatef(-cameraX, -cameraY, -cameraZ); // translate into the screen

        gl.glRotatef(modelRotX, 1.0f, 0.0f, 0.0f); // rotate about the x-axis
        gl.glRotatef(modelRotY, 0.0f, 1.0f, 0.0f); // rotate about the y-axis
        gl.glRotatef(modelRotZ, 0.0f, 0.0f, 1.0f); // rotate about the z-axis

        Tile tile = handler.getTileset().get(handler.getTileIndexSelected());

        for (int k = 0; k < tile.getTextureIDs().size(); k++) {
            //Draw polygons
            int start, end;
            final int vPerPolygon = 3;
            start = tile.getTexOffsetsTri().get(k);
            if (k + 1 < tile.getTextureIDs().size()) {
                end = (tile.getTexOffsetsTri().get(k + 1));
            } else {
                end = tile.getVCoordsTri().length / (3 * vPerPolygon);
            }


            gl.glBegin(GL_LINES);
            for (int i = start; i < end; i++) {
                for (int j = 0; j < vPerPolygon; j++) {
                    int offset = (i * vPerPolygon + j) * 3;
                    float[] normal = {
                            tile.getVCoordsTri()[offset] + tile.getNCoordsTri()[offset] * normalScale,
                            tile.getVCoordsTri()[offset + 1] + tile.getNCoordsTri()[offset + 1] * normalScale,
                            tile.getVCoordsTri()[offset + 2] + tile.getNCoordsTri()[offset + 2] * normalScale
                    };
                    gl.glVertex3fv(tile.getVCoordsTri(), (i * vPerPolygon + j) * 3);
                    gl.glVertex3fv(normal, 0);
                }
            }
            gl.glEnd();
        }

        for (int k = 0; k < tile.getTextureIDs().size(); k++) {
            //Draw polygons
            int start, end;
            final int vPerPolygon = 4;
            start = tile.getTexOffsetsQuad().get(k);
            if (k + 1 < tile.getTextureIDs().size()) {
                end = (tile.getTexOffsetsQuad().get(k + 1));
            } else {
                end = tile.getVCoordsQuad().length / (3 * vPerPolygon);
            }

            gl.glBegin(GL_LINES);
            for (int i = start; i < end; i++) {
                for (int j = 0; j < vPerPolygon; j++) {
                    int offset = (i * vPerPolygon + j) * 3;
                    float[] normal = {
                            tile.getVCoordsQuad()[offset] + tile.getNCoordsQuad()[offset] * normalScale,
                            tile.getVCoordsQuad()[offset + 1] + tile.getNCoordsQuad()[offset + 1] * normalScale,
                            tile.getVCoordsQuad()[offset + 2] + tile.getNCoordsQuad()[offset + 2] * normalScale
                    };
                    gl.glVertex3fv(tile.getVCoordsQuad(), (i * vPerPolygon + j) * 3);
                    gl.glVertex3fv(normal, 0);
                }
            }
            gl.glEnd();

        }

    }

    private void loadTexturesGL() {
        textures = new ArrayList<>();
        for (int i = 0; i < handler.getTileset().getMaterials().size(); i++) {
            textures.add(loadTextureGL(i));
        }
    }

    private Texture loadTextureGL(int index) {
        Texture tex = null;
        try {
            BufferedImage img = Utils.cloneImg(handler.getTileset().getMaterials().get(index).getTextureImg());
            ImageUtil.flipImageVertically(img);
            tex = AWTTextureIO.newTexture(GLProfile.getDefault(), img, false);
        } catch (Exception e) {
            tex = AWTTextureIO.newTexture(GLProfile.getDefault(), Tileset.defaultTexture, false);
        }
        return tex;
    }

    public void swapTextures(int index1, int index2) {
        Collections.swap(textures, index1, index2);
    }

    public MapEditorHandler getHandler() {
        return handler;
    }

    public void setHandler(MapEditorHandler handler) {
        this.handler = handler;
        requestUpdate();
        repaint();
    }

    public void updateTileGL() {
        requestUpdate();
    }

    public void updateGL() {
        requestUpdate();
    }

    public boolean isWireframe() {
        return wireframeEnabled;
    }

    public void swapVBOs(int index1, int index2) {
        requestUpdate();
        repaint();
    }

    public void setWireframe(boolean enabled) {
        this.wireframeEnabled = enabled;
    }

    public void setBackfaceCulling(boolean enabled) {
        this.backfaceCullingEnabled = enabled;
    }

    public void setLightingEnabled(boolean lightingEnabled) {
        this.lightingEnabled = lightingEnabled;
    }

    public boolean isLightingEnabled() {
        return lightingEnabled;
    }

    public void setTexturesEnabled(boolean texturesEnabled) {
        this.texturesEnabled = texturesEnabled;
    }

    public boolean isTexturesEnabled() {
        return texturesEnabled;
    }

    public void setNormalsEnabled(boolean normalsEnabled){
        this.normalsEnabled = normalsEnabled;
    }

}
