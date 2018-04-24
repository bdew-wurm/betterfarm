package net.bdew.wurm.betterfarm.area;

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
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class CultivateActionPerformer extends AreaActionPerformer {
    public CultivateActionPerformer(int radius, float skillLevel) {
        super(new ActionEntryBuilder((short) ModActions.getNextActionId(), String.format("Cultivate (%dx%d)", 2 * radius + 1, 2 * radius + 1), "cultivating", new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                36 /* ACTION_TYPE_ALWAYS_USE_ACTIVE_ITEM */
        }).range(4).build(), radius, skillLevel, Actions.CULTIVATE);
    }

    private boolean isCultivatable(byte type) {
        return type == Tiles.Tile.TILE_DIRT_PACKED.id || type == Tiles.Tile.TILE_MOSS.id || type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_STEPPE.id || type == Tiles.Tile.TILE_MYCELIUM.id;
    }

    @Override
    protected boolean canStartOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return performer.isPlayer() && performer.getSkills().getSkillOrLearn(SkillList.DIGGING).getKnowledge() >= skillLevel &&
                source != null && (source.getTemplateId() == ItemList.shovel || source.getTemplateId() == ItemList.rake) && onSurface;
    }

    @Override
    protected boolean canActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!performer.isPlayer() || source == null || (source.getTemplateId() != ItemList.shovel && source.getTemplateId() != ItemList.rake) || !onSurface)
            return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (!isCultivatable(t.id)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it can't be cultivated.", t.getName().toLowerCase()));
            return false;
        }

        return super.canActOnTile(performer, source, tilex, tiley, onSurface, tile, message);
    }


    @Override
    protected void doActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, float baseTime) {
        performer.getCommunicator().sendNormalServerMessage("You cultivate some soil and it's ready to sow now.");
        Server.getInstance().broadCastAction(performer.getName() + " cultivates some soil.", performer, 5);
        performer.getStatus().modifyStamina(-1000.0F);
        source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier());
        Skill digging = performer.getSkills().getSkillOrLearn(SkillList.DIGGING);
        digging.skillCheck(14.0D, source, 0.0D, false, baseTime);
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
    }

    @Override
    protected void doAnimation(Creature performer) {
        performer.playAnimation("farm", false);
    }

    @Override
    protected float tileActionTime(Creature performer, Item source) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.DIGGING), source, 0.0D);
    }
}
