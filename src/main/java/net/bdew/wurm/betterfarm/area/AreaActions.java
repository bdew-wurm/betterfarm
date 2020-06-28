package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import net.bdew.wurm.betterfarm.ActionDef;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.Utils;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.IItemAreaActions;
import net.bdew.wurm.betterfarm.area.tile.CultivatePerformer;
import net.bdew.wurm.betterfarm.area.tile.HarvestPerformer;
import net.bdew.wurm.betterfarm.area.tile.SowPerformer;
import net.bdew.wurm.betterfarm.area.tile.TendPerformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AreaActions {
    static List<TileAreaActionPerformer> cultivateActions, sowActions, tendActions, harvestActions, replantActions;
    static Map<AreaActionType, List<ItemAreaActionPerformer>> itemActions;

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
        if (!onSurface) return result;

        List<ActionEntry> actions = result.getAvailableActions();
        List<ActionEntry> origActions = new ArrayList<>(actions);

        final int x = Tiles.decodeTileX(target);
        final int y = Tiles.decodeTileY(target);
        final int tile = Server.surfaceMesh.getTile(x, y);

        Function<List<TileAreaActionPerformer>, List<TileAreaActionPerformer>> filter =
                l -> l.stream().filter(
                        a -> a.canStartOnTile(performer, source, x, y, onSurface, tile)
                ).collect(Collectors.toList());

        Utils.addOrReplaceActions(actions, Actions.CULTIVATE, "Cultivate", filter.apply(cultivateActions), "Tile", null);
        Utils.addOrReplaceActions(actions, Actions.SOW, "Sow", filter.apply(sowActions), "Tile", null);
        Utils.addOrReplaceActions(actions, Actions.FARM, "Farm", filter.apply(tendActions), "Tile", null);
        Utils.addOrReplaceActions(actions, Actions.HARVEST, "Harvest", filter.apply(harvestActions), "Tile", null);
        Utils.addOrReplaceActions(actions, 0, "Harvest and replant", filter.apply(replantActions), "Tile", null);

        return new SpecialRequestParam(result.getAvailableActions(), result.getHelpString(), origActions);
    }

    public static BehaviourDispatcher.RequestParam itemBehaviourHook(BehaviourDispatcher.RequestParam result, Creature performer, long targetId, Item source) {
        try {
            final Item target = Items.getItem(targetId);

            IItemAreaActions handler = BetterFarmMod.apiHandler.findHandler(target);
            if (handler == null) return result;

            List<ActionEntry> actions = result.getAvailableActions();
            List<ActionEntry> origActions = new ArrayList<>(actions);

            handler.getActions().forEach((type, act) -> {
                if (act.canStartOn(performer, source, target)) {
                    Utils.addOrReplaceActions(actions, type.baseAction, type.name,
                            itemActions.get(type).stream()
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
        itemActions.put(type, createActionList(i -> new ItemAreaActionPerformer(type, i.level, i.radius), defs));
    }

    public static void initActionLists() {
        cultivateActions = createActionList(i -> new CultivatePerformer(i.radius, i.level), BetterFarmMod.cultivateLevels);
        sowActions = createActionList(i -> new SowPerformer(i.radius, i.level), BetterFarmMod.sowLevels);
        tendActions = createActionList(i -> new TendPerformer(i.radius, i.level), BetterFarmMod.tendLevels);
        harvestActions = createActionList(i -> new HarvestPerformer(i.radius, false, i.level), BetterFarmMod.harvestLevels);
        replantActions = createActionList(i -> new HarvestPerformer(i.radius, true, i.level), BetterFarmMod.replantLevels);
        itemActions = new HashMap<>();
        initItemActions(AreaActionType.CULTIVATE, BetterFarmMod.cultivateLevels);
        initItemActions(AreaActionType.SOW, BetterFarmMod.sowLevels);
        initItemActions(AreaActionType.FARM, BetterFarmMod.tendLevels);
        initItemActions(AreaActionType.HARVEST, BetterFarmMod.harvestLevels);
        initItemActions(AreaActionType.HARVEST_AND_REPLANT, BetterFarmMod.replantLevels);
        initItemActions(AreaActionType.PICK_SPROUT, BetterFarmMod.pickLevels);
        initItemActions(AreaActionType.PRUNE, BetterFarmMod.pruneLevels);
    }
}
