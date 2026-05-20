package utils;

/**
 * Controls when {@link tileset.TilesetRenderer} runs: expensive offscreen GL thumbnail generation.
 * <ul>
 *   <li>{@code pdsm.enableJoglTilesetRendererStartup} — MainFrame constructor only</li>
 *   <li>{@code pdsm.enableJoglTilesetRendererRuntime} — open map/tileset, thumbnails, imports</li>
 *   <li>{@code pdsm.enableJoglTilesetRenderer} — legacy: enables both checks if set</li>
 * </ul>
 */
public final class TilesetRendererPolicy {

    private TilesetRendererPolicy() {
    }

    public static boolean isStartupEnabled() {
        return Boolean.getBoolean("pdsm.enableJoglTilesetRendererStartup")
                || Boolean.getBoolean("pdsm.enableJoglTilesetRenderer");
    }

    public static boolean isRuntimeEnabled() {
        String env = System.getenv("PDSM_DISABLE_TILESET_RENDERER");
        if ("1".equals(env) || "true".equalsIgnoreCase(env)) {
            return false;
        }
        return Boolean.getBoolean("pdsm.enableJoglTilesetRendererRuntime")
                || Boolean.getBoolean("pdsm.enableJoglTilesetRenderer");
    }
}
