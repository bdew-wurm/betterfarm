package net.bdew.wurm.betterfarm.planter;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.*;

import java.util.Collections;
import java.util.List;

public abstract class ContainerAction implements ModAction, ActionPerformer, BehaviourProvider {
    protected ActionEntry actionEntry;
    protected int skill;
    protected float requiredLevel;
    protected String targetsStr;

    public ContainerAction(ActionEntry actionEntry, int skill, float requiredLevel, String targetsStr) {
        this.actionEntry = actionEntry;
        this.skill = skill;
        this.requiredLevel = requiredLevel;
        this.targetsStr = targetsStr;
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId() {
        return actionEntry.getNumber();
    }

    protected abstract boolean canUseItem(Creature performer, Item item);

    protected abstract boolean canActOnContainer(Creature performer, Item item);

    protected abstract boolean canActOnItem(Creature performer, Item item);

    protected abstract boolean doActOnItem(Creature performer, Item source, Item item);

    protected abstract float baseActionTime(Creature performer, Item source);

    protected abstract boolean shouldAbortAction(Creature performer, Item source, Item target);

    public boolean canUse(Creature performer, Item source, Item target) {
        return performer.isPlayer() && performer.getSkills().getSkillOrLearn(skill).getKnowledge() >= requiredLevel
                && (!target.isLocked() || target.mayAccessHold(performer))
                && canActOnContainer(performer, target) && canUseItem(performer, source);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        return getBehavioursFor(performer, null, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target) {
        if (canUse(performer, source, target))
            return Collections.singletonList(actionEntry);
        else
            return null;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        if (!canUse(performer, source, target)) {
            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
        }

        if (counter == 1f) {
            if (shouldAbortAction(performer, source, target))
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);

            float baseTime = baseActionTime(performer, source);

            action.setData((long) (baseTime * 1000));

            float totalTime = 0;
            for (Item sub : target.getItems()) {
                if (canActOnItem(performer, sub))
                    totalTime += baseTime;
            }

            if (totalTime == 0) {
                performer.getCommunicator().sendNormalServerMessage(String.format("There are no %s in the %s.", targetsStr, target.getName()));
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
            }

            totalTime = (float) Math.ceil(totalTime);

            action.setTimeLeft((int) totalTime);
            performer.sendActionControl(actionEntry.getVerbString(), true, (int) totalTime);
            action.setNextTick(1 + baseTime / 10);
            performer.getCommunicator().sendNormalServerMessage(String.format("You start %s the %s in the %s.", actionEntry.getVerbString(), targetsStr, target.getName()));

        } else {
            if (counter >= action.getNextTick()) {
                for (Item sub : target.getItems()) {
                    if (canActOnItem(performer, sub)) {
                        if (!doActOnItem(performer, source, sub)) {
                            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
                        }
                        action.incNextTick(action.getData() * 0.0001f);
                        if (shouldAbortAction(performer, source, target))
                            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
                        else
                            return propagate(action, ActionPropagation.CONTINUE_ACTION);
                    }
                }

                performer.getCommunicator().sendNormalServerMessage(String.format("You finish %s.", actionEntry.getVerbString()));
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
            }
        }
        return propagate(action, ActionPropagation.CONTINUE_ACTION);
    }
}
