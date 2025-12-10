package net.democracycraft.democracyLib.api.service.mojang;

import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MojangService extends AsyncDemocracyService {

    CompletableFuture<@Nullable String> getName(final UUID uniqueIdentifier);

    CompletableFuture<@Nullable UUID> getUUID(final String name);

    CompletableFuture<@Nullable SkinDto> getSkin(final UUID uniqueIdentifier);

}
