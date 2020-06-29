package net.bdew.wurm.betterfarm.planter;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class PlanterRackPlantAction extends ContainerAction {
    public PlanterRackPlantAction(float requiredLevel) {
        super(new ActionEntryBuilder((short) ModActions.getNextActionId(), "Plant All", "planting", new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                36 /* ACTION_TYPE_ALWAYS_USE_ACTIVE_ITEM */
        }).range(4).build(), SkillList.GARDENING, requiredLevel, "empty planters");

    }

    @Override
    protected boolean canUseItem(Creature performer, Item item) {
        return item != null && item.isHollow();
    }

    @Override
    protected boolean canActOnContainer(Creature performer, Item item) {
        return item.getTemplateId() == ItemList.planterRack;
    }

    @Override
    protected boolean canActOnItem(Creature performer, Item item) {
        return item.getTemplateId() == ItemList.planterPottery && Methods.isActionAllowed(performer, Actions.PLANT);
    }

    private static Item findPlantable(Item container) {
        for (Item i : container.getItems()) {
            if ((i.isSpice() || i.isHerb() || PlanterHooks.potables.contains(i.getTemplateId())) && (i.isFresh() || i.isPStateNone()))
                return i;
        }
        return null;
    }

    @Override
    protected boolean shouldAbortAction(Creature performer, Item source, Item target) {
        if (findPlantable(source) == null) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You stop planting as there are no more things to plant in the %s.", source.getName()));
            return true;
        } else {
            return false;
        }
    }


    @Override
    protected boolean doActOnItem(Creature performer, Item source, Item item) {
        Item plantable = findPlantable(source);
        if (plantable == null) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You stop planting as there are no more things to plant in the %s.", source.getName()));
            return false;
        }

        float ql = (plantable.getQualityLevel() + item.getQualityLevel()) / 2f;
        float dmg = (plantable.getDamage() + item.getDamage()) / 2f;
        Skill gardening = performer.getSkills().getSkillOrLearn(SkillList.GARDENING);

        try {
            ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.planterPotteryFull);
            double power = gardening.skillCheck(template.getDifficulty() + dmg, ql, false, 10f);
            if (power <= 0.0D) {
                performer.getCommunicator().sendNormalServerMessage("Sadly, the fragile " + plantable.getName() + " do not survive despite your best efforts.", (byte) 3);
            } else {
                Item newPot = ItemFactory.createItem(1162, item.getQualityLevel(), item.getRarity(), performer.getName());
                newPot.setRealTemplate(plantable.getTemplate().getGrows());
                newPot.setLastOwnerId(item.getLastOwnerId());
                newPot.setDescription(item.getDescription());
                newPot.setDamage(item.getDamage());
                Item parent = item.getParent();
                parent.insertItem(newPot, true);
                Items.destroyItem(item.getWurmId());
                Items.destroyItem(plantable.getWurmId());
                performer.getCommunicator().sendNormalServerMessage("You plant the " + plantable.getName() + " in the pot.");
            }
        } catch (NoSuchTemplateException | NoSuchItemException | FailedException e) {
            BetterFarmMod.logException(String.format("Error planting herb %d into %d", plantable.getWurmId(), item.getWurmId()), e);
        }

        return true;
    }

    @Override
    protected float baseActionTime(Creature performer, Item source) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.GARDENING), null, 0.0D);
    }

}
