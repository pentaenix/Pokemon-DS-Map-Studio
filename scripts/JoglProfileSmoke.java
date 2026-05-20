import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;

public class JoglProfileSmoke {
    public static void main(String[] args) {
        System.out.println("[JoglProfileSmoke] java=" + System.getProperty("java.version"));
        System.out.println("[JoglProfileSmoke] os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("[JoglProfileSmoke] thread=" + Thread.currentThread().getName());

        System.out.println("[JoglProfileSmoke] before GLProfile.initSingleton");
        GLProfile.initSingleton();
        System.out.println("[JoglProfileSmoke] after GLProfile.initSingleton");

        System.out.println("[JoglProfileSmoke] before GLProfile.get(GL2)");
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        System.out.println("[JoglProfileSmoke] profile=" + profile);

        System.out.println("[JoglProfileSmoke] before caps");
        GLCapabilities caps = new GLCapabilities(profile);
        System.out.println("[JoglProfileSmoke] caps=" + caps);

        System.out.println("[JoglProfileSmoke] OK");
    }
}
