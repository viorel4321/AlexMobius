package custom.multipro;

import ai.AbstractNpcAI;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.custom.multipro.MultiProEvent;
import org.l2jmobius.gameserver.custom.multipro.MultiProManager;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillLearnData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.enums.AcquireSkillType;
import org.l2jmobius.gameserver.enums.ClassId;
import org.l2jmobius.gameserver.enums.Race;
import org.l2jmobius.gameserver.enums.ShortcutType;
import org.l2jmobius.gameserver.model.Shortcut;
import org.l2jmobius.gameserver.model.SkillLearn;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.AbstractScript;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.impl.creature.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.impl.creature.player.OnPlayerProfessionChange;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.holders.ItemHolder;
import org.l2jmobius.gameserver.model.quest.QuestState;
import org.l2jmobius.gameserver.model.quest.State;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MultiProScript extends AbstractNpcAI {

    public MultiProScript() {
        MultiProManager.getInstance().getAllowedNpcAndHtmlPath().forEach((npcId, path) ->
                addFirstTalkId(npcId));
        setOnEnterWorld(true);
    }

    @Override
    public String onEnterWorld(Player player) {
        tryRestoreSkill(player);
        player.addListener(new ConsumerEventListener(player, EventType.ON_PLAYER_PROFESSION_CHANGE, (OnPlayerProfessionChange event) -> tryRestoreSkill(event.getPlayer()), this));
        return null;
    }

    private void tryRestoreSkill(Player player) {
        QuestState qs = player.getQuestState(MultiProScript.class.getSimpleName());
        if(qs != null) {
            qs.getVars().forEach((varName, varVal) -> {
                if (varName.startsWith("multi_sk_")) {
                    String[] infoSpl = varName.split("_");
                    int classId = Integer.parseInt(infoSpl[2]);
                    if (player.getClassId().getId() == classId) {

                        int skId = Integer.parseInt(infoSpl[3]);
                        int skLvl = Integer.parseInt(varVal);

                        if (player.getSkillLevel(skId) < skLvl)
                            player.addSkill(SkillData.getInstance().getSkill(skId, skLvl), false);
                    }
                }
            });
        }
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player) {
        if(npc == null && player == null) {
            switch (MultiProEvent.getByEvent(event)) {
                case LOAD_NPC_DLG:

                    break;
            }
        }
        else if(player != null)
        {
            QuestState qs = checkQuest(player);
            if(event.startsWith("race"))
            {
                String[] splt = event.split(" ");
                if(splt.length == 3)
                {
                    String race = splt[1];
                    boolean isMage = splt[2].equalsIgnoreCase("mage");
                    Race selectedRace = Race.valueOf(race);

                    List<ClassId> classIdList = new ArrayList<>();
                    for (ClassId classId : ClassId.values()) {
                        if(classId.getRace() == selectedRace)
                        {
                            if(isMage && classId.isMage())
                                classIdList.add(classId);
                            else if(!isMage && !classId.isMage())
                                classIdList.add(classId);
                        }
                    }

                    new ArrayList<>(classIdList).forEach(classId -> {
                        if(classId.getId() < 88 || classId.getId() >= 123 && classId.getId() <= 130)
                        {
                            classIdList.remove(classId);
                        }
                    });

                    String html = HtmCache.getInstance().getHtm(qs.getPlayer(), "multipro/selectProf.htm");
                    html = html.replace("%profList%", getHtmSelectProf(classIdList));

                    return html;
                }
            }
            else if(event.startsWith("selectProf"))
            {
                String[] splt = event.split(" ");
                if(splt.length == 2)
                {
                    int selectedClassId = Integer.parseInt(splt[1]);
                    ClassId classId = getClassId(selectedClassId, qs.getPlayer().getClassId());
                    if(classId != null)
                    {
                        qs.set("lastClassId", classId.name());
                        sendSkillLearnWindow(qs.getPlayer());
                    }
                }
            }
            else if(event.startsWith("sk_info_requested_") && checkNpcForLearn(npc))
            {
                String[] splt = event.split("_");
                if(splt.length == 5)
                {
                    try {
                        int skId = Integer.parseInt(splt[3]);
                        int skLvl = Integer.parseInt(splt[4]);
                        if(canLearnSkill(qs.getPlayer(), skId, skLvl))
                        {
                            qs.getPlayer().sendPacket(new AcquireSkillInfo(AcquireSkillType.MULTIPRO, getSkillLearnInfo(qs.getPlayer(), skId, skLvl)));
                        }
                    }
                    catch (Exception err)
                    {
                        LOGGER.warning("Warn! Player try abuse multi pro with sk info: " + qs.getPlayer());
                    }
                }
            }
            else if(event.startsWith("sk_learn_request_") && checkNpcForLearn(npc))
            {
                String[] splt = event.split("_");
                if(splt.length == 5)
                {
                    try {
                        int skId = Integer.parseInt(splt[3]);
                        int skLvl = Integer.parseInt(splt[4]);

                        if(canLearnSkill(qs.getPlayer(), skId, skLvl))
                        {
                            SkillLearn sl = getSkillLearnInfo(qs.getPlayer(), skId, skLvl);

                            if(sl.getLevelUpSp() > 0)
                            {
                                if(qs.getPlayer().getSp() < sl.getLevelUpSp())
                                {
                                    qs.getPlayer().sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_SP_TO_LEARN_THIS_SKILL);
                                    return null;
                                }
                            }

                            if(sl.getGetLevel() > 0)
                            {
                                if(qs.getPlayer().getLevel() < sl.getGetLevel())
                                {
                                    return null;
                                }
                            }

                            for (ItemHolder requiredItem : sl.getRequiredItems()) {
                                if(!AbstractScript.hasItem(qs.getPlayer(), requiredItem, true))
                                {
                                    qs.getPlayer().sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_THE_NECESSARY_MATERIALS_OR_PREREQUISITES_TO_LEARN_THIS_SKILL);
                                    return null;
                                }
                            }

                            for (ItemHolder requiredItem : sl.getRequiredItems()) {
                                if(!AbstractScript.takeItems(qs.getPlayer(), requiredItem.getId(), requiredItem.getCount()))
                                {
                                    qs.getPlayer().sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_THE_NECESSARY_MATERIALS_OR_PREREQUISITES_TO_LEARN_THIS_SKILL);
                                    return null;
                                }
                            }

                            if(sl.getLevelUpSp() > 0)
                            {
                                qs.getPlayer().setSp(qs.getPlayer().getSp() - sl.getLevelUpSp());
                            }

                            qs.getPlayer().setSp(qs.getPlayer().getSp() - sl.getLevelUpSp());
                            qs.getPlayer().addSkill(SkillData.getInstance().getSkill(skId, skLvl), false);
                            qs.set("multi_sk_" + qs.getPlayer().getClassId().getId() + "_" + skId, String.valueOf(skLvl));
                            qs.getPlayer().broadcastUserInfo();

                            SkillList sl1 = new SkillList();
                            qs.getPlayer().getSkills().forEach((t1, skInfo) -> {
                                sl1.addSkill(skInfo.getId(), skInfo.getLevel(), skInfo.isPassive(), player.isSkillDisabled(skInfo), SkillData.getInstance().isEnchantable(skInfo.getId()));
                            });

                            qs.getPlayer().sendPacket(sl1);

                            for(Shortcut sc : qs.getPlayer().getAllShortCuts()) {
                                if (sc.getId() == skId && sc.getType() == ShortcutType.SKILL) {
                                    Shortcut newsc = new Shortcut(sc.getSlot(), sc.getPage(), sc.getType(), sc.getId(), skLvl, 1);
                                    qs.getPlayer().sendPacket(new ShortCutRegister(newsc));
                                    qs.getPlayer().registerShortCut(newsc);
                                }
                            }
                            qs.getPlayer().sendPacket(new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1_2).addSkillName(skId,  skLvl));
                            sendSkillLearnWindow(qs.getPlayer());
                        }
                    }
                    catch (Exception err)
                    {
                        LOGGER.warning("Warn! Player try abuse multi pro with learn: " + qs.getPlayer());
                    }
                }
            }
            return null;
        }

        return null;
    }

    private void sendSkillLearnWindow(Player player) {
        List<SkillLearn> availSkills = getAvailableSkillsToLearn(player, getPlayerLastClassIdRequested(player));
        if(availSkills != null && !availSkills.isEmpty()) {
            final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.MULTIPRO);
            for (SkillLearn availSkill : availSkills) {
                asl.addSkill(availSkill.getSkillId(), availSkill.getSkillLevel(), 10, availSkill.getLevelUpSp(), 0);
            }
            player.sendPacket(asl);
        }
        else
        {
            player.sendPacket(SystemMessageId.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
        }
    }

    private SkillLearn getSkillLearnInfo(Player player, int skId, int skLvl) {
        final SkillLearn res = getAvailableSkillsToLearn(player, getPlayerLastClassIdRequested(player)).stream().filter(sk -> sk.getSkillId() == skId && sk.getSkillLevel() == skLvl).findFirst().orElse(null);
        if(res != null && MultiProManager.getInstance().getChangeReqSpForItemId() > 0)
        {
            SkillLearn sl = new SkillLearn("", skId, skLvl, res.getGetLevel(), false, res.getLevelUpSp(), false, false, false);
            sl.addRequiredItem(new ItemHolder(MultiProManager.getInstance().getChangeReqSpForItemId(), Math.toIntExact(Math.max(1, Math.round(res.getLevelUpSp() / 1000.)))));
            return sl;
        }
        else if(res != null && MultiProManager.getInstance().getSkillItemLearnReqHashMap().containsKey(SkillData.getSkillHashCode(skId, skLvl)))
        {
            MultiProManager.SkillItemLearnReq tmp = MultiProManager.getInstance().getSkillItemLearnReqHashMap().get(SkillData.getSkillHashCode(skId, skLvl));
            SkillLearn sl = new SkillLearn("", skId, skLvl, res.getGetLevel(), false, res.getLevelUpSp(), false, false, false);
            if(tmp.getItemId() > 0)
            {
                sl.addRequiredItem(new ItemHolder(tmp.getItemId(), tmp.getItemCount()));
            }
            return sl;
        }
        return res;
    }

    private boolean canLearnSkill(Player player, int skId, int skLvl) {
        return Objects.requireNonNull(getAvailableSkillsToLearn(player, getPlayerLastClassIdRequested(player))).stream().anyMatch(sk -> sk.getSkillId() == skId && sk.getSkillLevel() == skLvl);
    }

    private ClassId getPlayerLastClassIdRequested(Player player) {
        String lastClassIdS = checkQuest(player).get("lastClassId");
        if(lastClassIdS != null)
        {
            return ClassId.valueOf(lastClassIdS);
        }

        return null;
    }

    private boolean checkNpcForLearn(Npc npc) {
        return MultiProManager.getInstance().getAllowedNpcAndHtmlPath().containsKey(npc.getId());
    }

    private static List<SkillLearn> getAvailableSkillsToLearn(Player player, ClassId classId) {
        if(classId == null)
            return null;

        List<SkillLearn> res = new ArrayList<>();

        for (SkillLearn skillLearn : SkillTreeData.getInstance().getCompleteClassSkillTree(ClassId.getClassId(classId.getId())).values()) {
            if (skillLearn.getGetLevel() > player.getLevel())
                continue;

            int playerLearnedSkLvl = player.getSkillLevel(skillLearn.getSkillId());
            //если у игрока есть уже это умение, то добавить в лист, только в том случае, если умение выше уровнем
            if (skillLearn.getSkillLevel() > playerLearnedSkLvl && res.stream().noneMatch(sk -> sk.getSkillId() == skillLearn.getSkillId() && sk.getSkillLevel() <= skillLearn.getSkillLevel())) {
                res.stream().filter(skLrnAdded -> skLrnAdded.getSkillId() == skillLearn.getSkillId() && skLrnAdded.getSkillLevel() > skillLearn.getSkillLevel()).findFirst().ifPresent(res::remove);
                res.add(skillLearn);
            }

        }

        return res;
    }

    private ClassId getClassId(int selectedClassId, ClassId playerCurrentClassId) {
        for (ClassId classId : ClassId.values()) {
            if(classId.getId() == selectedClassId)
            {
                if(playerCurrentClassId.level() < classId.level())
                {
                    ClassId parent = classId.getParent();
                    if(parent != null && parent.level() <= playerCurrentClassId.level())
                    {
                        return parent;
                    }
                    else if(parent != null)
                    {
                        ClassId parent2 = parent.getParent();
                        if(parent2 != null && parent2.level() <= playerCurrentClassId.level())
                        {
                            return parent2;
                        }
                        else if(parent2 != null)
                        {
                            ClassId parent3 = parent2.getParent();
                            if(parent3.level() <= playerCurrentClassId.level())
                            {
                                return parent3;
                            }
                        }
                        else
                            return null;
                    }
                    else
                        return null;
                }
                else
                    return classId;
            }
        }
        return null;
    }

    private String getHtmSelectProf(List<ClassId> classIdList) {
        StringBuilder sb = new StringBuilder();
        for (ClassId classId : classIdList) {
            sb.append("<a action=\"bypass -h npc_%objectId%_Quest MultiProScript selectProf ").append(classId.getId()).append("\">").append("<ClassId>").append(classId).append("</ClassId>").append("</a>").append("<br>");
        }
        return sb.toString();
    }

    private QuestState checkQuest(Player player) {
        QuestState st = player.getQuestState(MultiProScript.class.getSimpleName());
        if(st == null) {
            st = newQuestState(player);
            st.setState(State.STARTED);
            player.setQuestState(st);
        }

        return st;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        String newFileName = null;
        for (Map.Entry<Integer, String> entry : MultiProManager.getInstance().getAllowedNpcAndHtmlPath().entrySet()) {
            Integer npcId = entry.getKey();

            if(npcId == npc.getId())
            {
                newFileName = entry.getValue();
                break;
            }
        }

        String content = newFileName == null ? null : getHtm(player, newFileName);
        if(content != null) {
            content = content.replace("%objectId%", String.valueOf(npc.getObjectId()));
            player.setLastQuestNpcObject(npc.getObjectId());
        }
        return content;
    }

    public static void main(String[] args) {
        new MultiProScript();
    }
}
