package org.l2jmobius.gameserver.custom.multipro;

import java.util.Arrays;

public enum MultiProEvent {
    LOAD_NPC_DLG("load_npc_dlg");

    private final String event;

    MultiProEvent(String loadNpcDlg) {
        event = loadNpcDlg;
    }

    public String getEvent() {
        return event;
    }

    public static MultiProEvent getByEvent(String event)
    {
        return Arrays.stream(MultiProEvent.values()).filter(mpe -> mpe.getEvent().equals(event)).findFirst().orElse(null);
    }
}
