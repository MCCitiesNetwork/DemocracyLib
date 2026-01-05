package net.democracycraft.democracyLib.api.dialog;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DialogButtonHandler {

    /**
     * The id from {@link DialogButton#id()} that this method should handle.
     */
    String buttonId();

    /**
     * Max number of times the registered callback can be used.
     * <p>
     * Use {@link net.kyori.adventure.text.event.ClickCallback#UNLIMITED_USES} for unlimited.
     */
    int uses() default 1;
}
