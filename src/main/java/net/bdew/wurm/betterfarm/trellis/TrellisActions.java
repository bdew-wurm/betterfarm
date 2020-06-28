package net.bdew.wurm.betterfarm.trellis;

import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;
import net.bdew.wurm.betterfarm.api.IItemAction;
import net.bdew.wurm.betterfarm.api.IItemAreaActions;

import java.util.HashMap;
import java.util.Map;

public class TrellisActions implements IItemAreaActions {
    private final Map<AreaActionType, IItemAction> actions;

    public TrellisActions(TrellisType type) {
        actions = new HashMap<>();
        actions.put(AreaActionType.PRUNE, new TrellisActionPrune(type));
        actions.put(AreaActionType.HARVEST, new TrellisActionHarvest(type));
        actions.put(AreaActionType.PICK_SPROUT, new TrellisActionPick(type));
    }

    @Override
    public Map<AreaActionType, IItemAction> getActions() {
        return actions;
    }

    public static void register() {
        for (TrellisType t : TrellisType.values())
            BetterFarmAPI.INSTANCE.addItemAreaHandler(t.trellisId, new TrellisActions(t));
    }
}
