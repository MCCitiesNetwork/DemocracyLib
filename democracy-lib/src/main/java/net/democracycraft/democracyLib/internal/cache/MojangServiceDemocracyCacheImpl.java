package net.democracycraft.democracyLib.internal.cache;

import net.democracycraft.democracyLib.api.cache.MojangServiceDemocracyCache;
import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.internal.service.engine.AsyncDemocracyServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class MojangServiceDemocracyCacheImpl extends AsyncDemocracyServiceImpl implements MojangServiceDemocracyCache {

    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, SkinDto> skins = new ConcurrentHashMap<>();

    public MojangServiceDemocracyCacheImpl(ExecutorService executor) {
        super(executor);
    }

    @Override
    public @NotNull Map<UUID, String> getUniqueIdentifierToNameMap() {
        return uuidToName;
    }

    @Override
    public @NotNull Map<String, UUID> getNameToUniqueIdentifierMap() {
        return nameToUuid;
    }

    @Override
    public @NotNull Map<UUID, SkinDto> getUniqueIdentifierToSkinMap() {
        return skins;
    }

    @Override
    public void clearCache() {
        supplyAsync(() -> {
            uuidToName.clear();
            nameToUuid.clear();
            skins.clear();
            return null; // void return
        });
    }

    @Override
    public @NotNull String getServiceName() {
        return "MojangServiceCache";
    }

    @Override
    public @NotNull ExecutorService getExecutorService() {
        return executor;
    }
}
