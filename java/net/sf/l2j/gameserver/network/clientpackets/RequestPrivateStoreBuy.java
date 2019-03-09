package net.sf.l2j.gameserver.network.clientpackets;

import java.util.HashSet;
import java.util.Set;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.ItemRequest;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.actor.instance.Player.StoreType;
import net.sf.l2j.gameserver.model.tradelist.TradeList;
import net.sf.l2j.gameserver.network.SystemMessageId;

public final class RequestPrivateStoreBuy extends L2GameClientPacket
{
	private static final int BATCH_LENGTH = 12; // length of one item
	
	private int _storePlayerId;
	private Set<ItemRequest> _items = null;
	
	@Override
	protected void readImpl()
	{
		_storePlayerId = readD();
		int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET || count * BATCH_LENGTH != _buf.remaining())
			return;
		
		_items = new HashSet<>();
		
		for (int i = 0; i < count; i++)
		{
			int objectId = readD();
			long cnt = readD();
			int price = readD();
			
			if (objectId < 1 || cnt < 1 || price < 0)
			{
				_items = null;
				return;
			}
			
			_items.add(new ItemRequest(objectId, (int) cnt, price));
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (_items == null)
			return;
		
		final Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (player.isCursedWeaponEquipped())
			return;
		
		final Player storePlayer = World.getInstance().getPlayer(_storePlayerId);
		if (storePlayer == null)
			return;
		
		if (!player.isInsideRadius(storePlayer, Npc.INTERACTION_DISTANCE, true, false))
			return;
		
		if (!(storePlayer.getStoreType() == StoreType.SELL || storePlayer.getStoreType() == StoreType.PACKAGE_SELL))
			return;
		
		final TradeList storeList = storePlayer.getSellList();
		if (storeList == null)
			return;
		
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		if (storePlayer.getStoreType() == StoreType.PACKAGE_SELL && storeList.getItems().size() > _items.size())
			return;
		
		if (!storeList.privateStoreBuy(player, _items))
			return;
		
		if (storeList.getItems().isEmpty())
		{
			storePlayer.setStoreType(StoreType.NONE);
			storePlayer.broadcastUserInfo();
		}
	}
}