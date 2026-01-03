package net.democracycraft.democracyLib.internal.config;

import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.jetbrains.annotations.NotNull;

@ConfigSerializable
public class GitHubGistConfigurationImpl implements GitHubGistConfiguration {

    @Setting("token")
    @Comment("Your GitHub Personal Access Token (PAT)")
    private String token = "";

    @Setting("api-url")
    @Comment("API Base URL. Leave default for standard GitHub.")
    private String apiBaseUrl = "https://api.github.com";

    @Setting("public-gists")
    @Comment("Should the Gists be public?")
    private boolean isPublic = false;


    @Override
    public @NotNull String getToken() {
        return token;
    }

    @Override
    public @NotNull String getApiBaseUrl() {
        return apiBaseUrl;
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

}