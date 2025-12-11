package net.democracycraft.democracyLib.internal.service.engine;

import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public abstract class AsyncDemocracyServiceImpl implements AsyncDemocracyService {

    protected final ExecutorService executor;

    protected AsyncDemocracyServiceImpl(ExecutorService executor) {
        this.executor = executor;
    }

    protected <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            // rename thread
            Thread currentThread = Thread.currentThread();
            String oldName = currentThread.getName();


            String newName = generateThreadName();
            currentThread.setName(newName);

            try {
                // execute task
                return task.get();
            } finally {
                // restore name
                currentThread.setName(oldName);
            }
        }, executor);
    }

    private String generateThreadName() {
        String serviceName = getServiceName();
        String boundPluginName = "DemocracyLibProcess"; //default

        // if implements PluginBoundDemocracyService, get plugin name
        if (this instanceof PluginBoundDemocracyService) {
            Plugin boundPlugin = ((PluginBoundDemocracyService<?>) this).getBoundPlugin();
            boundPluginName = boundPlugin.getName();
        }

        return String.format("Async-DemocracyLib-[%s]-[%s]", boundPluginName, serviceName);
    }

}
