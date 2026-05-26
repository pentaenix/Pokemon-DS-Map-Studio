package resort.bake;

import java.util.ArrayList;
import java.util.List;

public class ResortBakeReport {

    public String sourceRtpksPath;
    public String sourceMapPath;
    public String selectedMapCoordinate;
    public String exportRegion;
    public int visibleCellCount;
    public int uniqueLocalTileIndices;
    public int uniqueResortTileIds;
    public int chunkCount;
    public int materialGroupCount;
    public String outputRpakPath;
    public String outputRbmapPath;
    public final List<String> warnings = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bake Report\n");
        sb.append("Source RTPKS: ").append(sourceRtpksPath).append('\n');
        sb.append("Source map/project: ").append(sourceMapPath).append('\n');
        sb.append("Selected map: ").append(selectedMapCoordinate).append('\n');
        sb.append("Export region: ").append(exportRegion).append('\n');
        sb.append("Visible cells: ").append(visibleCellCount).append('\n');
        sb.append("Unique local tile indices: ").append(uniqueLocalTileIndices).append('\n');
        sb.append("Unique Resort tile IDs: ").append(uniqueResortTileIds).append('\n');
        sb.append("Chunks: ").append(chunkCount).append('\n');
        sb.append("Material groups: ").append(materialGroupCount).append('\n');
        sb.append("Output RPAK: ").append(outputRpakPath).append('\n');
        sb.append("Output RBMAP: ").append(outputRbmapPath).append('\n');
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
