package net.bdew.wurm.betterfarm.trees;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;
import net.bdew.wurm.betterfarm.BetterFarmMod;

public class TreeActionPick extends TreeActionBase {
    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return super.canStartOn(performer, source, tilex, tiley, onSurface, tile)
                && isTree(tile)
                && source != null && source.getTemplateId() == ItemList.sickle;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, sendMsg)) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        byte data = Tiles.decodeData(tile);
        FoliageAge age = FoliageAge.getFoliageAge(data);

        if (age != FoliageAge.MATURE_SPROUTING
                && age != FoliageAge.OLD_ONE_SPROUTING
                && age != FoliageAge.OLD_TWO_SPROUTING
                && age != FoliageAge.VERY_OLD_SPROUTING) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it has no sprouts to pick.", t.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayPickSprouts();
    }

    @Override
    int getPrimarySkill() {
        return SkillList.FORESTRY;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        return Actions.getStandardActionTime(performer, forestry, source, 0);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        if (!performer.getInventory().mayCreatureInsertItem()) {
            performer.getCommunicator().sendNormalServerMessage("You decide to stop as your inventory is full.");
            return false;
        }

        try {
            final int weight = ItemTemplateFactory.getInstance().getTemplate(ItemList.sprout).getWeightGrams();
            if (!performer.canCarry(weight)) {
                performer.getCommunicator().sendNormalServerMessage("You would not be able to carry the sprout. You need to drop some things first.");
                return false;
            }
        } catch (NoSuchTemplateException e) {
            BetterFarmMod.logException("Error getting sprout weight", e);
            return false;
        }

        Tiles.Tile tileObj = Tiles.getTile(Tiles.decodeType(tile));

        if (tileObj.isBush()) {
            performer.getCommunicator().sendNormalServerMessage("You start cutting a sprout from the bush.");
            Server.getInstance().broadCastAction(performer.getName() + " starts to cut a sprout off a bush.", performer, 5);
        } else {
            performer.getCommunicator().sendNormalServerMessage("You start cutting a sprout from the tree.");
            Server.getInstance().broadCastAction(performer.getName() + " starts to cut a sprout off a tree.", performer, 5);
        }
        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, byte rarity) {
        Tiles.Tile tileObj = Tiles.getTile(Tiles.decodeType(tile));
        byte data = Tiles.decodeData(tile);
        int age = FoliageAge.getAgeAsByte(data);

        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);

        if (age != 7 && age != 9 && age != 11 && age != 13) {
            performer.getCommunicator().sendNormalServerMessage("You try to pick a sprout but realize there are none on this trellis.");
            return true;
        }

        if (rarity != 0) {
            performer.playPersonalSound("sound.fx.drumroll");
        }

        double bonus = Math.max(1.0, sickle.skillCheck(1.0, source, 0.0, false, 10));
        double power = forestry.skillCheck(1.0, source, bonus, false, 10);

        try {
            float modifier = 1.0f;
            if (source.getSpellEffects() != null) {
                modifier = source.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
            }
            final Item sprout = ItemFactory.createItem(ItemList.sprout, Math.max(1.0f, Math.min(100.0f, (float) power * modifier + source.getRarity())), tileObj.materialId, rarity, null);
            if (power < 0.0) {
                sprout.setDamage((float) (-power) / 2.0f);
            }
            SoundPlayer.playSound("sound.forest.branchsnap", tilex, tiley, true, 2.0f);
            performer.getInventory().insertItem(sprout, true);

            final byte newData = (byte) (((age - 1) << 4) + (data & 0xF) & 0xFF);
            Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Tiles.decodeType(tile), newData);
            Players.getInstance().sendChangedTile(tilex, tiley, true, false);

            performer.getCommunicator().sendNormalServerMessage(String.format("You cut a sprout from the %s.", tileObj.isTree() ? "tree" : "bush"));
            Server.getInstance().broadCastAction(String.format("%s cuts a sprout off a %s.", performer.getName(), tileObj.isTree() ? "tree" : "bush"), performer, 5);
        } catch (FailedException | NoSuchTemplateException e) {
            BetterFarmMod.logException("Error making sprout", e);
            performer.getCommunicator().sendNormalServerMessage("You fail to pick the sprout. You realize something is wrong with the world.");
        }

        return afterActionCompleted(performer, source);
    }
}
