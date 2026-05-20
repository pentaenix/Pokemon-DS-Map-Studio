import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

public class JoglPanelSmoke {
    public static void main(String[] args) {
        System.out.println("[JoglPanelSmoke] main thread=" + Thread.currentThread().getName());
        EventQueue.invokeLater(() -> {
            System.out.println("[JoglPanelSmoke] EDT=" + Thread.currentThread().getName());

            System.out.println("[JoglPanelSmoke] before GLProfile.get");
            GLProfile profile = GLProfile.get(GLProfile.GL2);
            System.out.println("[JoglPanelSmoke] profile=" + profile);

            System.out.println("[JoglPanelSmoke] before caps");
            GLCapabilities caps = new GLCapabilities(profile);
            System.out.println("[JoglPanelSmoke] caps=" + caps);

            System.out.println("[JoglPanelSmoke] before GLJPanel");
            GLJPanel panel = new GLJPanel(caps);
            System.out.println("[JoglPanelSmoke] after GLJPanel");

            panel.addGLEventListener(new GLEventListener() {
                @Override
                public void init(GLAutoDrawable drawable) {
                    System.out.println("[JoglPanelSmoke] GLEventListener.init thread=" + Thread.currentThread().getName());
                    GL2 gl = drawable.getGL().getGL2();
                    System.out.println("[JoglPanelSmoke] GL_VENDOR=" + gl.glGetString(GL.GL_VENDOR));
                    System.out.println("[JoglPanelSmoke] GL_RENDERER=" + gl.glGetString(GL.GL_RENDERER));
                    System.out.println("[JoglPanelSmoke] GL_VERSION=" + gl.glGetString(GL.GL_VERSION));
                }

                @Override
                public void dispose(GLAutoDrawable drawable) {
                    System.out.println("[JoglPanelSmoke] dispose");
                }

                @Override
                public void display(GLAutoDrawable drawable) {
                    drawable.getGL().getGL2().glClearColor(0.2f, 0.3f, 0.5f, 1f);
                    drawable.getGL().getGL2().glClear(GL.GL_COLOR_BUFFER_BIT);
                }

                @Override
                public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                    System.out.println("[JoglPanelSmoke] reshape " + width + "x" + height);
                }
            });

            JFrame frame = new JFrame("JOGL Panel Smoke");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(panel, BorderLayout.CENTER);
            frame.setSize(640, 480);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            System.out.println("[JoglPanelSmoke] visible");
        });
    }
}
