package net.bdew.wurm.betterfarm.trees;

import com.wurmonline.mesh.*;
import com.wurmonline.server.Items;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.Materials;
import com.wurmonline.server.players.AchievementList;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;

public class TreeActionPlant extends TreeActionBase {
    private Item findSprout(Item container) {
        for (Item item : container.getAllItems(true)) {
            if (item.getTemplateId() == ItemList.sprout &&
                    (Materials.getTreeTypeForWood(item.getMaterial()) != null || Materials.getBushTypeForWood(item.getMaterial()) != null)) {
                return item;
            }
        }
        return null;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayPlantSprouts();
    }

    @Override
    int getPrimarySkill() {
        return SkillList.GARDENING;
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return super.canStartOn(performer, source, tilex, tiley, onSurface, tile)
                && (Tiles.decodeType(tile) == Tiles.Tile.TILE_DIRT.id || Tiles.decodeType(tile) == Tiles.Tile.TILE_GRASS.id)
                && source != null && source.isHollow() && findSprout(source) != null;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, sendMsg)) return false;

        final VolaTile vt = Zones.getOrCreateTile(tilex, tiley, onSurface);
        if (vt != null && vt.getStructure() != null) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip a tile as it's %s.",
                        vt.getStructure().isTypeBridge() ? "under a bridge" : "inside a house"));
            return false;
        }

        if (Terraforming.isCornerUnderWater(tilex, tiley, onSurface)) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage("You skip a tile as the ground is too wet.");
            return false;
        }

        return true;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);
        return Actions.getStandardActionTime(performer, gardening, null, 0.0);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        if (findSprout(source) == null) {
            performer.getCommunicator().sendNormalServerMessage("You stop planting as you're out of sprouts.");
            return false;
        }
        performer.getCommunicator().sendNormalServerMessage("You start planting the sprout.");
        Server.getInstance().broadCastAction(performer.getName() + " starts to plant a sprout.", performer, 5);
        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, byte rarity) {
        byte tileType = Tiles.decodeType(tile);

        if (tileType != Tiles.Tile.TILE_DIRT.id && tileType != Tiles.Tile.TILE_GRASS.id) {
            performer.getCommunicator().sendNormalServerMessage("You try to plant a sprout but discover something already growing in the ground!");
            return true;
        }

        Item sprout = findSprout(source);
        if (sprout == null) {
            performer.getCommunicator().sendNormalServerMessage("You stop planting as you're out of sprouts.");
            return false;
        }

        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);
        gardening.skillCheck(1.0f + sprout.getDamage(), sprout.getCurrentQualityLevel(), false, 10);

        SoundPlayer.playSound("sound.forest.branchsnap", tilex, tiley, onSurface, 0.0f);

        int newData = Tiles.encodeTreeData(FoliageAge.YOUNG_ONE, false, true, GrassData.GrowthTreeStage.SHORT);

        TreeData.TreeType treeType = Materials.getTreeTypeForWood(sprout.getMaterial());
        if (treeType != null) {
            Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), treeType.asNormalTree(), (byte) newData);
        } else {
            BushData.BushType bushType = Materials.getBushTypeForWood(sprout.getMaterial());
            Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), bushType.asNormalBush(), (byte) newData);
        }
        Server.setWorldResource(tilex, tiley, 0);
        performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(tilex, tiley, onSurface, true);
        if (performer.getDeity() != null && performer.getDeity().number == 1) {
            performer.maybeModifyAlignment(1.0f);
        }
        performer.achievement(AchievementList.ACH_PLANTING);
        performer.achievement(119);
        performer.getStatus().modifyStamina(-1000.0f);
        performer.getCommunicator().sendNormalServerMessage("You plant the sprout.");
        Server.getInstance().broadCastAction(performer.getName() + " plants a sprout.", performer, 5);
        Items.destroyItem(sprout.getWurmId());
        return true;
    }
}
