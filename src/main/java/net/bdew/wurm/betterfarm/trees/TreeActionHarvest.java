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
import net.bdew.wurm.betterfarm.Utils;
import net.bdew.wurm.betterfarm.api.ActionEntryOverride;

public class TreeActionHarvest extends TreeActionBase {
    static ActionEntryOverride override = new ActionEntryOverride(Actions.HARVEST, "Harvest", "harvesting", "Nature");

    @Override
    public ActionEntryOverride getOverride(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return override;
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return super.canStartOn(performer, source, tilex, tiley, onSurface, tile)
                && isTree(tile)
                && source != null
                && (source.getTemplateId() == ItemList.sickle || source.getTemplateId() == ItemList.bucketSmall);
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, sendMsg)) return false;

        final byte data = Tiles.decodeData(tile);
        final int age = FoliageAge.getAgeAsByte(data);
        Tiles.Tile tileOjb = Tiles.getTile(Tiles.decodeType(tile));

        if (age < FoliageAge.MATURE_ONE.getAgeId()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as it's too young to harvest.", tileOjb.getName().toLowerCase()));
            return false;
        }

        if (age >= FoliageAge.OVERAGED.getAgeId()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as it's too old to harvest.", tileOjb.getName().toLowerCase()));
            return false;
        }

        int harvestable = TreeUtils.getProduce(tile, tilex, tiley);

        if (harvestable < 0) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as nothing is growing on it.", tileOjb.getName().toLowerCase()));
            return false;
        }

        if (harvestable == ItemList.sapMaple) {
            if (source == null || source.getTemplateId() != ItemList.bucketSmall) {
                if (sendMsg)
                    performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as you need a bucket to harvest the sap.", tileOjb.getName().toLowerCase()));
                return false;
            }
        } else if (source == null || source.getTemplateId() != ItemList.sickle) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as you need a sickle to harvest the fruit.", tileOjb.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayHarvestFruit();
    }

    @Override
    int getPrimarySkill() {
        return SkillList.FORESTRY;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        return Actions.getQuickActionTime(performer, forestry, null, 0.0)
                * calcMaxHarvest(tile, forestry.getRealKnowledge(), source);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Tiles.Tile tileOjb = Tiles.getTile(Tiles.decodeType(tile));

        if (!performer.getInventory().mayCreatureInsertItem()) {
            performer.getCommunicator().sendNormalServerMessage("You decide to stop as your inventory is full.");
            return false;
        }

        int harvestable = TreeUtils.getProduce(tile, tilex, tiley);
        if (harvestable == ItemList.sapMaple) {
            ItemTemplate tpl;

            try {
                tpl = ItemTemplateFactory.getInstance().getTemplate(harvestable);
            } catch (NoSuchTemplateException e) {
                performer.getCommunicator().sendNormalServerMessage(String.format("You fail to harvest the %s. You realize something is wrong with the world.", tileOjb.getName().toLowerCase()));
                BetterFarmMod.logException("Error getting harvest template", e);
                return false;
            }

            Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
            int amount = calcMaxHarvest(tile, forestry.getRealKnowledge(), source);

            if (!TreeUtils.checkFillBucket(performer, source, harvestable, amount * tpl.getWeightGrams(), true))
                return false;
        }

        performer.getCommunicator().sendNormalServerMessage("You start to harvest the " + tileOjb.getName().toLowerCase() + ".");
        Server.getInstance().broadCastAction(String.format("%s starts to harvest a %s.", performer.getName(), tileOjb.isTree() ? "tree" : "bush"), performer, 5);

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, byte rarity) {
        Tiles.Tile tileOjb = Tiles.getTile(Tiles.decodeType(tile));
        int produce = TreeUtils.getProduce(tile, tilex, tiley);

        if (produce < 0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You try to harvest but realize there is nothing on the %s.", tileOjb.getName().toLowerCase()));
            return true;
        }

        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);

        ItemTemplate tpl;

        try {
            tpl = ItemTemplateFactory.getInstance().getTemplate(produce);
        } catch (NoSuchTemplateException e) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You fail to harvest the %s. You realize something is wrong with the world.", tileOjb.getName().toLowerCase()));
            BetterFarmMod.logException("Error getting harvest template", e);
            return false;
        }

        int amount = calcMaxHarvest(tile, forestry.getRealKnowledge(), source);

        if (!performer.canCarry(amount * tpl.getWeightGrams())) {
            performer.getCommunicator().sendNormalServerMessage("You would not be able to carry the harvest. You need to drop some things first.");
            return false;
        }

        if (produce == ItemList.sapMaple) {
            if (!TreeUtils.checkFillBucket(performer, source, produce, amount * tpl.getWeightGrams(), true))
                return false;
        }

        double power = 0;
        for (int i = 0; i < amount; i++) {
            double bonus = 0.0;
            if (source.getTemplateId() == ItemList.sickle) {
                Skill toolSkill = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);
                bonus = Math.max(1.0, toolSkill.skillCheck(1.0, source, 0.0, false, 10f));
            }
            power += forestry.skillCheck(forestry.getRealKnowledge() - 5.0, source, bonus, false, 10f);
        }
        power = power / amount;

        float ql = (float) (forestry.getRealKnowledge() + (100 - forestry.getRealKnowledge()) * (power / 500));
        float modifier = 1.0f;

        if (source.getSpellEffects() != null) {
            modifier = source.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }

        ql = Math.max(1, Math.min(100.0f, (ql + source.getRarity()) * modifier));

        performer.getStatus().modifyStamina(-1500 * amount);

        if (source.getTemplateId() == ItemList.bucketSmall) {
            if (rarity > 0) performer.playPersonalSound("sound.fx.drumroll");
            try {
                TreeUtils.fillBucket(performer, source, produce, amount * tpl.getWeightGrams(), ql, rarity);
            } catch (FailedException | NoSuchTemplateException e) {
                BetterFarmMod.logException("Error creating sap", e);
            }
        } else {
            if (rarity > 0) {
                try {
                    performer.playPersonalSound("sound.fx.drumroll");
                    Item harvested = ItemFactory.createItem(produce, ql, rarity, null);
                    performer.getInventory().insertItem(harvested);
                    amount -= 1;
                } catch (FailedException | NoSuchTemplateException e) {
                    BetterFarmMod.logException("Error creating rare harvest", e);
                }
            }

            if (amount > 0)
                Utils.addStackedItems(performer.getInventory(), produce, ql, amount, tpl.getName());

            SoundPlayer.playSound("sound.forest.branchsnap", tilex, tiley, true, 3.0f);
        }

        performer.getCommunicator().sendNormalServerMessage(String.format("You harvest some %s from the %s.", tpl.getPlural(), tileOjb.getName().toLowerCase()));
        Server.getInstance().broadCastAction(String.format("%s harvests %s from a %s.", performer.getName(), tpl.getPlural(), tileOjb.isTree() ? "tree" : "bush"), performer, 5);

        Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), tileOjb.id, (byte) (Tiles.decodeData(tile) & 0xF7));
        Players.getInstance().sendChangedTile(tilex, tiley, true, false);

        return afterActionCompleted(performer, source);
    }

    private static int calcMaxHarvest(final int tile, final double currentSkill, final Item tool) {
        final byte data = Tiles.decodeData(tile);
        final byte age = FoliageAge.getAgeAsByte(data);
        int maxByAge = 1;
        if (age >= FoliageAge.OLD_ONE.getAgeId()) {
            if (age < FoliageAge.OLD_TWO.getAgeId()) maxByAge = 2;
            else if (age < FoliageAge.VERY_OLD.getAgeId()) maxByAge = 3;
            else if (age < FoliageAge.OVERAGED.getAgeId()) maxByAge = 4;
        }
        int bonus = 0;
        if (tool.getSpellEffects() != null) {
            final float extraChance = tool.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_FARMYIELD) - 1.0f;
            if (extraChance > 0.0f && Server.rand.nextFloat() < extraChance) {
                ++bonus;
            }
        }
        return Math.min(maxByAge, (int) (currentSkill + 28.0) / 27 + bonus);
    }
}