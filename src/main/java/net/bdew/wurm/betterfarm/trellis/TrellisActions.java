package net.bdew.wurm.betterfarm.trellis;

import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;

public class TrellisActions {
    public static void register() {
        TrellisActionPrune prune = new TrellisActionPrune();
        TrellisActionHarvest harvest = new TrellisActionHarvest();
        TrellisActionPick pick = new TrellisActionPick();

        for (TrellisType t : TrellisType.values()) {
            BetterFarmAPI.INSTANCE.addItemAreaHandler(t.trellisId, AreaActionType.PRUNE, prune);
            BetterFarmAPI.INSTANCE.addItemAreaHandler(t.trellisId, AreaActionType.HARVEST, harvest);
            BetterFarmAPI.INSTANCE.addItemAreaHandler(t.trellisId, AreaActionType.PICK_SPROUT, pick);
        }
    }
}
