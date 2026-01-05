package net.democracycraft.democracyLib.api.dialog.factory;

import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.audience.Audience;

public interface DialogContext {

    Audience getAudience();

    DialogResponseView dialogResponseView();

}
