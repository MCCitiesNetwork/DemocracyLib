package net.democracycraft.democracyLib.api.service.engine;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

public interface AsyncDemocracyService extends DemocracyService {

    @NotNull ExecutorService getExecutorService();
}
