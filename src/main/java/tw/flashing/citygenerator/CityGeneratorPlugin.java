package tw.flashing.citygenerator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CityGeneratorPlugin extends JavaPlugin {
    private final Map<UUID, GenerationJob> jobs = new ConcurrentHashMap<>();
    private final CityGraphGenerator graphGenerator = new CityGraphGenerator();
    private SchematicService schematicService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path schematicFolder = getDataFolder().toPath()
                .resolve(getConfig().getString("schematic-folder", "schematics"));
        schematicService = new SchematicService(schematicFolder);
        try {
            schematicService.createFolder();
        } catch (IOException exception) {
            getLogger().severe("無法建立 schematic 目錄：" + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("請將 deadend.schem、straight.schem、corner.schem、t_junction.schem、cross.schem 放入 "
                + schematicService.folder().toAbsolutePath());
    }

    @Override
    public void onDisable() {
        jobs.values().forEach(GenerationJob::cancel);
        jobs.values().forEach(GenerationJob::stopActionBar);
        jobs.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("stopdungeon")) {
            return stopGeneration(sender);
        }
        if (command.getName().equalsIgnoreCase("citycreate")) {
            return createCity(sender, args);
        }
        return false;
    }

    private boolean createCity(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此指令只能由玩家執行。", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("用法：/citycreate <路口節點數>", NamedTextColor.YELLOW));
            return true;
        }

        int mapSize;
        try {
            mapSize = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("路口節點數必須是整數。", NamedTextColor.RED));
            return true;
        }

        int maximum = Math.max(2, getConfig().getInt(
                "max-node-count", getConfig().getInt("max-map-size", 50)));
        if (mapSize < 2 || mapSize > maximum) {
            player.sendMessage(Component.text("節點數量必須介於 2 到 " + maximum + "。", NamedTextColor.RED));
            return true;
        }
        if (jobs.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("你已經有一個城市正在生成；可用 /stopdungeon 停止。", NamedTextColor.RED));
            return true;
        }

        GenerationJob job = new GenerationJob(player.getUniqueId(), player.getLocation().getBlock().getLocation());
        jobs.put(player.getUniqueId(), job);
        getLogger().info(player.getName() + " 開始生成城市，路口節點=" + mapSize);
        job.setActionBarTask(Bukkit.getScheduler().runTaskTimer(this, () -> {
            Component message = Component.text("城市生成中…", NamedTextColor.YELLOW);
            Bukkit.getOnlinePlayers().forEach(online -> online.sendActionBar(message));
        }, 0L, 10L));

        int minimumEdgeLength = getConfig().getInt("min-edge-length", 2);
        int maximumEdgeLength = getConfig().getInt("max-edge-length", 6);
        int roadClearance = getConfig().getInt("road-clearance", 1);
        int loopChance = getConfig().getInt("loop-chance", 30);
        int configuredMaximumLoops = getConfig().getInt("max-loops", 0);
        int maximumLoops = configuredMaximumLoops > 0
                ? configuredMaximumLoops
                : Math.max(1, mapSize / 10);
        GenerationSettings settings = new GenerationSettings(
                minimumEdgeLength, maximumEdgeLength, roadClearance,
                loopChance, maximumLoops);
        player.sendMessage(Component.text("正在載入結構並計算城市…", NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskAsynchronously(this,
                () -> prepareGeneration(player, mapSize, settings, job));
        return true;
    }

    private void prepareGeneration(Player player, int mapSize, GenerationSettings settings, GenerationJob job) {
        try {
            schematicService.loadAll();
            CityGraph graph = graphGenerator.generate(mapSize, settings, job::isCancelled);
            Bukkit.getScheduler().runTask(this, () -> {
                if (job.isCancelled()) {
                    finish(job, Component.text("城市生成已停止。", NamedTextColor.RED));
                } else {
                    beginPasting(player, job, graph);
                }
            });
        } catch (CityGraphGenerator.GenerationCancelledException ignored) {
            Bukkit.getScheduler().runTask(this,
                    () -> finish(job, Component.text("城市生成已停止。", NamedTextColor.RED)));
        } catch (Exception | LinkageError exception) {
            getLogger().severe("城市準備失敗：" + exception.getMessage());
            Bukkit.getScheduler().runTask(this, () -> finish(job,
                    Component.text("城市生成失敗：" + exception.getMessage(), NamedTextColor.RED)));
        }
    }

    private void beginPasting(Player player, GenerationJob job, CityGraph graph) {
        int spacing = Math.max(1, getConfig().getInt("spacing", 7));
        int perTick = Math.max(1, getConfig().getInt("placements-per-tick", 1));
        boolean pasteAir = getConfig().getBoolean("paste-air", true);
        Iterator<Map.Entry<GridPos, List<GridPos>>> iterator = graph.adjacency().entrySet().iterator();
        int total = graph.adjacency().size();

        player.sendMessage(Component.text("拓樸完成，共 " + graph.junctionCount() + " 個路口、"
                + total + " 個模板單位，開始貼上結構。", NamedTextColor.GREEN));
        new BukkitRunnable() {
            private int completed;

            @Override
            public void run() {
                if (job.isCancelled()) {
                    cancel();
                    finish(job, Component.text("城市生成已停止（已完成 " + completed + "/" + total + "）。",
                            NamedTextColor.RED));
                    return;
                }

                try {
                    for (int count = 0; count < perTick && iterator.hasNext(); count++) {
                        Map.Entry<GridPos, List<GridPos>> entry = iterator.next();
                        GridPos position = entry.getKey();
                        int offsetX = (position.x() - graph.start().x()) * spacing;
                        int offsetZ = (position.z() - graph.start().z()) * spacing;
                        Location target = job.origin().clone().add(offsetX, 0, offsetZ);
                        schematicService.paste(SchematicChoice.fromNeighbours(position, entry.getValue()), target, pasteAir);
                        completed++;
                    }
                } catch (Exception exception) {
                    cancel();
                    getLogger().severe("貼上 schematic 失敗：" + exception.getMessage());
                    finish(job, Component.text("城市生成失敗：" + exception.getMessage(), NamedTextColor.RED));
                    return;
                }

                if (!iterator.hasNext()) {
                    cancel();
                    finish(job, Component.text("城市生成完成，共貼上 " + completed + " 個節點。",
                            NamedTextColor.GREEN));
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private boolean stopGeneration(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此指令只能由玩家執行。", NamedTextColor.RED));
            return true;
        }

        GenerationJob job = jobs.get(player.getUniqueId());
        if (job == null) {
            player.sendMessage(Component.text("目前沒有正在進行的城市生成。", NamedTextColor.YELLOW));
            return true;
        }
        job.cancel();
        getLogger().info(player.getName() + " 停止城市生成");
        finish(job, Component.text("城市生成已停止。", NamedTextColor.RED));
        return true;
    }

    private void finish(GenerationJob job, Component message) {
        if (!jobs.remove(job.owner(), job)) {
            return;
        }
        job.stopActionBar();
        Player owner = Bukkit.getPlayer(job.owner());
        if (owner != null) {
            owner.sendMessage(message);
        }
    }
}
