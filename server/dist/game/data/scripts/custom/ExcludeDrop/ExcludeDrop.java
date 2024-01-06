package custom.ExcludeDrop;

import ai.AbstractNpcAI;
import org.l2jmobius.commons.util.PropertiesParser;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.custom.multipro.MultiProEvent;
import org.l2jmobius.gameserver.custom.multipro.MultiProManager;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.enums.AcquireSkillType;
import org.l2jmobius.gameserver.enums.ClassId;
import org.l2jmobius.gameserver.enums.Race;
import org.l2jmobius.gameserver.enums.ShortcutType;
import org.l2jmobius.gameserver.model.Shortcut;
import org.l2jmobius.gameserver.model.SkillLearn;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.events.AbstractScript;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.impl.creature.player.OnPlayerProfessionChange;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.holders.DropGroupHolder;
import org.l2jmobius.gameserver.model.holders.DropHolder;
import org.l2jmobius.gameserver.model.holders.ItemHolder;
import org.l2jmobius.gameserver.model.quest.QuestState;
import org.l2jmobius.gameserver.model.quest.State;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.*;
import org.l2jmobius.gameserver.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExcludeDrop extends AbstractNpcAI {

    private final String _configFile = "config/Custom/ExcludeDrop.ini";
    private final List<Integer> _allowedIds = new ArrayList<Integer>();
    private boolean _isEnabled;

    public ExcludeDrop() {
        loadConfig();
    }

    private void loadConfig() {
        final PropertiesParser confParser = new PropertiesParser(_configFile);
        _isEnabled = confParser.getBoolean("Enabled", false);

        if(_isEnabled) {
            String[] allowedIdsS = confParser.getString("AllowedIds", "").split(";");

            for (String idS : allowedIdsS) {
                if (Util.isDigit(idS)) {
                    _allowedIds.add(Integer.valueOf(idS));
                }
            }


            for (NpcTemplate template : NpcData.getInstance().getTemplates(NpcTemplate::isAttackable)) {
                if (template.getDropList() != null)
                    template.getDropList().removeIf(dropHolder -> !_allowedIds.contains(dropHolder.getItemId()));

                if (template.getDropGroups() != null) {
                    for (DropGroupHolder dropGroup : template.getDropGroups()) {
                        if (dropGroup.getDropList() != null) {
                            dropGroup.getDropList().removeIf(dropHolder -> !_allowedIds.contains(dropHolder.getItemId()));
                        }
                    }
                }
            }

        }
    }

    public static void main(String[] args) {
        new ExcludeDrop();
    }
}
