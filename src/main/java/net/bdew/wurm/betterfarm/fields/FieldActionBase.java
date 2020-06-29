package net.bdew.wurm.betterfarm.fields;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.structures.Blocker;
import com.wurmonline.server.structures.Blocking;
import com.wurmonline.server.structures.BlockingResult;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.api.ITileAction;

public abstract class FieldActionBase implements ITileAction {
    abstract boolean checkRole(VillageRole role);

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!performer.isPlayer() || !onSurface) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (performer.getPower() < 2) {
            if (Zones.protectedTiles[tilex][tiley]) {
                if (message)
                    performer.getCommunicator().sendNormalServerMessage(String.format("Some unnatural force stops you from touching the %s, you decide to skip it.", t.getName().toLowerCase()));
                return false;
            }

            VolaTile vt = Zones.getOrCreateTile(tilex, tiley, onSurface);
            Village village = vt.getVillage();
            if (village != null) {
                VillageRole role = village.getRoleFor(performer);
                if (role == null || !checkRole(role)) {
                    if (message)
                        performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's against local laws.", t.getName().toLowerCase()));
                    return false;
                }
            }
        }

        final BlockingResult blockers = Blocking.getBlockerBetween(performer, performer.getPosX(), performer.getPosY(), (tilex << 2) + 2, (tiley << 2) + 2, performer.getPositionZ(), Tiles.decodeHeightAsFloat(tile), onSurface, onSurface, false, Blocker.TYPE_ALL, -1L, performer.getBridgeId(), -10L, false);
        if (blockers != null && blockers.getFirstBlocker() != null) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s since a %s blocks you.", t.getName().toLowerCase(), blockers.getFirstBlocker().getName().toLowerCase()));
            return false;
        }

        return true;
    }
}
