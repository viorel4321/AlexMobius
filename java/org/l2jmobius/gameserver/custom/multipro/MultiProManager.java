package org.l2jmobius.gameserver.custom.multipro;

import org.l2jmobius.commons.util.PropertiesParser;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.instancemanager.QuestManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class MultiProManager {
    public static final Logger LOGGER = Logger.getLogger(MultiProManager.class.getName());
    private final String _configFile = "config/Custom/MultiPro.ini";
    private HashMap<Integer, String> MultiProAllowedNpcAndHtmlPath;
    private HashMap<Integer, SkillItemLearnReq> MultiProSkillItemLearnReqHashMap;
    private int _changeReqSpForItemId;
    public MultiProManager()
    {
        load();
    }

    private void load() {
        try {
            final PropertiesParser confParser = new PropertiesParser(_configFile);

            LearnReqs:
            {
                MultiProSkillItemLearnReqHashMap = new HashMap<>();
                _changeReqSpForItemId = confParser.getInt("changeReqSpForItem", 0);
                if(_changeReqSpForItemId == 0) {
                    String skillsLearnItem = confParser.getString("skillLearnItem", "");
                    StringTokenizer st1 = new StringTokenizer(skillsLearnItem, ":");
                    while (st1.hasMoreTokens()) {
                        try {
                            String skillLearnItem = st1.nextToken();
                            StringTokenizer st2 = new StringTokenizer(skillLearnItem, ",");
                            int skillId = Integer.parseInt(st2.nextToken());
                            int skillLvl = Integer.parseInt(st2.nextToken());
                            int itemId = Integer.parseInt(st2.nextToken());
                            int itemCount = Integer.parseInt(st2.nextToken());

                            MultiProSkillItemLearnReqHashMap.put(SkillData.getSkillHashCode(skillId, skillLvl), new SkillItemLearnReq(skillId, skillLvl, itemId, itemCount));
                        } catch (Exception err) {
                            LOGGER.info("Error when get multi pro skill cond: " + err);
                        }
                    }
                }
            }

            AllowedNpc:
            {
                String allowedNpcConf = confParser.getString("allowedNpc", "");
                MultiProAllowedNpcAndHtmlPath = new HashMap<>();
                StringTokenizer st1 = new StringTokenizer(allowedNpcConf, ";");
                while (st1.hasMoreTokens()) {
                    StringTokenizer st2 = new StringTokenizer(st1.nextToken(), ":");
                    int npcId = Integer.parseInt(st2.nextToken());
                    String htmlPath = st2.nextToken();
                    MultiProAllowedNpcAndHtmlPath.put(npcId, htmlPath);
                    NpcTemplate npc = NpcData.getInstance().getTemplate(npcId);
                }
            }

        } catch (Exception err) {
            LOGGER.info("Error when load multi pro config: " + err);
        }
    }


    public void showHtmlFile(Player player, String file, Npc npc)
    {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        html.setFile(player, file);
        html.replace("%npc_name%", npc.getName());
        player.sendPacket(html);
    }

    public void onAcquireSkillInfoRequested(Player player, int id, int level) {
        QuestManager.getInstance().getQuest("MultiProScript").notifyEvent("sk_info_requested_" + id + "_" + level, player.getLastFolkNPC(), player);
    }

    public void onAcquireSkillRequested(Player player, int id, int level) {
        QuestManager.getInstance().getQuest("MultiProScript").notifyEvent("sk_learn_request_" + id + "_" + level, player.getLastFolkNPC(), player);
    }

    public static class SkillItemLearnReq
    {
        int skillId;
        int skillLvl;
        int itemId;
        int itemCount;
        int spCost;

        public SkillItemLearnReq(int skillId, int skillLvl, int itemId, int itemCount) {
            this.skillId = skillId;
            this.skillLvl = skillLvl;
            this.itemId = itemId;
            this.itemCount = itemCount;
        }

        public int getSkillId() {
            return skillId;
        }

        public int getSkillLvl() {
            return skillLvl;
        }

        public int getItemId() {
            return itemId;
        }

        public int getItemCount() {
            return itemCount;
        }

        public int getSpCost() {
            return spCost;
        }
    }

    public int getChangeReqSpForItemId() {
        return _changeReqSpForItemId;
    }

    public HashMap<Integer, String> getAllowedNpcAndHtmlPath() {
        return MultiProAllowedNpcAndHtmlPath;
    }

    public HashMap<Integer, SkillItemLearnReq> getSkillItemLearnReqHashMap() {
        return MultiProSkillItemLearnReqHashMap;
    }

    public static MultiProManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        protected static final MultiProManager INSTANCE = new MultiProManager();
    }
}
