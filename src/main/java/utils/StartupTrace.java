package utils;

/**
 * Flushed stderr diagnostics for startup issues (especially under piped {@code tee}).
 * <ul>
 *   <li>{@code -Dpdsm.trace=false} — disable all [PDSM] lines</li>
 *   <li>{@code -Dpdsm.trace.verbose=true} — fine-grained sub-steps (e.g. JOGL init)</li>
 * </ul>
 */
public final class StartupTrace {

    private StartupTrace() {
    }

    public static boolean isTraceEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("pdsm.trace", "true"));
    }

    public static boolean isVerboseEnabled() {
        return Boolean.parseBoolean(System.getProperty("pdsm.trace.verbose", "false"));
    }

    /** Major milestones (main thread / EDT phases). */
    public static void log(String phase) {
        if (!isTraceEnabled()) {
            return;
        }
        emit(phase);
    }

    /** Fine-grained steps (optional; enable with {@code -Dpdsm.trace.verbose=true}). */
    public static void detail(String phase) {
        if (!isTraceEnabled() || !isVerboseEnabled()) {
            return;
        }
        emit(phase);
    }

    private static void emit(String phase) {
        Thread t = Thread.currentThread();
        System.err.println("[PDSM] " + System.currentTimeMillis()
                + " tid=" + t.getId()
                + " " + t.getName()
                + " | " + phase);
        System.err.flush();
    }
}
