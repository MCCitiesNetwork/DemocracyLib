package net.democracycraft.democracyLib.api.service.engine;

import net.democracycraft.democracyLib.api.cache.DemocracyCache;

public interface CacheHolderDemocracyService<CacheType extends DemocracyCache> extends DemocracyService{

    CacheType getCache();

}
