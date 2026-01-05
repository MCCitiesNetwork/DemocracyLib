package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.dialog.DialogResponseView;
import net.democracycraft.democracyLib.api.dialog.factory.DialogContext;
import net.kyori.adventure.audience.Audience;

public class DialogContextImp implements DialogContext {

    private final Audience audience;
    private final DialogResponseView dialogResponseView;

    public DialogContextImp(Audience audience, DialogResponseView dialogResponseView) {
        this.audience = audience;
        this.dialogResponseView = dialogResponseView;
    }

    @Override
    public Audience getAudience() {
        return audience;
    }

    @Override
    public DialogResponseView dialogResponseView() {
        return dialogResponseView;
    }
}
