package net.democracycraft.democracyLib.internal.bootstrap.handler;

import net.democracycraft.democracyLib.api.data.SkinDto;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Special handler to adapt SkinDto across classloaders.
 */
public final class MojangDemocracyBootstrapHandler extends GenericDemocracyBootstrapHandler {

    public MojangDemocracyBootstrapHandler(Object target) {
        super(target);
    }

    @Override
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
        Object result = super.invoke(proxy, method, args);

        if ("getSkin".equals(method.getName()) && result instanceof CompletableFuture<?> cf) {
            return cf.thenApply(dto -> {
                if (dto == null) return null;
                try {
                    Method valueM = dto.getClass().getMethod("value");
                    Method sigM = dto.getClass().getMethod("signature");
                    String value = (String) valueM.invoke(dto);
                    String sig = (String) sigM.invoke(dto);
                    return SkinDto.of(value, sig);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return result;
    }
}
