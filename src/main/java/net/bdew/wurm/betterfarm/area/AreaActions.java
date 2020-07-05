package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import net.bdew.wurm.betterfarm.ActionDef;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.Utils;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.IItemAction;
import net.bdew.wurm.betterfarm.api.ITileAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AreaActions {
    static Map<AreaActionType, List<AreaActionPerformer>> actionPerformers;

    static class SpecialRequestParam extends BehaviourDispatcher.RequestParam {
        private final List<ActionEntry> originalActions, filteredActions;

        public SpecialRequestParam(List<ActionEntry> actions, String help, List<ActionEntry> originalActions) {
            super(actions, help);
            this.filteredActions = actions;
            this.originalActions = originalActions;
        }

        @Override
        public void filterForSelectBar() {
            filteredActions.clear();
            filteredActions.addAll(originalActions);
            super.filterForSelectBar();
        }
    }

    public static BehaviourDispatcher.RequestParam tileBehaviourHook(BehaviourDispatcher.RequestParam result, Creature performer, long target, boolean onSurface, Item source) {
        List<ActionEntry> actions = result.getAvailableActions();
        List<ActionEntry> origActions = new ArrayList<>(actions);

        int x = Tiles.decodeTileX(target);
        int y = Tiles.decodeTileY(target);
        int tileData = Server.surfaceMesh.getTile(x, y);
        byte tileType = Tiles.decodeType(tileData);

        Map<AreaActionType, ITileAction> handlers = BetterFarmMod.apiHandler.getTileActions(tileType);
        if (handlers == null) return result;

        handlers.forEach((type, act) -> {
            if (act.canStartOn(performer, source, x, y, onSurface, tileData)) {
                Utils.addOrReplaceActions(actions, type.baseAction, type.name,
                        actionPerformers.get(type).stream()
                                .filter(a -> act.checkSkill(performer, a.skillLevel))
                                .collect(Collectors.toList()),
                        "Tile", type.goesUnder);
            }
        });


        return new SpecialRequestParam(result.getAvailableActions(), result.getHelpString(), origActions);
    }

    public static BehaviourDispatcher.RequestParam itemBehaviourHook(BehaviourDispatcher.RequestParam result, Creature performer, long targetId, Item source) {
        try {
            final Item target = Items.getItem(targetId);

            Map<AreaActionType, IItemAction> handlers = BetterFarmMod.apiHandler.getItemActions(target);
            if (handlers == null) return result;

            List<ActionEntry> actions = result.getAvailableActions();
            List<ActionEntry> origActions = new ArrayList<>(actions);

            handlers.forEach((type, act) -> {
                if (act.canStartOn(performer, source, target)) {
                    Utils.addOrReplaceActions(actions, type.baseAction, type.name,
                            actionPerformers.get(type).stream()
                                    .filter(a -> act.checkSkill(performer, a.skillLevel))
                                    .collect(Collectors.toList()),
                            "Single", type.goesUnder);
                }
            });

            return new SpecialRequestParam(result.getAvailableActions(), result.getHelpString(), origActions);
        } catch (NoSuchItemException e) {
            return result;
        }
    }

    private static <T> List<T> createActionList(Function<ActionDef, T> cons, List<ActionDef> defs) {
        return defs.stream().map(cons).collect(Collectors.toList());
    }

    private static void initItemActions(AreaActionType type, List<ActionDef> defs) {
        actionPerformers.put(type, createActionList(i -> new AreaActionPerformer(type, i.level, i.radius), defs));
    }

    public static void initActionLists() {
        actionPerformers = new HashMap<>();
        initItemActions(AreaActionType.CULTIVATE, BetterFarmMod.cultivateLevels);
        initItemActions(AreaActionType.SOW, BetterFarmMod.sowLevels);
        initItemActions(AreaActionType.FARM, BetterFarmMod.tendLevels);
        initItemActions(AreaActionType.HARVEST, BetterFarmMod.harvestLevels);
        initItemActions(AreaActionType.HARVEST_AND_REPLANT, BetterFarmMod.replantLevels);
        initItemActions(AreaActionType.PICK_SPROUT, BetterFarmMod.pickLevels);
        initItemActions(AreaActionType.PRUNE, BetterFarmMod.pruneLevels);
    }
}
