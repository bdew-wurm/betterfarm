package net.bdew.wurm.betterfarm.fields;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;

public class FieldActionCultivate extends FieldActionBase {
    private boolean isCultivatable(byte type) {
        return type == Tiles.Tile.TILE_DIRT_PACKED.id || type == Tiles.Tile.TILE_MOSS.id || type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_STEPPE.id || type == Tiles.Tile.TILE_MYCELIUM.id;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayCultivate();
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return source != null && (source.getTemplateId() == ItemList.shovel || source.getTemplateId() == ItemList.rake) && onSurface;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, message)) return false;
        if (!canStartOn(performer, source, tilex, tiley, onSurface, tile)) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (!isCultivatable(t.id)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it can't be cultivated.", t.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, byte rarity) {
        performer.getCommunicator().sendNormalServerMessage("You cultivate some soil and it's ready to sow now.");
        Server.getInstance().broadCastAction(performer.getName() + " cultivates some soil.", performer, 5);
        performer.getStatus().modifyStamina(-1000.0F);
        Skill digging = performer.getSkills().getSkillOrLearn(SkillList.DIGGING);
        digging.skillCheck(14.0D, source, 0.0D, false, 10);
        Methods.sendSound(performer, "sound.work.digging" + (Server.rand.nextInt(3) + 1));
        short h = Tiles.decodeHeight(tile);
        Server.setSurfaceTile(tilex, tiley, h, Tiles.Tile.TILE_DIRT.id, (byte) 0);
        Players.getInstance().sendChangedTiles(tilex, tiley, 1, 1, onSurface, true);
        try {
            Zone toCheckForChange = Zones.getZone(tilex, tiley, onSurface);
            toCheckForChange.changeTile(tilex, tiley);
        } catch (NoSuchZoneException e) {
            BetterFarmMod.logException("Failed to get zone for tile", e);
        }
        if (source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier())) {
            performer.getCommunicator().sendNormalServerMessage(String.format("Your %s broke!", source.getName().toLowerCase()));
            return false;
        }
        return true;
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        performer.playAnimation("farm", false);
        return true;
    }

    @Override
    public boolean checkSkill(Creature performer, float needed) {
        return performer.getSkills().getSkillOrLearn(SkillList.DIGGING).getRealKnowledge() >= needed;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.DIGGING), source, 0.0D);
    }
}
