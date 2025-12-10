package net.democracycraft.democracyLib.api.service.worldguard;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.democracycraft.democracyLib.api.service.engine.SyncDemocracyService;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Service for accessing WorldGuard regions.
 * <p>
 * Maybe do a Region wrapper TODO ?
 */
public interface WorldGuardService extends SyncDemocracyService {
    /**
     * Returns regions overlapping the bounding box in the given world.
     * The result is fetched live from WorldGuard.
     */
    @NotNull List<ProtectedRegion> getRegionsAt(@NotNull Block block);

    /**
     * Returns all regions in the given world.
     * The result is fetched live from WorldGuard.
     */
    @NotNull List<ProtectedRegion> getRegionsIn(@NotNull World world);

    @Nullable ProtectedRegion getRegionById(@NotNull String id, @NotNull World world);

}
