package net.democracycraft.democracyLib.internal.service.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.democracycraft.democracyLib.api.service.worldguard.WorldGuardService;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WorldGuardServiceImpl implements WorldGuardService {

    private final RegionContainer regionContainer;

    public WorldGuardServiceImpl() {
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();;
    }

    @Override
    public @NotNull List<ProtectedRegion> getRegionsAt(@NotNull Block block) {
        return getRegionsIn(block.getWorld()).stream().filter(region -> region.contains(block.getX(), block.getY(), block.getZ())).toList();
    }


    @Override
    public @NotNull List<ProtectedRegion> getRegionsIn(@NotNull World world) {
        Objects.requireNonNull(world, "world");

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        RegionManager manager = regionContainer.get(weWorld);
        if (manager == null) {
            return List.of();
        }

        return new ArrayList<>(manager.getRegions().values());
    }

    @Override
    public @Nullable ProtectedRegion getRegionById(@NotNull String id, @NotNull World world) {
        return getRegionsIn(world).stream().filter(region -> region.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public @NotNull String getServiceName() {
        return "WorldGuardService";
    }
}
