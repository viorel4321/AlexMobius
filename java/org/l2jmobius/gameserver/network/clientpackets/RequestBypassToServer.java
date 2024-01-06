/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.network.clientpackets;

import java.util.Date;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.l2jmobius.Config;
import org.l2jmobius.commons.network.ReadablePacket;
import org.l2jmobius.commons.util.CommonUtil;
import org.l2jmobius.gameserver.ai.CtrlIntention;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.handler.AdminCommandHandler;
import org.l2jmobius.gameserver.handler.BypassHandler;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.model.Elementals;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.impl.creature.npc.OnNpcManorBypass;
import org.l2jmobius.gameserver.model.events.impl.creature.player.OnPlayerBypass;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.olympiad.Hero;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.network.Disconnection;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.ServerPackets;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.ExOpenMPCC;
import org.l2jmobius.gameserver.network.serverpackets.LeaveWorld;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;
import org.l2jmobius.gameserver.util.Util;

import org.l2jmobius.gameserver.network.serverpackets.ExSendUIEvent;

import static org.l2jmobius.gameserver.scripting.java.ScriptingClassLoader.LOGGER;

/**
 * RequestBypassToServer client packet implementation.
 *
 * @author HorridoJoho
 */
public class RequestBypassToServer implements ClientPacket {
    // FIXME: This is for compatibility, will be changed when bypass functionality got an overhaul by NosBit

    private static final String[] _possibleNonHtmlCommands =
            {
                    "_bbs",
                    "bbs",
                    "_mail",
                    "_friend",
                    "_match",
                    "_diary",
                    "_olympiad?command",
                    "manor_menu_select",
                    "_StatusWnd",
                    "_whoami"
            };

    // S
    private String _command;

    @Override
    public void read(ReadablePacket packet) {
        _command = packet.readString();
    }

