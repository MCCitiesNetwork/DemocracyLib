package net.democracycraft.democracyLib.api.data;

import net.democracycraft.democracyLib.internal.data.SkinDtoImpl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public interface SkinDto {

    @NotNull String value();

    @NotNull String signature();

    @Contract("_, _ -> new")
    static @NonNull SkinDto of(@NotNull String value, @NotNull String signature) {
        return new SkinDtoImpl(value, signature);
    }

}
