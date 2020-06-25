package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.structures.Blocker;
import com.wurmonline.server.structures.Blocking;
import com.wurmonline.server.structures.BlockingResult;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.Utils;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;
import net.bdew.wurm.betterfarm.api.ItemAreaHandler;

public class TrellisHandler implements ItemAreaHandler {
    private final TrellisType type;

    public TrellisHandler(TrellisType type) {
        this.type = type;
    }

    @Override
    public boolean checkSkill(Creature performer, AreaActionType action, float needed) {
        return performer.getSkills().getSkillOrLearn(SkillList.GARDENING).getRealKnowledge() >= needed;
    }

    @Override
    public boolean canStartOn(Creature performer, AreaActionType action, Item source, Item target) {
        if (!performer.isPlayer()
                || target.getTemplateId() != type.trellisId
                || source == null || source.getTemplateId() != ItemList.sickle)
            return false;

        if (action != AreaActionType.HARVEST && action != AreaActionType.PICK_SPROUT && action != AreaActionType.PRUNE)
            return false;

        return target.getParentOrNull() == null;
    }

    @Override
    public boolean canActOn(Creature performer, AreaActionType action, Item source, Item target, boolean sendMsg) {
        if (!canStartOn(performer, action, source, target)) return false;

        VolaTile vt = Zones.getTileOrNull(target.getTilePos(), target.isOnSurface());
        if (vt == null) return false;

        final FoliageAge age = FoliageAge.getFoliageAge(target.getAuxData());

        if (action == AreaActionType.PRUNE && !age.isPrunable()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's not of the right age.", target.getName().toLowerCase()));
            return false;
        }

        if (action == AreaActionType.PICK_SPROUT
                && age != FoliageAge.MATURE_SPROUTING
                && age != FoliageAge.OLD_ONE_SPROUTING
                && age != FoliageAge.OLD_TWO_SPROUTING
                && age != FoliageAge.VERY_OLD_SPROUTING) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it has no sprouts to pick.", target.getName().toLowerCase()));
            return false;
        }

        if (action == AreaActionType.HARVEST) {
            if (type.productId <= 0) return false;

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
        }

        final BlockingResult blockers = Blocking.getBlockerBetween(performer, performer.getPosX(), performer.getPosY(), target.getPosX(), target.getPosY(), performer.getPositionZ(), target.getPosZ(), performer.isOnSurface(), target.isOnSurface(), false, Blocker.TYPE_ALL, -1L, performer.getBridgeId(), -10L, false);
        if (blockers != null && blockers.getFirstBlocker() != null) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip %s the %s since a %s blocks you.", action.verb, target.getName().toLowerCase(), blockers.getFirstBlocker().getName().toLowerCase()));
            return false;
        }

        Village village = vt.getVillage();
        if (village != null) {
            VillageRole role = village.getRoleFor(performer);
            if (role == null
                    || (action == AreaActionType.HARVEST && !role.mayHarvestFruit())
                    || (action == AreaActionType.PICK_SPROUT && !role.mayPickSprouts())
                    || (action == AreaActionType.PRUNE && !role.mayPrune())
            ) {
                if (sendMsg)
                    performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip %s the %s as it's against local laws.", action.verb, target.getName().toLowerCase()));
                return false;
            }
        }

        return true;
    }

    @Override
    public float getActionTime(Creature performer, AreaActionType action, Item source, Item target) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);
        if (action == AreaActionType.PRUNE)
            return Actions.getStandardActionTime(performer, forestry, source, sickle.getKnowledge(0.0));
        else if (action == AreaActionType.HARVEST)
            return Actions.getQuickActionTime(performer, gardening, null, 0) * calcTrellisMaxHarvest(target, gardening.getRealKnowledge(), source);
        else if (action == AreaActionType.PICK_SPROUT)
            return Actions.getStandardActionTime(performer, forestry, source, 0);
        return 0;
    }

    @Override
    public boolean actionStarted(Creature performer, AreaActionType action, Item source, Item target) {
        if (action != AreaActionType.PRUNE) {
            if (!performer.getInventory().mayCreatureInsertItem()) {
                performer.getCommunicator().sendNormalServerMessage("You decide to stop as your inventory is full.");
                return false;
            }
        }

        if (action == AreaActionType.PICK_SPROUT) {
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
        } else if (action == AreaActionType.PRUNE) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You start to prune the %s.", target.getName().toLowerCase()));
            Server.getInstance().broadCastAction(String.format("%s starts to prune the %s.", performer.getName(), target.getName().toLowerCase()), performer, 5);
        } else if (action == AreaActionType.HARVEST) {
            performer.getCommunicator().sendNormalServerMessage("You start to harvest the " + target.getName() + ".");
            Server.getInstance().broadCastAction(performer.getName() + " starts to harvest a trellis.", performer, 5);
        }

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, AreaActionType action, Item source, Item target) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);

        if (action == AreaActionType.PICK_SPROUT) {
            byte age = target.getLeftAuxData();
            if (age != 7 && age != 9 && age != 11 && age != 13) {
                performer.getCommunicator().sendNormalServerMessage("You try to pick a sprout but realize there are none on this trellis.");
                return true;
            }

            byte rarity = performer.getRarity();
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
        } else if (action == AreaActionType.PRUNE) {
            final FoliageAge age = FoliageAge.getFoliageAge(target.getAuxData());
            double bonus = Math.max(1.0, sickle.skillCheck(1.0, source, 0.0, false, 10));
            double power = forestry.skillCheck(forestry.getKnowledge(0.0) - 10.0, source, bonus, false, 10);
            SoundPlayer.playSound("sound.forest.branchsnap", target.getTileX(), target.getTileY(), true, 3.0f);
            if (power < 0.0) {
                performer.getCommunicator().sendNormalServerMessage(String.format("You make a lot of errors and failed to prune the %s.", target.getName().toLowerCase()));
                return true;
            }
            target.setLeftAuxData(age.getPrunedAge().getAgeId());
            target.updateName();
            performer.getCommunicator().sendNormalServerMessage(String.format("You prune the %s.", target.getName().toLowerCase()));
            Server.getInstance().broadCastAction(String.format("%s prunes the %s.", performer.getName(), target.getName().toLowerCase()), performer, 5);
        } else if (action == AreaActionType.HARVEST) {
            if (!target.isHarvestable()) {
                performer.getCommunicator().sendNormalServerMessage("You try to harvest but realize there is nothing on this trellis.");
                return true;
            }

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

            byte rarity = performer.getRarity();

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
        }

        if (source.setDamage(source.getDamage() + 0.003f * source.getDamageModifier())) {
            performer.getCommunicator().sendNormalServerMessage(String.format("Your %s broke!", source.getName()));
            return false;
        }

        return true;
    }

    public static void register() {
        for (TrellisType t : TrellisType.values())
            BetterFarmAPI.INSTANCE.addItemAreaHandler(t.trellisId, new TrellisHandler(t));
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
