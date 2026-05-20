import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Minimal JOGL + Swing EDT smoke test (same classpath intent as the map studio app). */
public class JoglSmoke {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            System.out.println("JoglSmoke EDT=" + Thread.currentThread().getName());
            GLProfile profile = GLProfile.get(GLProfile.GL2);
            GLCapabilities caps = new GLCapabilities(profile);
            GLCanvas canvas = new GLCanvas(caps);

            JFrame frame = new JFrame("JOGL Smoke");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(canvas);
            frame.setSize(600, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
