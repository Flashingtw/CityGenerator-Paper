package tw.flashing.citygenerator;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class GenerationJob {
    private final UUID owner;
    private final Location origin;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private BukkitTask actionBarTask;

    GenerationJob(UUID owner, Location origin) {
        this.owner = owner;
        this.origin = origin;
    }

    UUID owner() {
        return owner;
    }

    Location origin() {
        return origin;
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    void cancel() {
        cancelled.set(true);
    }

    void setActionBarTask(BukkitTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }

    void stopActionBar() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
    }
}
