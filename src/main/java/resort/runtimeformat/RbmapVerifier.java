package resort.runtimeformat;

import resort.bake.ChunkBuilder;
import resort.formats.ResortMapMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public final class RbmapVerifier {

    private RbmapVerifier() {
    }

    public static RbmapVerificationReport verify(Path rbmapPath) {
        RbmapVerificationReport report = new RbmapVerificationReport();
        report.rbmapPath = rbmapPath.toAbsolutePath().normalize().toString();

        try {
            RbmapReader.LoadResult rbmap = RbmapReader.load(rbmapPath);
            fillRbmapSummary(report, rbmap.document);
            verifyRbmapStructure(report, rbmap.document);

            Path rpakPath = resolveRpakPath(rbmapPath, rbmap.document.rpakDependency);
            report.resolvedRpakPath = rpakPath.toAbsolutePath().normalize().toString();
            if (!Files.isRegularFile(rpakPath)) {
                report.errors.add("Referenced RPAK not found: " + rpakPath);
            } else {
                verifyRpak(report, rpakPath, rbmap.document);
            }
        } catch (IOException ex) {
            report.errors.add(ex.getMessage());
        } catch (Exception ex) {
            report.errors.add(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }

        report.valid = report.errors.isEmpty();
        return report;
    }

    private static void fillRbmapSummary(RbmapVerificationReport report, RbmapDocument document) {
        report.mapId = document.mapId;
        report.displayName = document.displayName;
        report.rpakDependency = document.rpakDependency;
        report.exportRegion = formatRegion(document.bake.exportRegion, document.bake.mapCoordinate);
        report.chunkCount = document.chunks.size();
        report.materialGroupCount = countMaterialGroups(document);
        report.placementCount = countPlacements(document);
        report.uniqueResortTileIds = countUniqueResortTileIds(document);
        report.spawnCount = document.spawns != null ? document.spawns.size() : 0;
    }

    private static void verifyRbmapStructure(RbmapVerificationReport report, RbmapDocument document) {
        if (document.chunks.isEmpty()) {
            report.warnings.add("RBMAP contains no chunks (empty baked map).");
        }

        ResortMapMetadata.ExportRegion region = document.bake.exportRegion;
        if (region == null) {
            report.errors.add("Bake export region is missing.");
        } else if (region.width <= 0 || region.height <= 0) {
            report.errors.add("Bake export region width/height must be positive.");
        }

        if (document.bake.chunkSizeTiles <= 0) {
            report.errors.add("Bake chunkSizeTiles must be positive.");
        }

        for (RuntimeChunk chunk : document.chunks) {
            if (chunk.materialGroups == null || chunk.materialGroups.isEmpty()) {
                report.errors.add("Chunk " + chunk.chunkX + "," + chunk.chunkY + " has no material groups.");
                continue;
            }
            for (RuntimeMaterialGroup group : chunk.materialGroups) {
                if (group.placements == null || group.placements.isEmpty()) {
                    report.errors.add("Chunk " + chunk.chunkX + "," + chunk.chunkY
                            + " material " + group.materialId + " has no placements.");
                }
            }
        }
    }

    private static void verifyRpak(RbmapVerificationReport report,
                                   Path rpakPath,
                                   RbmapDocument rbmapDocument) throws IOException {
        RpakReader.LoadResult rpak = RpakReader.load(rpakPath);
        report.rpakTileCount = rpak.document.tiles.size();
        report.rpakMeshCount = countMeshEntries(rpak.archive);
        report.rpakTextureCount = rpak.document.textures != null ? rpak.document.textures.size() : 0;

        Set<Long> rpakTileIds = RpakReader.tileIds(rpak.document);
        Set<Long> usedTileIds = collectUsedResortTileIds(rbmapDocument);
        for (Long resortTileId : usedTileIds) {
            if (!rpakTileIds.contains(resortTileId)) {
                report.errors.add("Placement uses Resort tile ID missing from RPAK manifest: " + resortTileId);
            } else if (!RpakReader.hasMeshEntry(rpak.archive, resortTileId)) {
                report.errors.add("RPAK is missing mesh entry for Resort tile ID: " + resortTileId);
            }
        }

        if (rpak.document.textures != null) {
            for (RpakDocument.TextureEntry texture : rpak.document.textures) {
                if (texture.name == null || texture.name.isEmpty()) {
                    report.errors.add("RPAK contains a texture entry with no name.");
                } else if (!RpakReader.hasTextureEntry(rpak.archive, texture.name)) {
                    report.errors.add("RPAK is missing texture file: " + texture.name);
                }
            }
        }

        for (RuntimeTileTemplate tile : rpak.document.tiles) {
            if (!RpakReader.hasMeshEntry(rpak.archive, tile.resortTileId)) {
                report.errors.add("RPAK manifest lists tile without mesh entry: " + tile.resortTileId);
            }
        }
    }

    public static Path inferRuntimeRoot(Path rbmapPath) {
        Path rbmapDir = rbmapPath.toAbsolutePath().normalize().getParent();
        if (rbmapDir != null && "maps".equals(rbmapDir.getFileName().toString())) {
            return rbmapDir.getParent();
        }
        return rbmapDir;
    }

    public static Path resolveRpakPath(Path rbmapPath, String rpakDependency) {
        Path dependency = Paths.get(rpakDependency);
        if (dependency.isAbsolute()) {
            return dependency.normalize();
        }

        Path rbmapDir = rbmapPath.toAbsolutePath().normalize().getParent();
        if (rbmapDir != null && "maps".equals(rbmapDir.getFileName().toString())) {
            Path runtimeRoot = rbmapDir.getParent();
            if (runtimeRoot != null) {
                return runtimeRoot.resolve(dependency).normalize();
            }
        }

        if (rbmapDir != null) {
            return rbmapDir.resolve(dependency).normalize();
        }
        return dependency.normalize();
    }

    private static int countMaterialGroups(RbmapDocument document) {
        int count = 0;
        for (RuntimeChunk chunk : document.chunks) {
            if (chunk.materialGroups != null) {
                count += chunk.materialGroups.size();
            }
        }
        return count;
    }

    private static int countPlacements(RbmapDocument document) {
        int count = 0;
        for (RuntimeChunk chunk : document.chunks) {
            if (chunk.materialGroups == null) {
                continue;
            }
            for (RuntimeMaterialGroup group : chunk.materialGroups) {
                if (group.placements != null) {
                    count += group.placements.size();
                }
            }
        }
        return count;
    }

    private static Set<Long> collectUsedResortTileIds(RbmapDocument document) {
        Set<Long> ids = new HashSet<>();
        for (RuntimeChunk chunk : document.chunks) {
            if (chunk.materialGroups == null) {
                continue;
            }
            for (RuntimeMaterialGroup group : chunk.materialGroups) {
                if (group.placements == null) {
                    continue;
                }
                for (ChunkBuilder.ChunkPlacement placement : group.placements) {
                    ids.add(placement.resortTileId);
                }
            }
        }
        return ids;
    }

    private static int countUniqueResortTileIds(RbmapDocument document) {
        return collectUsedResortTileIds(document).size();
    }

    private static int countMeshEntries(java.util.Map<String, byte[]> archive) {
        int count = 0;
        for (String name : archive.keySet()) {
            if (name.startsWith("meshes/tile_") && name.endsWith(".json")) {
                count++;
            }
        }
        return count;
    }

    private static String formatRegion(ResortMapMetadata.ExportRegion region, int[] mapCoordinate) {
        if (region == null) {
            return "(missing)";
        }
        String mapPart = mapCoordinate != null && mapCoordinate.length >= 2
                ? " @ map " + mapCoordinate[0] + "," + mapCoordinate[1]
                : "";
        return region.x + "," + region.y + " "
                + region.width + "x" + region.height
                + mapPart;
    }
}
