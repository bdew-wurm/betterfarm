package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import net.bdew.wurm.betterfarm.ActionDef;
import net.bdew.wurm.betterfarm.BetterFarmMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AreaActions {
    static List<AreaActionPerformer> cultivateActions;
    static List<AreaActionPerformer> sowActions;
    static List<AreaActionPerformer> tendActions;
    static List<AreaActionPerformer> harvestActions;
    static List<AreaActionPerformer> replantActions;

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

        Function<List<AreaActionPerformer>, List<AreaActionPerformer>> filter =
                l -> l.stream().filter(
                        a -> a.canStartOnTile(performer, source, x, y, onSurface, tile)
                ).collect(Collectors.toList());

        addOrReplaceActions(actions, Actions.CULTIVATE, "Cultivate", filter.apply(cultivateActions));
        addOrReplaceActions(actions, Actions.SOW, "Sow", filter.apply(sowActions));
        addOrReplaceActions(actions, Actions.FARM, "Farm", filter.apply(tendActions));
        addOrReplaceActions(actions, Actions.HARVEST, "Harvest", filter.apply(harvestActions));
        addOrReplaceActions(actions, 0, "Harvest and replant", filter.apply(replantActions));

        return new SpecialRequestParam(result.getAvailableActions(), result.getHelpString(), origActions);
    }

    private static List<AreaActionPerformer> createActionList(Function<ActionDef, AreaActionPerformer> costructor, List<ActionDef> defs) {
        return defs.stream().map(costructor).collect(Collectors.toList());
    }

    public static void initActionLists() {
        cultivateActions = createActionList(i -> new CultivateActionPerformer(i.radius, i.level), BetterFarmMod.cultivateLevels);
        sowActions = createActionList(i -> new SowActionPerformer(i.radius, i.level), BetterFarmMod.sowLevels);
        tendActions = createActionList(i -> new TendActionPerformer(i.radius, i.level), BetterFarmMod.tendLevels);
        harvestActions = createActionList(i -> new HarvestActionPerformer(i.radius, false, i.level), BetterFarmMod.harvestLevels);
        replantActions = createActionList(i -> new HarvestActionPerformer(i.radius, true, i.level), BetterFarmMod.replantLevels);
    }

    private static void addOrReplaceActions(List<ActionEntry> list, int actionNumber, String name, List<AreaActionPerformer> available) {
        if (available.isEmpty()) return;
        if (actionNumber > 0) {
            int p = 0;
            for (ActionEntry actionEntry : list) {
                if (actionEntry.getNumber() == actionNumber) {
                    ActionEntry old = list.remove(p);
                    list.add(p++, new ActionEntry((short) (-1 - available.size()), old.getActionString(), ""));
                    list.add(p++, new ActionEntry(old.getNumber(), "Tile", old.getVerbString()));
                    for (AreaActionPerformer act : available) {
                        list.add(p++, new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
                    }
                    return;
                }
                p++;
            }
        }
        if (available.size() > 1) {
            list.add(new ActionEntry((short) -available.size(), name, ""));
            for (AreaActionPerformer act : available) {
                list.add(new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
            }
        } else {
            list.add(available.get(0).actionEntry);
        }
    }
}
