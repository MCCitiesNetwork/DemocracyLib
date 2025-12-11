package net.democracycraft.democracyLib.internal.service.mojang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.democracycraft.democracyLib.api.cache.MojangServiceDemocracyCache;
import net.democracycraft.democracyLib.api.data.SkinDto;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.service.engine.AsyncDemocracyServiceImpl;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

public class MojangServiceImpl<PluginType extends Plugin> extends AsyncDemocracyServiceImpl implements MojangService<PluginType> {

    private static final String UUID_TO_NAME_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String NAME_TO_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int TIMEOUT_MS = 5000;

    private final PluginType plugin;

    private final MojangServiceDemocracyCache mojangServiceDemocracyCache;

    public MojangServiceImpl(PluginType plugin, MojangServiceDemocracyCache mojangServiceDemocracyCache) {
        super(mojangServiceDemocracyCache.getExecutorService());
        this.plugin = plugin;
        this.mojangServiceDemocracyCache = mojangServiceDemocracyCache;
    }

    @Override
    public CompletableFuture<@Nullable String> getName(final UUID uniqueIdentifier) {
        if (uniqueIdentifier == null) return CompletableFuture.completedFuture(null);


        // check cache
        if (mojangServiceDemocracyCache.getUniqueIdentifierToNameMap().containsKey(uniqueIdentifier)) {
            return CompletableFuture.completedFuture(mojangServiceDemocracyCache.getUniqueIdentifierToNameMap().get(uniqueIdentifier));
        }

        // Fetch from Mojang API
        return supplyAsync(() -> {
            try {
                String url = UUID_TO_NAME_URL + uniqueIdentifier.toString().replace("-", "");
                JsonObject response = getJsonObject(url);
                
                if (response != null && response.has("name")) {
                    String name = response.get("name").getAsString();
                    // update cache
                    mojangServiceDemocracyCache.getUniqueIdentifierToNameMap().put(uniqueIdentifier, name);
                    mojangServiceDemocracyCache.getNameToUniqueIdentifierMap().put(name.toLowerCase(), uniqueIdentifier);
                    return name;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve name for UUID " + uniqueIdentifier, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<@Nullable UUID> getUUID(final String name) {
        if (name == null) return CompletableFuture.completedFuture(null);

        // check cache
        if (mojangServiceDemocracyCache.getNameToUniqueIdentifierMap().containsKey(name.toLowerCase())) {
            return CompletableFuture.completedFuture(mojangServiceDemocracyCache.getNameToUniqueIdentifierMap().get(name.toLowerCase()));
        }

        return supplyAsync(() -> {
            try {
                String url = NAME_TO_UUID_URL + name;
                JsonObject response = getJsonObject(url);
                
                if (response != null && response.has("id")) {
                    String rawUuid = response.get("id").getAsString();
                    UUID uuid = fromRawUUID(rawUuid);
                    
                    // update cache
                    mojangServiceDemocracyCache.getNameToUniqueIdentifierMap().put(name.toLowerCase(), uuid);
                    mojangServiceDemocracyCache.getUniqueIdentifierToNameMap().put(uuid, response.get("name").getAsString());
                    return uuid;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve name for UUID " + name, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<@Nullable SkinDto> getSkin(final UUID uniqueIdentifier) {
        if (uniqueIdentifier == null) return CompletableFuture.completedFuture(null);

        // check cache
        if (mojangServiceDemocracyCache.getUniqueIdentifierToSkinMap().containsKey(uniqueIdentifier)) {
            return CompletableFuture.completedFuture(mojangServiceDemocracyCache.getUniqueIdentifierToSkinMap().get(uniqueIdentifier));
        }

        return supplyAsync(() -> {
            try {
                // ?unsigned=false to get signed textures
                String url = UUID_TO_NAME_URL + uniqueIdentifier.toString().replace("-", "") + "?unsigned=false";
                JsonObject response = getJsonObject(url);

                if (response != null && response.has("properties")) {
                    JsonArray properties = response.getAsJsonArray("properties");

                    for (JsonElement element : properties) {
                        JsonObject prop = element.getAsJsonObject();
                        if (prop.has("name") && prop.get("name").getAsString().equals("textures")) {
                            String value = prop.get("value").getAsString();
                            String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                            
                            SkinDto skin = SkinDto.of(value, signature);
                            mojangServiceDemocracyCache.getUniqueIdentifierToSkinMap().put(uniqueIdentifier, skin);
                            return skin;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve skin for UUID " + uniqueIdentifier, e);
            }
            return null;
        });
    }

    // util

    private UUID fromRawUUID(String rawUuid) {
        return UUID.fromString(rawUuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        ));
    }

    private JsonObject getJsonObject(String urlString) throws IOException {
        HttpURLConnection connection = createConnection(urlString);
        int status = connection.getResponseCode();
        
        // basic rate limit handling
        if (status == 429) {
            System.err.println("[DemocracyLib] Mojang API Rate Limit hit!");
            return null;
        }
        if (status != HttpURLConnection.HTTP_OK) return null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "DemocracyLib-MojangService/1.0");
        return connection;
    }
    
    // Shut down executor
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public @NotNull String getServiceName() {
        return "MojangService_" + plugin.getName();
    }

    @Override
    public @NotNull PluginType getBoundPlugin() {
        return plugin;
    }

    @Override
    public MojangServiceDemocracyCache getCache() {
        return mojangServiceDemocracyCache;
    }

    @Override
    public @NotNull ExecutorService getExecutorService() {
        return executor;
    }
}

