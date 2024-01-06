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
package ai.bosses.QueenAnt;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.Config;
import org.l2jmobius.gameserver.ai.CtrlIntention;
import org.l2jmobius.gameserver.instancemanager.GrandBossManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.holders.SkillHolder;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.type.BossZone;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.PlaySound;

import ai.AbstractNpcAI;

/**
 * Queen Ant's AI
 * @author Emperorc
 */
public class QueenAnt extends AbstractNpcAI
{
	private static final int ANTHARAS = 40080;
	private static final int LARVA = 29002;
	private static final int NURSE = 29003;
	private static final int GUARD = 29004;
	private static final int ROYAL = 29005;





	// QUEEN Status Tracking :
	private static final byte ALIVE = 0; // Queen Ant is spawned.
	private static final byte DEAD = 1; // Queen Ant has been killed.



	private static SkillHolder HEAL1 = new SkillHolder(4020, 1);
	private static SkillHolder HEAL2 = new SkillHolder(4024, 1);

	Monster _queen = null;
	private Monster _larva = null;
	private final Set<Monster> _nurses = ConcurrentHashMap.newKeySet();

	private QueenAnt()
	{

		addFactionCallId(NURSE);


		final StatSet info = GrandBossManager.getInstance().getStatSet(ANTHARAS);
		if (GrandBossManager.getInstance().getStatus(ANTHARAS) == DEAD)
		{
			// load the unlock date and time for queen ant from DB
			final long temp = info.getLong("respawn_time") - System.currentTimeMillis();
			// if queen ant is locked until a certain time, mark it so and start the unlock timer
			// the unlock time has not yet expired.
			if (temp > 0)
			{
				startQuestTimer("queen_unlock", temp, null, null);
			}
			else
			{
				// the time has already expired while the server was offline. Immediately spawn queen ant.
				final GrandBoss queen = (GrandBoss) addSpawn(ANTHARAS, 2373, 236009, -3392, 0, false, 0);
				GrandBossManager.getInstance().setStatus(ANTHARAS, ALIVE);
				spawnBoss(queen);
			}
		}
	}

