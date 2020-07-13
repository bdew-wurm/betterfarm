package net.bdew.wurm.betterfarm.trees;

import com.wurmonline.mesh.Tiles;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;

public class TreeActions {
    public static void register() {
        TreeActionPick pick = new TreeActionPick();
        TreeActionHarvest harvest = new TreeActionHarvest();
        TreeActionPlant plant = new TreeActionPlant();
        TreeActionPrune prune = new TreeActionPrune();

        for (Tiles.Tile t : Tiles.Tile.values()) {
            if (t.isNormalTree() || t.isNormalBush() || (BetterFarmMod.allowInfectedTrees && (t.isMyceliumTree() || t.isMyceliumBush()))) {
                BetterFarmAPI.INSTANCE.addTileAreaHandler(t.id, AreaActionType.PICK_SPROUT, pick);
                BetterFarmAPI.INSTANCE.addTileAreaHandler(t.id, AreaActionType.HARVEST, harvest);
                BetterFarmAPI.INSTANCE.addTileAreaHandler(t.id, AreaActionType.PRUNE, prune);
            }
        }

        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_DIRT.id, AreaActionType.PLANT, plant);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_GRASS.id, AreaActionType.PLANT, plant);
    }
}
