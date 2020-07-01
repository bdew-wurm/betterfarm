package net.bdew.wurm.betterfarm.planter;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.WurmCalendar;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Forage;
import com.wurmonline.server.behaviours.Herb;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class PlanterRackPickAction extends ContainerAction {
    public PlanterRackPickAction(float requiredLevel) {
        super(new ActionEntryBuilder((short) ModActions.getNextActionId(), "Pick All", "picking", new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                37 /* ACTION_TYPE_NEVER_USE_ACTIVE_ITEM */
        }).range(4).build(), SkillList.GARDENING, requiredLevel, "ripe planters");

    }

    @Override
    protected boolean canUseItem(Creature performer, Item item) {
        return true;
    }

    @Override
    protected boolean canActOnContainer(Creature performer, Item item) {
        return item.getTemplateId() == ItemList.planterRack;
    }

    @Override
    protected boolean canActOnItem(Creature performer, Item item) {
        if (item.getTemplateId() != ItemList.planterPotteryFull || !Methods.isActionAllowed(performer, Actions.PICK))
            return false;
        ItemTemplate temp = item.getRealTemplate();
        int age = item.getAuxData() & 127;
        boolean pickable = (item.getAuxData() & 128) != 0;
        return temp != null && pickable && age > 5 && age < 95;
    }

    private static float getDifficulty(int templateId, int knowledge) {
        float h = Herb.getDifficulty(templateId, knowledge);
        if (h > 0.0F) {
            return h;
        } else {
            float f = Forage.getDifficulty(templateId, knowledge);
            return f > 0.0F ? f : 0.0F;
        }
    }

    @Override
    protected boolean shouldAbortAction(Creature performer, Item source, Item target) {
        return false;
    }

    @Override
    protected boolean doActOnItem(Creature performer, Item source, Item item, byte rarity) {
        ItemTemplate growing = item.getRealTemplate();
        if (!performer.getInventory().mayCreatureInsertItem()) {
            performer.getCommunicator().sendNormalServerMessage("Your inventory is full. You would have no space to put whatever you pick.");
            return false;
        }

        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);

        if (rarity != 0)
            performer.playPersonalSound("sound.fx.drumroll");

        int age = item.getAuxData() & 127;
        int knowledge = (int) gardening.getKnowledge(0.0D);
        float diff = getDifficulty(item.getRealTemplateId(), knowledge);
        double power = gardening.skillCheck(diff, 0.0D, false, 10f);

        try {
            float ql = Herb.getQL(power, knowledge);
            Item newItem = ItemFactory.createItem(item.getRealTemplateId(), Math.max(ql, 1.0F), (byte) 0, rarity, null);
            if (ql < 0.0F) {
                newItem.setDamage(-ql / 2.0F);
            } else {
                newItem.setIsFresh(true);
            }
            Item inventory = performer.getInventory();
            inventory.insertItem(newItem);
        } catch (FailedException | NoSuchTemplateException e) {
            BetterFarmMod.logException(String.format("Error picking herb from %d", item.getTemplateId()), e);
        }

        item.setLastMaintained(WurmCalendar.currentTime);
        if (power < -50.0D) {
            performer.getCommunicator().sendNormalServerMessage("You broke off more than needed and damaged the plant, but still managed to get " + growing.getNameWithGenus() + ".");
            item.setAuxData((byte) (age + 1));
        } else if (power > 0.0D) {
            performer.getCommunicator().sendNormalServerMessage("You successfully picked " + growing.getNameWithGenus() + ", it now looks healthier.");
            item.setAuxData((byte) (age + 1));
        } else {
            performer.getCommunicator().sendNormalServerMessage("You successfully picked " + growing.getNameWithGenus() + ".");
            item.setAuxData((byte) (age + 1));
        }

        return true;
    }

    @Override
    protected float baseActionTime(Creature performer, Item source) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.GARDENING), null, 0.0D) / 5f;
    }
}
