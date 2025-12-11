package net.democracycraft.democracyLib.api.cache;

import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public interface MojangServiceDemocracyCache extends DemocracyCache, AsyncDemocracyService {

    @NotNull Map<UUID, String> getUniqueIdentifierToNameMap();

    @NotNull Map<String, UUID> getNameToUniqueIdentifierMap();

    @NotNull Map<UUID, SkinDto> getUniqueIdentifierToSkinMap();


}
