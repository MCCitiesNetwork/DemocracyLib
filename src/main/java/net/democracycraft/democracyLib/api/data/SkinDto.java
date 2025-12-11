package net.democracycraft.democracyLib.api.data;

import net.democracycraft.democracyLib.internal.data.SkinDtoImpl;

public interface SkinDto {

    String value();

    String signature();

    static SkinDto of(String value, String signature) {
        return new SkinDtoImpl(value, signature);
    }

}
