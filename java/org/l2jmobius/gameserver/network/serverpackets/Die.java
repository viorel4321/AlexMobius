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
package org.l2jmobius.gameserver.network.serverpackets;

import org.l2jmobius.gameserver.data.xml.AdminData;
import org.l2jmobius.gameserver.instancemanager.CHSiegeManager;
import org.l2jmobius.gameserver.instancemanager.CastleManager;
import org.l2jmobius.gameserver.instancemanager.FortManager;
import org.l2jmobius.gameserver.instancemanager.TerritoryWarManager;
import org.l2jmobius.gameserver.model.AccessLevel;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.olympiad.OlympiadManager;
import org.l2jmobius.gameserver.model.siege.Castle;
import org.l2jmobius.gameserver.model.siege.Fort;
import org.l2jmobius.gameserver.model.siege.SiegeClan;
import org.l2jmobius.gameserver.model.siege.clanhalls.SiegableHall;
import org.l2jmobius.gameserver.network.ServerPackets;

public class Die extends ServerPacket
{
	private final int _objectId;
	private final boolean _canTeleport;
	private final boolean _sweepable;
	private AccessLevel _access = AdminData.getInstance().getAccessLevel(0);
	private Clan _clan;
	private final Creature _creature;
	private boolean _isJailed;
	private boolean _staticRes = false;
	
	public Die(Creature creature)
	{
		_objectId = creature.getObjectId();
		_creature = creature;
		if (creature.isPlayer())
		{
			final Player player = creature.getActingPlayer();
			_access = player.getAccessLevel();
			_clan = player.getClan();
			_isJailed = player.isJailed();
		}
		_canTeleport = creature.canRevive() && !creature.isPendingRevive();
		_sweepable = creature.isSweepActive();
	}
	
	@Override
	public void write()
	{
		ServerPackets.DIE.writeId(this);
		writeInt(_objectId);
		writeInt(_canTeleport);
		if (_creature.isPlayer())
		{
			if (!OlympiadManager.getInstance().isRegistered(_creature.getActingPlayer()) && !_creature.getActingPlayer().isOnEvent())
			{
				_staticRes = _creature.getInventory().haveItemForSelfResurrection();
			}
			// Verify if player can use fixed resurrection without Feather
			if (_access.allowFixedRes())
			{
				_staticRes = true;
			}
		}
		if (_canTeleport && (_clan != null) && !_isJailed)
		{
			boolean isInCastleDefense = false;
			boolean isInFortDefense = false;
			SiegeClan siegeClan = null;
			final Castle castle = CastleManager.getInstance().getCastle(_creature);
			final Fort fort = FortManager.getInstance().getFort(_creature);
			final SiegableHall hall = CHSiegeManager.getInstance().getNearbyClanHall(_creature);
			if ((castle != null) && castle.getSiege().isInProgress())
			{
				// siege in progress
				siegeClan = castle.getSiege().getAttackerClan(_clan);
				if ((siegeClan == null) && castle.getSiege().checkIsDefender(_clan))
				{
					isInCastleDefense = true;
				}
			}
			else if ((fort != null) && fort.getSiege().isInProgress())
			{
				// siege in progress
				siegeClan = fort.getSiege().getAttackerClan(_clan);
				if ((siegeClan == null) && fort.getSiege().checkIsDefender(_clan))
				{
					isInFortDefense = true;
				}
			}
			writeInt(_clan.getHideoutId() > 0); // 6d 01 00 00 00 - to hide away
			writeInt((_clan.getCastleId() > 0) || isInCastleDefense); // 6d 02 00 00 00 - to castle
			writeInt((TerritoryWarManager.getInstance().getHQForClan(_clan) != null) || ((siegeClan != null) && !isInCastleDefense && !isInFortDefense && !siegeClan.getFlag().isEmpty()) || ((hall != null) && (hall.getSiege() != null) && hall.getSiege().checkIsAttacker(_clan))); // siege HQ
			writeInt(_sweepable); // sweepable (blue glow)
			writeInt(_staticRes); // 6d 04 00 00 00 - to FIXED
			writeInt((_clan.getFortId() > 0) || isInFortDefense); // 6d 05 00 00 00 - to fortress
		}
		else
		{
			writeInt(0); // 6d 01 00 00 00 - to hide away
			writeInt(0); // 6d 02 00 00 00 - to castle
			writeInt(0); // 6d 03 00 00 00 - to siege HQ
			writeInt(_sweepable); // sweepable (blue glow)
			writeInt(_staticRes); // 6d 04 00 00 00 - to FIXED
			writeInt(0); // 6d 05 00 00 00 - to fortress
		}
		// TODO: protocol 152
		// writeByte(0); // show die animation
		// writeInt(0); // agathion ress button
		// writeInt(0); // additional free space
	}
}
