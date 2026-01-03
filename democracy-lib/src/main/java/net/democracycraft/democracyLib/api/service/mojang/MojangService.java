package net.democracycraft.democracyLib.api.service.mojang;

import net.democracycraft.democracyLib.api.cache.MojangServiceDemocracyCache;
import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.CacheHolderDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MojangService<PluginType extends Plugin> extends AsyncDemocracyService, PluginBoundDemocracyService<PluginType>, CacheHolderDemocracyService<MojangServiceDemocracyCache> {

    CompletableFuture<@Nullable String> getName(final UUID uniqueIdentifier);

    CompletableFuture<@Nullable UUID> getUUID(final String name);

    CompletableFuture<@Nullable SkinDto> getSkin(final UUID uniqueIdentifier);

}
