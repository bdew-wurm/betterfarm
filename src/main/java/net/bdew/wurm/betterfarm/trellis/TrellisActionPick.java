package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;
import net.bdew.wurm.betterfarm.BetterFarmMod;

public class TrellisActionPick extends TrellisActionBase {
    @Override
    public boolean canActOn(Creature performer, Item source, Item target, boolean sendMsg) {
        if (!super.canActOn(performer, source, target, sendMsg)) return false;

        final FoliageAge age = FoliageAge.getFoliageAge(target.getAuxData());

        if (age != FoliageAge.MATURE_SPROUTING
                && age != FoliageAge.OLD_ONE_SPROUTING
                && age != FoliageAge.OLD_TWO_SPROUTING
                && age != FoliageAge.VERY_OLD_SPROUTING) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it has no sprouts to pick.", target.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayPickSprouts();
    }

    @Override
    public float getActionTime(Creature performer, Item source, Item target) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        return Actions.getStandardActionTime(performer, forestry, source, 0);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, Item target) {
        TrellisType type = TrellisType.fromItem(target);
        if (type == null) return true;

        if (!performer.getInventory().mayCreatureInsertItem()) {
            performer.getCommunicator().sendNormalServerMessage("You decide to stop as your inventory is full.");
            return false;
        }

        try {
            final int weight = ItemTemplateFactory.getInstance().getTemplate(type.sproutId).getWeightGrams();
            if (!performer.canCarry(weight)) {
                performer.getCommunicator().sendNormalServerMessage("You would not be able to carry the sprout. You need to drop some things first.");
                return false;
            }
        } catch (NoSuchTemplateException e) {
            BetterFarmMod.logException("Error getting sprout weight", e);
            return false;
        }

        performer.getCommunicator().sendNormalServerMessage("You start cutting a sprout from the trellis.");
        Server.getInstance().broadCastAction(String.format("%s starts to cut a sprout off a trellis.", performer.getName()), performer, 5);

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, Item target, byte rarity) {
        TrellisType type = TrellisType.fromItem(target);
        if (type == null || type.productId <= 0) return true;

        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);

        byte age = target.getLeftAuxData();
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
            final Item sprout = ItemFactory.createItem(type.sproutId, Math.max(1.0f, Math.min(100.0f, (float) power * modifier + source.getRarity())), type.material, rarity, null);
            if (power < 0.0) {
                sprout.setDamage((float) (-power) / 2.0f);
            }
            SoundPlayer.playSound("sound.forest.branchsnap", target.getTileX(), target.getTileY(), true, 2.0f);
            performer.getInventory().insertItem(sprout, true);
            target.setLeftAuxData(age - 1);
            target.updateName();
            performer.getCommunicator().sendNormalServerMessage(String.format("You cut a %s from the trellis.", type.sproutName));
            Server.getInstance().broadCastAction(String.format("%s cuts a %s off a trellis.", performer.getName(), type.sproutName), performer, 5);
        } catch (FailedException | NoSuchTemplateException e) {
            BetterFarmMod.logException("Error making sprout", e);
            performer.getCommunicator().sendNormalServerMessage(String.format("You fail to pick the %s. You realize something is wrong with the world.", type.sproutName));
        }

        return afterActionCompleted(performer, source);
    }
}
