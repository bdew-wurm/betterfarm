package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.Utils;

public class TrellisActionHarvest extends TrellisActionBase {

    @Override
    public boolean canActOn(Creature performer, Item source, Item target, boolean sendMsg) {
        TrellisType type = TrellisType.fromItem(target);
        if (type == null || type.productId <= 0) return false;

        if (!super.canActOn(performer, source, target, sendMsg)) return false;

        if (!target.isHarvestable()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's not ready to harvest.", target.getName().toLowerCase()));
            return false;
        }

        if (!target.isPlanted()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's not secured to the ground.", target.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayHarvestFruit();
    }

    @Override
    public float getActionTime(Creature performer, Item source, Item target) {
        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);
        return Actions.getQuickActionTime(performer, gardening, null, 0)
                * calcTrellisMaxHarvest(target, gardening.getRealKnowledge(), source);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, Item target) {
        if (!performer.getInventory().mayCreatureInsertItem()) {
            performer.getCommunicator().sendNormalServerMessage("You decide to stop as your inventory is full.");
            return false;
        }

        performer.getCommunicator().sendNormalServerMessage("You start to harvest the " + target.getName() + ".");
        Server.getInstance().broadCastAction(performer.getName() + " starts to harvest a trellis.", performer, 5);

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, Item target, byte rarity) {
        TrellisType type = TrellisType.fromItem(target);
        if (type == null || type.productId <= 0) return true;

        if (!target.isHarvestable()) {
            performer.getCommunicator().sendNormalServerMessage("You try to harvest but realize there is nothing on this trellis.");
            return true;
        }

        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);

        int templateId = type.productId;
        if (templateId == ItemList.grapesBlue && target.getTileY() <= Zones.worldTileSizeY / 2) {
            templateId = ItemList.grapesGreen;
        }

        ItemTemplate tpl;

        try {
            tpl = ItemTemplateFactory.getInstance().getTemplate(templateId);
        } catch (NoSuchTemplateException e) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You fail to harvest the %s. You realize something is wrong with the world.", target.getName().toLowerCase()));
            BetterFarmMod.logException("Error getting harvest template", e);
            return false;
        }

        int amount = calcTrellisMaxHarvest(target, gardening.getRealKnowledge(), source);

        if (!performer.canCarry(amount * tpl.getWeightGrams())) {
            performer.getCommunicator().sendNormalServerMessage("You would not be able to carry the harvest. You need to drop some things first.");
            return false;
        }

        double power = 0;
        for (int i = 0; i < amount; i++)
            power += gardening.skillCheck(gardening.getKnowledge(0.0) - 5.0, source, 0.0, false, 10);
        power = power / amount;
        float ql = (float) (gardening.getRealKnowledge() + (100 - gardening.getRealKnowledge()) * (power / 500));
        float modifier = 1.0f;
        if (source.getSpellEffects() != null) {
            modifier = source.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }

        ql = Math.max(1, Math.min(100.0f, (ql + source.getRarity()) * modifier));

        if (rarity > 0) {
            try {
                performer.playPersonalSound("sound.fx.drumroll");
                Item harvested = ItemFactory.createItem(templateId, ql, rarity, null);
                performer.getInventory().insertItem(harvested);
                amount -= 1;
            } catch (FailedException | NoSuchTemplateException e) {
                BetterFarmMod.logException("Error creating rare harvest", e);
            }
        }

        if (amount > 0)
            Utils.addStackedItems(performer.getInventory(), templateId, ql, amount, tpl.getName());

        performer.getCommunicator().sendNormalServerMessage("You harvest some" + tpl.getPlural() + " from the " + target.getName() + ".");
        Server.getInstance().broadCastAction(performer.getName() + " harvests " + tpl.getPlural() + " from a trellis.", performer, 5);

        SoundPlayer.playSound("sound.forest.branchsnap", target.getTileX(), target.getTileY(), true, 3.0f);
        target.setLastMaintained(WurmCalendar.currentTime);
        target.setHarvestable(false);

        return afterActionCompleted(performer, source);
    }

    private static int calcTrellisMaxHarvest(Item trellis, double currentSkill, Item tool) {
        int bonus = 0;
        if (tool.getSpellEffects() != null) {
            final float extraChance = tool.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_FARMYIELD) - 1.0f;
            if (extraChance > 0.0f && Server.rand.nextFloat() < extraChance) {
                ++bonus;
            }
        }
        return Math.min((int) (trellis.getCurrentQualityLevel() + 1.0f), (int) (currentSkill + 28.0) / 27 + bonus);
    }
}