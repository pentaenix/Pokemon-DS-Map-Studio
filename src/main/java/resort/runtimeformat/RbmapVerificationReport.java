package resort.runtimeformat;

import java.util.ArrayList;
import java.util.List;

public class RbmapVerificationReport {

    public String rbmapPath;
    public String resolvedRpakPath;
    public String mapId;
    public String displayName;
    public String rpakDependency;
    public String exportRegion;
    public int chunkCount;
    public int materialGroupCount;
    public int placementCount;
    public int uniqueResortTileIds;
    public int spawnCount;
    public int rpakTileCount;
    public int rpakMeshCount;
    public int rpakTextureCount;
    public boolean valid;
    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(valid ? "RBMAP verification passed.\n" : "RBMAP verification failed.\n");
        sb.append("\nRBMAP: ").append(rbmapPath).append('\n');
        if (mapId != null) {
            sb.append("Map ID: ").append(mapId).append('\n');
        }
        if (displayName != null && !displayName.isEmpty()) {
            sb.append("Display name: ").append(displayName).append('\n');
        }
        if (rpakDependency != null) {
            sb.append("RPAK dependency: ").append(rpakDependency).append('\n');
        }
        if (resolvedRpakPath != null) {
            sb.append("Resolved RPAK: ").append(resolvedRpakPath).append('\n');
        }
        if (exportRegion != null) {
            sb.append("Export region: ").append(exportRegion).append('\n');
        }
        sb.append("Chunks: ").append(chunkCount).append('\n');
        sb.append("Material groups: ").append(materialGroupCount).append('\n');
        sb.append("Placements: ").append(placementCount).append('\n');
        sb.append("Unique Resort tile IDs: ").append(uniqueResortTileIds).append('\n');
        sb.append("Spawns: ").append(spawnCount).append('\n');
        if (resolvedRpakPath != null) {
            sb.append("RPAK tiles: ").append(rpakTileCount).append('\n');
            sb.append("RPAK mesh entries: ").append(rpakMeshCount).append('\n');
            sb.append("RPAK texture entries: ").append(rpakTextureCount).append('\n');
        }
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String warning : warnings) {
                sb.append(" - ").append(warning).append('\n');
            }
        }
        if (!errors.isEmpty()) {
            sb.append("\nErrors:\n");
            for (String error : errors) {
                sb.append(" - ").append(error).append('\n');
            }
        }
        return sb.toString();
    }
}
