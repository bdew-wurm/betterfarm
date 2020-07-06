package net.bdew.wurm.betterfarm.trees;

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
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.api.ITileAction;

public abstract class TreeActionBase implements ITileAction {
    abstract boolean checkRole(VillageRole role);

    abstract int getPrimarySkill();

    @Override
    public boolean checkSkill(Creature performer, float needed) {
        return performer.getSkills().getSkillOrLearn(getPrimarySkill()).getRealKnowledge() >= needed;
    }

    public boolean isTree(int tile) {
        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        return t.isNormalTree() || t.isNormalBush() || (BetterFarmMod.allowInfectedTrees && (t.isMyceliumTree() || t.isMyceliumBush()));
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return performer.isPlayer() && onSurface;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg) {
        if (!canStartOn(performer, source, tilex, tiley, onSurface, tile)) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        VolaTile vt = Zones.getOrCreateTile(tilex, tiley, onSurface);

        final BlockingResult blockers = Blocking.getBlockerBetween(performer, performer.getPosX(), performer.getPosY(), (tilex << 2) + 2, (tiley << 2) + 2, performer.getPositionZ(), Tiles.decodeHeightAsFloat(tile), onSurface, onSurface, false, Blocker.TYPE_ALL, -1L, performer.getBridgeId(), -10L, false);
        if (blockers != null && blockers.getFirstBlocker() != null) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s since a %s blocks you.", t.getName().toLowerCase(), blockers.getFirstBlocker().getName().toLowerCase()));
            return false;
        }

        if (performer.getPower() < 2) {
            Village village = vt.getVillage();
            if (village != null) {
                VillageRole role = village.getRoleFor(performer);
                if (role == null || !checkRole(role)) {
                    if (sendMsg)
                        performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's against local laws.", t.getName().toLowerCase()));
                    return false;
                }
            }
        }

        return true;
    }

    boolean afterActionCompleted(Creature performer, Item source) {
        if (source.setDamage(source.getDamage() + 0.003f * source.getDamageModifier())) {
            performer.getCommunicator().sendNormalServerMessage(String.format("Your %s broke!", source.getName()));
            return false;
        } else return true;
    }
}
