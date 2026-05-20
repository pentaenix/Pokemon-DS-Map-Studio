import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Known-good minimal Swing smoke test for macOS: UI built on the EDT via {@link EventQueue#invokeLater}.
 * Run without {@code -XstartOnFirstThread}. The map studio app omits it too for the same reason.
 *
 * @see scripts/mac-window-smoke.sh
 */
public class MacWindowSmoke {

    public static void main(String[] args) {
        System.out.println("headless=" + GraphicsEnvironment.isHeadless());

        EventQueue.invokeLater(() -> {
            JFrame frame = new JFrame("MacWindowSmoke");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(new JLabel("Hello from Swing on macOS", SwingConstants.CENTER));
            frame.setSize(420, 220);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        });
    }
}