    @Override
    public void run(GameClient client) {
        final Player player = client.getPlayer();

        if (player == null) {
            return;
        }

        if (_command.isEmpty()) {
            PacketLogger.warning(player + " sent empty bypass!");
            Disconnection.of(client, player).defaultSequence(LeaveWorld.STATIC_PACKET);
            return;
        }

        boolean requiresBypassValidation = true;
        for (String possibleNonHtmlCommand : _possibleNonHtmlCommands) {
            if (_command.startsWith(possibleNonHtmlCommand)) {
                requiresBypassValidation = false;
                break;
            }
        }

        int bypassOriginId = 0;
        if (requiresBypassValidation) {
            bypassOriginId = player.validateHtmlAction(_command);
            if (bypassOriginId == -1) {
                return;
            }

            if ((bypassOriginId > 0) && !Util.isInsideRangeOfObjectId(player, bypassOriginId, Npc.INTERACTION_DISTANCE)) {
                // No logging here, this could be a common case where the player has the html still open and run too far away and then clicks a html action
                return;
            }
        }

        if (!client.getFloodProtectors().canUseServerBypass()) {
            return;
        }

        try {
            if (_command.startsWith("admin_")) {
                AdminCommandHandler.getInstance().useAdminCommand(player, _command, true);
            } else if (CommunityBoardHandler.getInstance().isCommunityBoardCommand(_command)) {
                CommunityBoardHandler.getInstance().handleParseCommand(_command, player);
            } else if (_command.equals("come_here") && player.isGM()) {
                comeHere(player);
            } else if (_command.startsWith("npc_")) {
                final int endOfId = _command.indexOf('_', 5);
                String id;
                if (endOfId > 0) {
                    id = _command.substring(4, endOfId);
                } else {
                    id = _command.substring(4);
                }
                if (Util.isDigit(id)) {
                    final WorldObject object = World.getInstance().findObject(Integer.parseInt(id));
                    if ((object != null) && object.isNpc() && (endOfId > 0) && player.isInsideRadius2D(object, Npc.INTERACTION_DISTANCE)) {
                        ((Npc) object).onBypassFeedback(player, _command.substring(endOfId + 1));
                    }
                }

                player.sendPacket(ActionFailed.STATIC_PACKET);
            } else if (_command.startsWith("item_")) {
                final int endOfId = _command.indexOf('_', 5);
                String id;
                if (endOfId > 0) {
                    id = _command.substring(5, endOfId);
                } else {
                    id = _command.substring(5);
                }
                try {
                    final Item item = player.getInventory().getItemByObjectId(Integer.parseInt(id));
                    if ((item != null) && (endOfId > 0)) {
                        item.onBypassFeedback(player, _command.substring(endOfId + 1));
                    }

                    player.sendPacket(ActionFailed.STATIC_PACKET);
                } catch (NumberFormatException nfe) {
                    PacketLogger.warning("NFE for command [" + _command + "] " + nfe.getMessage());
                }
            } else if (_command.startsWith("_match")) {
                final String params = _command.substring(_command.indexOf('?') + 1);
                final StringTokenizer st = new StringTokenizer(params, "&");
                final int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
                final int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
                final int heroid = Hero.getInstance().getHeroByClass(heroclass);
                if (heroid > 0) {
                    Hero.getInstance().showHeroFights(player, heroclass, heroid, heropage);
                }
            } else if (_command.startsWith("_diary")) {
                final String params = _command.substring(_command.indexOf('?') + 1);
                final StringTokenizer st = new StringTokenizer(params, "&");
                final int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
                final int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
                final int heroid = Hero.getInstance().getHeroByClass(heroclass);
                if (heroid > 0) {
                    Hero.getInstance().showHeroDiary(player, heroclass, heroid, heropage);
                }
            }
//Вывести html

            else if (_command.startsWith("_StatusWnd"))//todo new window statuswnd
            {
                // item html
                NpcHtmlMessage msg = new NpcHtmlMessage(0, 1, HtmCache.getInstance().getHtm(player, "data/custom/Window/StatusWnd/status.htm"));


                int PAtk = (int) player.getPAtk(player);
                int MAtk = (int) player.getMAtk(player, null);
                int pDef = (int) player.getPDef(player);
                int mDef = (int) player.getMDef(player, null);
                int accuracy = player.getAccuracy();
                int PAtkSpd = (int) player.getPAtkSpd();
                int MAtkSpd = player.getMAtkSpd();
                int MoveSpeed = (int) player.getMoveSpeed();
                int Evasion = player.getEvasionRate(player);
                int CritRate = (int) player.calcStat(Stat.CRITICAL_RATE, 0, player, null);
                int str = player.getSTR();
                int con = player.getCON();
                int dex = player.getDEX();
                int INT = player.getINT();
                int wit = player.getWIT();
                int men = player.getMEN();
                int fireRes = (int) player.calcStat(Stat.FIRE_RES, 0, player, null);
                int windRes = (int) player.calcStat(Stat.WIND_RES, 0, player, null);
                int waterRes = (int) player.calcStat(Stat.WATER_RES, 0, player, null);
                int earthRes = (int) player.calcStat(Stat.EARTH_RES, 0, player, null);
                int holyRes = (int) player.calcStat(Stat.HOLY_RES, 0, player, null);
                int darkRes = (int) player.calcStat(Stat.DARK_RES, 0, player, null);

                //проверка игрока на элемент атаки
                if (player.getAttackElementValue(Elementals.FIRE) >= 1) {
                    msg.replace("%NameElementals%", "Огонь");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.FIRE));
                } else if (player.getAttackElementValue(Elementals.EARTH) >= 1) {
                    msg.replace("%NameElementals%", "Земля");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.EARTH));
                } else if (player.getAttackElementValue(Elementals.DARK) >= 1) {
                    msg.replace("%NameElementals%", "Тьма");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.DARK));
                } else if (player.getAttackElementValue(Elementals.WIND) >= 1) {
                    msg.replace("%NameElementals%", "Ветер");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.WIND));
                } else if (player.getAttackElementValue(Elementals.HOLY) >= 1) {
                    msg.replace("%NameElementals%", "Святость");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.HOLY));
                } else if (player.getAttackElementValue(Elementals.WATER) >= 1) {
                    msg.replace("%NameElementals%", "Вода");
                    msg.replace("%Elementals%", player.getAttackElementValue(Elementals.WATER));
                } else {
                    //если нет атакующего элемента то отсылать String
                    msg.replace("%NameElementals%", "Нет");
                    msg.replace("%Elementals%", "0");
                }


                final Clan clan = player.getClan();
                if (clan == null) {
                    msg.replace("%clan%", "Нет");
                } else {
                    msg.replace("%clan%", clan.getName());
                }

                msg.replace("%fame%",player.getFame());
                msg.replace("%Karma%",player.getKarma());
                msg.replace("%pc%", player.getPkKills());
                msg.replace("%pvp%", player.getPvpKills());
                msg.replace("%sp%", player.getSp());
                msg.replace("%class%", ClassListData.getInstance().getClass(player.getActiveClass()).getClientCode());
                msg.replace("%Name%", player.getName());
                msg.replace("%PAtk%", PAtk);
                msg.replace("%MAtk%", MAtk);
                msg.replace("%accuracy%", accuracy);
                msg.replace("%mDef%", mDef);
                msg.replace("%pDef%", pDef);
                msg.replace("%PAtkSpd%", PAtkSpd);
                msg.replace("%MAtkSpd%", MAtkSpd);
                msg.replace("%MoveSpeed%", MoveSpeed);
                msg.replace("%Evasion%", Evasion);
                msg.replace("%CritRate%", CritRate);

                msg.replace("%str%", str);
                msg.replace("%con%", con);
                msg.replace("%dex%", dex);
                msg.replace("%INT%", INT);
                msg.replace("%wit%", wit);
                msg.replace("%men%", men);

                msg.replace("%fireRes%", fireRes);
                msg.replace("%windRes%", windRes);
                msg.replace("%waterRes%", waterRes);
                msg.replace("%earthRes%", earthRes);
                msg.replace("%holyRes%", holyRes);
                msg.replace("%darkRes%", darkRes);

                msg.disableValidation();
                player.sendPacket(msg);

            } else if (_command.startsWith("_whoami"))//todo new window statuswnd
            {
                // item htm
                NpcHtmlMessage msg = new NpcHtmlMessage(0, 1, HtmCache.getInstance().getHtm(player, "data/custom/Window/StatusWnd/whoami.htm"));

                msg.disableValidation();
                player.sendPacket(msg);

            } else if (_command.startsWith("_olympiad?command")) {
                final int arenaId = Integer.parseInt(_command.split("=")[2]);
                final IBypassHandler handler = BypassHandler.getInstance().getHandler("arenachange");
                if (handler != null) {
                    handler.useBypass("arenachange " + (arenaId - 1), player, null);
                }
            } else if (_command.startsWith("manor_menu_select")) {
                final Npc lastNpc = player.getLastFolkNPC();
                if (Config.ALLOW_MANOR && (lastNpc != null) && lastNpc.canInteract(player) && EventDispatcher.getInstance().hasListener(EventType.ON_NPC_MANOR_BYPASS, lastNpc)) {
                    final String[] split = _command.substring(_command.indexOf('?') + 1).split("&");
                    final int ask = Integer.parseInt(split[0].split("=")[1]);
                    final int state = Integer.parseInt(split[1].split("=")[1]);
                    final boolean time = split[2].split("=")[1].equals("1");
                    EventDispatcher.getInstance().notifyEventAsync(new OnNpcManorBypass(player, lastNpc, ask, state, time), lastNpc);
                }
            } else {
                final IBypassHandler handler = BypassHandler.getInstance().getHandler(_command);
                if (handler != null) {
                    if (bypassOriginId > 0) {
                        final WorldObject bypassOrigin = World.getInstance().findObject(bypassOriginId);
                        if ((bypassOrigin != null) && bypassOrigin.isCreature()) {
                            handler.useBypass(_command, player, (Creature) bypassOrigin);
                        } else {
                            handler.useBypass(_command, player, null);
                        }
                    } else {
                        handler.useBypass(_command, player, null);
                    }
                } else {
                    PacketLogger.warning(client + " sent not handled RequestBypassToServer: [" + _command + "]");
                }
            }
        } catch (Exception e) {
            PacketLogger.warning("Exception processing bypass from " + player + ": " + _command + " " + e.getMessage());
            PacketLogger.warning(CommonUtil.getStackTrace(e));
            if (player.isGM()) {
                final StringBuilder sb = new StringBuilder(200);
                sb.append("<html><body>");
                sb.append("Bypass error: " + e + "<br1>");
                sb.append("Bypass command: " + _command + "<br1>");
                sb.append("StackTrace:<br1>");
                for (StackTraceElement ste : e.getStackTrace()) {
                    sb.append(ste + "<br1>");
                }
                sb.append("</body></html>");
                // item html
                final NpcHtmlMessage msg = new NpcHtmlMessage(0, 1, sb.toString());
                msg.disableValidation();
                player.sendPacket(msg);
            }
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_BYPASS, player)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerBypass(player, _command), player);
        }
    }

    /**
     * @param player
     */
    private void comeHere(Player player) {
        final WorldObject obj = player.getTarget();
        if (obj == null) {
            return;
        }
        if (obj instanceof Npc) {
            final Npc temp = (Npc) obj;
            temp.setTarget(player);
            temp.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, player.getLocation());
        }
    }
}
