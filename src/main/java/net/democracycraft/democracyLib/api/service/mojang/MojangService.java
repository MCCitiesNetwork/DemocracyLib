package net.democracycraft.democracyLib.api.service;

import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MojangService extends AsyncDemocracyService {

    CompletableFuture<String> getName(final UUID uniqueIdentifier);

    CompletableFuture<UUID> getUUID(final String name);

    CompletableFuture<SkinDto> getSkin(final UUID uniqueIdentifier);

}