	private void spawnBoss(GrandBoss npc)
	{
		GrandBossManager.getInstance().addBoss(npc);

		GrandBossManager.getInstance().addBoss(npc);
		startQuestTimer("action", 10000, npc, null, true);
		startQuestTimer("heal", 1000, null, null, true);
		npc.broadcastPacket(new PlaySound(1, "BS01_A", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
		_queen = npc;
		_larva = (Monster) addSpawn(ANTHARAS, 2373, 236009, -3392, 0, false, 0);
	}

	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		switch (event)
		{
			case "heal":
			{
				boolean notCasting;
				final boolean larvaNeedHeal = (_larva != null) && (_larva.getCurrentHp() < _larva.getMaxHp());
				final boolean queenNeedHeal = (_queen != null) && (_queen.getCurrentHp() < _queen.getMaxHp());
				for (Monster nurse : _nurses)
				{
					if ((nurse == null) || nurse.isDead() || nurse.isCastingNow())
					{
						continue;
					}

					notCasting = nurse.getAI().getIntention() != CtrlIntention.AI_INTENTION_CAST;
					if (larvaNeedHeal)
					{
						if ((nurse.getTarget() != _larva) || notCasting)
						{
							nurse.setTarget(_larva);
							nurse.useMagic(getRandomBoolean() ? HEAL1.getSkill() : HEAL2.getSkill());
						}
						continue;
					}
					if (queenNeedHeal)
					{
						if (nurse.getLeader() == _larva)
						{
							continue;
						}

						if ((nurse.getTarget() != _queen) || notCasting)
						{
							nurse.setTarget(_queen);
							nurse.useMagic(HEAL1.getSkill());
						}
						continue;
					}
					// if nurse not casting - remove target
					if (notCasting && (nurse.getTarget() != null))
					{
						nurse.setTarget(null);
					}
				}
				break;
			}
			case "action":
			{
				if ((npc != null) && (getRandom(3) == 0))
				{
					if (getRandom(2) == 0)
					{
						npc.broadcastSocialAction(3);
					}
					else
					{
						npc.broadcastSocialAction(4);
					}
				}
				break;
			}
			case "antharas_unlock":
			{
				final GrandBoss queen = (GrandBoss) addSpawn(ANTHARAS, 2373, 236009, -3392, 0, false, 0);
				GrandBossManager.getInstance().setStatus(ANTHARAS, ALIVE);
				spawnBoss(queen);
				break;
			}
		}
		return super.onAdvEvent(event, npc, player);
	}

	@Override
	public String onSpawn(Npc npc)
	{
		final Monster mob = (Monster) npc;
		switch (npc.getId())
		{
			case LARVA:
			{
				mob.setImmobilized(true);
				mob.setMortal(false);
				mob.setIsRaidMinion(true);
				break;
			}
			case NURSE:
			{
				mob.disableCoreAI(true);
				mob.setIsRaidMinion(true);
				_nurses.add(mob);
				break;
			}
			case ROYAL:
			case GUARD:
			{
				mob.setIsRaidMinion(true);
				break;
			}
			case ANTHARAS:
			{
				cancelQuestTimer("DISTANCE_CHECK", npc, null);
				startQuestTimer("DISTANCE_CHECK", 5000, npc, null, true);
				break;
			}
		}
		return super.onSpawn(npc);
	}

	@Override
	public String onFactionCall(Npc npc, Npc caller, Player attacker, boolean isSummon)
	{
		if ((caller == null) || (npc == null))
		{
			return super.onFactionCall(npc, caller, attacker, isSummon);
		}

		if (!npc.isCastingNow() && (npc.getAI().getIntention() != CtrlIntention.AI_INTENTION_CAST) && (caller.getCurrentHp() < caller.getMaxHp()))
		{
			npc.setTarget(caller);
			((Attackable) npc).useMagic(HEAL1.getSkill());
		}
		return null;
	}

	@Override
	public String onAggroRangeEnter(Npc npc, Player player, boolean isSummon)
	{
		if ((npc == null) || (player.isGM() && player.isInvisible()))
		{
			return null;
		}

		final boolean isMage;
		final Playable character;
		if (isSummon)
		{
			isMage = false;
			character = player.getSummon();
		}
		else
		{
			isMage = player.isMageClass();
			character = player;
		}

		if (character == null)
		{
			return null;
		}



		return super.onAggroRangeEnter(npc, player, isSummon);
	}

	@Override
	public String onKill(Npc npc, Player killer, boolean isSummon)
	{
		final int npcId = npc.getId();
		if (npcId == ANTHARAS)
		{
			npc.broadcastPacket(new PlaySound(1, "BS02_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
			GrandBossManager.getInstance().setStatus(ANTHARAS, DEAD);
			// Calculate Min and Max respawn times randomly.
			final long respawnTime = (Config.QUEEN_ANT_SPAWN_INTERVAL + getRandom(-Config.QUEEN_ANT_SPAWN_RANDOM, Config.QUEEN_ANT_SPAWN_RANDOM)) * 3600000;
			startQuestTimer("queen_unlock", respawnTime, null, null);
			cancelQuestTimer("action", npc, null);
			cancelQuestTimer("heal", null, null);
			// also save the respawn time so that the info is maintained past reboots
			final StatSet info = GrandBossManager.getInstance().getStatSet(ANTHARAS);
			info.set("respawn_time", System.currentTimeMillis() + respawnTime);
			GrandBossManager.getInstance().setStatSet(ANTHARAS, info);
			_nurses.clear();
			if (_larva != null)
			{
				_larva.deleteMe();
			}
			_larva = null;
			_queen = null;
			cancelQuestTimers("DISTANCE_CHECK");
		}
		else if ((_queen != null) && !_queen.isAlikeDead())
		{
			if (npcId == ROYAL)
			{
				final Monster mob = (Monster) npc;
				if (mob.getLeader() != null)
				{
					mob.getLeader().getMinionList().onMinionDie(mob, (280 + getRandom(40)) * 1000);
				}
			}
			else if (npcId == NURSE)
			{
				final Monster mob = (Monster) npc;
				_nurses.remove(mob);
				if (mob.getLeader() != null)
				{
					mob.getLeader().getMinionList().onMinionDie(mob, 10000);
				}
			}
		}
		return super.onKill(npc, killer, isSummon);
	}

	public static void main(String[] args)
	{
		new QueenAnt();
	}
}
