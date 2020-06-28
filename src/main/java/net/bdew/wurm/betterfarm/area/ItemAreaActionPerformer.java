package net.bdew.wurm.betterfarm.area;

import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.IItemAction;
import net.bdew.wurm.betterfarm.api.IItemAreaActions;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ActionPropagation;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.WeakHashMap;

public class ItemAreaActionPerformer extends BaseAreaActionPerformer {
    private final AreaActionType type;
    private final WeakHashMap<Action, ActionInfo> actionData = new WeakHashMap<>();

    private static class ActionInfo {
        private final IItemAction handler;
        private final Iterator<Item> targets;
        private Item current = null;

        public ActionInfo(IItemAction handler, Iterator<Item> targets) {
            this.handler = handler;
            this.targets = targets;
        }
    }

    public ItemAreaActionPerformer(AreaActionType type, float skillLevel, int radius) {
        super(new ActionEntryBuilder((short) ModActions.getNextActionId(), String.format("%s (%dx%d)", type.name, 2 * radius + 1, 2 * radius + 1), type.verb, new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                35 /* ACTION_TYPE_MAYBE_USE_ACTIVE_ITEM */
        }).range(4).build(), radius, skillLevel);
        this.type = type;
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        ActionInfo data = actionData.get(action);

        if (data == null) {
            IItemAreaActions actions = BetterFarmMod.apiHandler.findHandler(target);
            if (actions == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            IItemAction handler = actions.getActions().get(type);
            if (handler == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            if (!handler.checkSkill(performer, skillLevel) || !handler.canStartOn(performer, source, target))
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);

            LinkedList<Item> items = new LinkedList<>();
            int tilex = target.getTileX();
            int tiley = target.getTileY();

            float totalTime = 0;

            for (int x = tilex - radius; x <= tilex + radius; x++) {
                for (int y = tiley - radius; y <= tiley + radius; y++) {
                    VolaTile tile = Zones.getTileOrNull(x, y, target.isOnSurface());
                    if (tile == null) continue;
                    for (Item item : tile.getItems()) {
                        if (handler.canActOn(performer, source, item, true)) {
                            items.add(item);
                            totalTime += handler.getActionTime(performer, source, item);
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                performer.getCommunicator().sendNormalServerMessage(String.format("You stop %s as you can't find any valid targets", type.verb));
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
            }

            action.setTimeLeft((int) totalTime);
            performer.sendActionControl(type.verb, true, (int) totalTime);
            action.setNextTick(1);

            this.actionData.put(action, data = new ActionInfo(handler, items.iterator()));
        }

        if (counter >= action.getNextTick()) {
            if (data.current != null) {
                if (!data.handler.actionCompleted(performer, source, data.current)) {
                    actionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
            }
            if (!data.targets.hasNext()) {
                actionData.remove(action);
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
            }
            Item item = data.current = data.targets.next();
            if (data.handler.canActOn(performer, source, item, true)) {
                if (data.handler.actionStarted(performer, source, item)) {
                    action.incNextTick(data.handler.getActionTime(performer, source, item) / 10f);
                } else {
                    actionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
            }
        }

        return propagate(action, ActionPropagation.CONTINUE_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
    }
}
