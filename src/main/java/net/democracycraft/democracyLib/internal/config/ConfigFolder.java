package net.democracycraft.democracyLib.internal.config;

import net.democracycraft.democracyLib.DemocracyLib;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public enum ConfigFolder {

    GITHUB("github");

    private final String subFolderName;

    ConfigFolder(@NotNull String subFolderName) {
        this.subFolderName = subFolderName;
    }

    @NotNull
    public File getFolder() {
        File pluginDataFolder = DemocracyLib.getInstance().getDataFolder();

        return new File(pluginDataFolder, subFolderName);
    }

    public static void createFolders() {
        for (ConfigFolder folder : values()) {
            File dir = folder.getFolder();
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }
}