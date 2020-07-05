package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Constants;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.IItemAction;
import net.bdew.wurm.betterfarm.api.ITileAction;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ActionPropagation;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

public class AreaActionPerformer implements ActionPerformer {
    public final int radius;
    public final float skillLevel;
    public final ActionEntry actionEntry;
    private final AreaActionType type;
    private final WeakHashMap<Action, ItemActionData> itemActionData = new WeakHashMap<>();
    private final WeakHashMap<Action, TileActionData> tileActionData = new WeakHashMap<>();

    public AreaActionPerformer(AreaActionType type, float skillLevel, int radius) {
        actionEntry = new ActionEntryBuilder((short) ModActions.getNextActionId(), String.format("%s (%dx%d)", type.name, 2 * radius + 1, 2 * radius + 1), type.verb, new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                35 /* ACTION_TYPE_MAYBE_USE_ACTIVE_ITEM */
        }).range(4).build();

        this.radius = radius;
        this.skillLevel = skillLevel;
        this.type = type;

        ModActions.registerAction(actionEntry);
        ModActions.registerActionPerformer(this);

        BetterFarmMod.logDebug(String.format("Registered action %d - %s (%dx%d)", getActionId(), type.name, 2 * radius + 1, 2 * radius + 1));

        if (BetterFarmMod.allowMountedAreaActions) BetterFarmMod.allowWhenMountedIds.add(actionEntry.getNumber());
    }

    // === Items ===

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        return action(action, performer, null, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        ItemActionData data = itemActionData.get(action);

        if (data == null) {
            Map<AreaActionType, IItemAction> actions = BetterFarmMod.apiHandler.getItemActions(target);
            if (actions == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            IItemAction handler = actions.get(type);
            if (handler == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            if (!handler.checkSkill(performer, skillLevel) || !handler.canStartOn(performer, source, target))
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);

            LinkedList<ItemActionData.Entry> items = new LinkedList<>();
            int tilex = target.getTileX();
            int tiley = target.getTileY();

            float totalTime = 0;

            for (int x = tilex - radius; x <= tilex + radius; x++) {
                for (int y = tiley - radius; y <= tiley + radius; y++) {
                    VolaTile tile = Zones.getTileOrNull(x, y, target.isOnSurface());
                    if (tile == null) continue;
                    for (Item item : tile.getItems()) {
                        if (handler.canActOn(performer, source, item, true)) {
                            float time = handler.getActionTime(performer, source, item);
                            items.add(new ItemActionData.Entry(item, handler.getActionTime(performer, source, item)));
                            totalTime += time;
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

            this.itemActionData.put(action, data = new ItemActionData(handler, items.iterator()));
        }

        if (counter >= action.getNextTick()) {
            if (data.current != null) {
                if (!data.handler.actionCompleted(performer, source, data.current.item, action.getRarity())) {
                    itemActionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
                if (data.targets.hasNext()) action.setRarity(performer.getRarity());
            }
            if (!data.targets.hasNext()) {
                itemActionData.remove(action);
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
            }
            ItemActionData.Entry next = data.targets.next();
            if (data.handler.canActOn(performer, source, next.item, true)) {
                if (data.handler.actionStarted(performer, source, next.item)) {
                    data.current = next;
                    action.incNextTick(next.time / 10f);
                } else {
                    itemActionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
            } else {
                data.current = null;
            }
        }

        return propagate(action, ActionPropagation.CONTINUE_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
    }

    // === Tiles ===

    @Override
    public boolean action(Action action, Creature performer, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short num, float counter) {
        return action(action, performer, null, tilex, tiley, onSurface, heightOffset, tile, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short num, float counter) {
        if ((tilex - radius < 0) || (tilex + radius > 1 << Constants.meshSize) || (tiley - radius < 0) || (tiley + radius > 1 << Constants.meshSize)) {
            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
        }

        TileActionData data = tileActionData.get(action);

        if (data == null) {
            Map<AreaActionType, ITileAction> actions = BetterFarmMod.apiHandler.getTileActions(Tiles.decodeType(tile));
            if (actions == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            ITileAction handler = actions.get(type);
            if (handler == null)
                return propagate(action, ActionPropagation.SERVER_PROPAGATION, ActionPropagation.ACTION_PERFORMER_PROPAGATION);

            if (!handler.checkSkill(performer, skillLevel) || !handler.canStartOn(performer, source, tilex, tiley, onSurface, tile))
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);

            MeshIO mesh = (onSurface ? Server.surfaceMesh : Server.caveMesh);

            LinkedList<TileActionData.Entry> items = new LinkedList<>();
            float totalTime = 0;

            for (int x = tilex - radius; x <= tilex + radius; x++) {
                for (int y = tiley - radius; y <= tiley + radius; y++) {
                    int t = mesh.getTile(x, y);
                    if (handler.canActOn(performer, source, x, y, onSurface, t, true)) {
                        float time = handler.getActionTime(performer, source, x, y, onSurface, t);
                        items.add(new TileActionData.Entry(x, y, time));
                        totalTime += time;
                    }
                }
            }

            if (totalTime <= 0) {
                performer.getCommunicator().sendNormalServerMessage(String.format("You stop %s as you can't find any valid targets", type.verb));
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
            }

            action.setTimeLeft((int) totalTime);
            performer.sendActionControl(type.verb, true, (int) totalTime);
            action.setNextTick(1);

            this.tileActionData.put(action, data = new TileActionData(handler, mesh, items.iterator()));
        }

        if (counter >= action.getNextTick()) {
            if (data.current != null) {
                int t = data.mesh.getTile(data.current.x, data.current.y);
                if (!data.handler.actionCompleted(performer, source, data.current.x, data.current.y, onSurface, t, action.getRarity())) {
                    tileActionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
                if (data.targets.hasNext()) action.setRarity(performer.getRarity());
            }
            if (!data.targets.hasNext()) {
                tileActionData.remove(action);
                return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
            }
            TileActionData.Entry next = data.targets.next();
            int t = data.mesh.getTile(next.x, next.y);
            if (data.handler.canActOn(performer, source, next.x, next.y, onSurface, t, true)) {
                if (data.handler.actionStarted(performer, source, next.x, next.y, onSurface, t)) {
                    data.current = next;
                    action.incNextTick(next.time / 10f);
                } else {
                    tileActionData.remove(action);
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }
            } else {
                data.current = null;
            }
        }

        return propagate(action, ActionPropagation.CONTINUE_ACTION, ActionPropagation.NO_SERVER_PROPAGATION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
    }

    @Override
    public short getActionId() {
        return actionEntry.getNumber();
    }
}
