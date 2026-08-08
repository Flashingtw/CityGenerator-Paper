package tw.flashing.citygenerator;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class SchematicService {
    private static final Set<String> REQUIRED = Set.of(
            "deadend", "straight", "corner", "t_junction", "cross"
    );

    private final Path folder;
    private volatile Map<String, Clipboard> clipboards = Map.of();

    SchematicService(Path folder) {
        this.folder = folder;
    }

    void createFolder() throws IOException {
        Files.createDirectories(folder);
    }

    void loadAll() throws IOException {
        Map<String, Clipboard> loaded = new HashMap<>();
        for (String name : REQUIRED) {
            Path path = folder.resolve(name + ".schem");
            if (!Files.isRegularFile(path)) {
                throw new IOException("找不到 schematic：" + path.toAbsolutePath());
            }

            // FAWE 2.15.x is based on a WorldEdit API version from before findByPath(Path).
            // The deprecated File overload remains available in both FAWE and WorldEdit 7.4.x.
            ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
            if (format == null) {
                throw new IOException("無法辨識 schematic 格式：" + path.toAbsolutePath());
            }

            try (InputStream input = Files.newInputStream(path);
                 ClipboardReader reader = format.getReader(input)) {
                loaded.put(name, reader.read());
            }
        }
        clipboards = Map.copyOf(loaded);
    }

    void paste(SchematicChoice choice, Location location, boolean pasteAir) throws Exception {
        Clipboard clipboard = clipboards.get(choice.name());
        if (clipboard == null) {
            throw new IllegalStateException("Schematic 尚未載入：" + choice.name());
        }

        World bukkitWorld = location.getWorld();
        BlockVector3 target = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(bukkitWorld))) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            // Denizen's schematic angle convention rotates in the opposite direction
            // to WorldEdit's AffineTransform positive Y rotation.
            holder.setTransform(new AffineTransform().rotateY(-choice.angle()));
            Operation operation = holder.createPaste(editSession)
                    .to(target)
                    .ignoreAirBlocks(!pasteAir)
                    .build();
            Operations.complete(operation);
        }
    }

    Path folder() {
        return folder;
    }
}
