package net.democracycraft.democracyLib.api.dialog.factory;

import io.papermc.paper.dialog.Dialog;
import net.democracycraft.democracyLib.internal.dialog.factory.DialogFactoryImp;

public interface DialogFactory {

    /**
     * Builds a Paper {@link Dialog} from an annotated controller instance.
     */
    static Dialog create(Object controller) {
        return DialogFactoryImp.create(controller);
    }

}
