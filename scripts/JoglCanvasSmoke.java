import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

public class JoglCanvasSmoke {
    public static void main(String[] args) {
        System.out.println("[JoglCanvasSmoke] main thread=" + Thread.currentThread().getName());
        EventQueue.invokeLater(() -> {
            System.out.println("[JoglCanvasSmoke] EDT=" + Thread.currentThread().getName());

            System.out.println("[JoglCanvasSmoke] before GLProfile.get");
            GLProfile profile = GLProfile.get(GLProfile.GL2);
            System.out.println("[JoglCanvasSmoke] profile=" + profile);

            System.out.println("[JoglCanvasSmoke] before caps");
            GLCapabilities caps = new GLCapabilities(profile);
            System.out.println("[JoglCanvasSmoke] caps=" + caps);

            System.out.println("[JoglCanvasSmoke] before GLCanvas");
            GLCanvas canvas = new GLCanvas(caps);
            System.out.println("[JoglCanvasSmoke] after GLCanvas");

            canvas.addGLEventListener(new GLEventListener() {
                @Override
                public void init(GLAutoDrawable drawable) {
                    System.out.println("[JoglCanvasSmoke] GLEventListener.init thread=" + Thread.currentThread().getName());
                    GL2 gl = drawable.getGL().getGL2();
                    System.out.println("[JoglCanvasSmoke] GL_VENDOR=" + gl.glGetString(GL.GL_VENDOR));
                    System.out.println("[JoglCanvasSmoke] GL_RENDERER=" + gl.glGetString(GL.GL_RENDERER));
                    System.out.println("[JoglCanvasSmoke] GL_VERSION=" + gl.glGetString(GL.GL_VERSION));
                }

                @Override
                public void dispose(GLAutoDrawable drawable) {
                    System.out.println("[JoglCanvasSmoke] dispose");
                }

                @Override
                public void display(GLAutoDrawable drawable) {
                    drawable.getGL().getGL2().glClearColor(0.2f, 0.3f, 0.5f, 1f);
                    drawable.getGL().getGL2().glClear(GL.GL_COLOR_BUFFER_BIT);
                }

                @Override
                public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                    System.out.println("[JoglCanvasSmoke] reshape " + width + "x" + height);
                }
            });

            JFrame frame = new JFrame("JOGL Canvas Smoke");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(canvas, BorderLayout.CENTER);
            frame.setSize(640, 480);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            System.out.println("[JoglCanvasSmoke] visible");
        });
    }
}
