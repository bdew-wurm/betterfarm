package net.bdew.wurm.betterfarm.fields;

import com.wurmonline.mesh.Tiles;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;

public class FieldActions {
    public static void register() {
        FieldActionSow sow = new FieldActionSow();
        FieldActionCultivate cultivate = new FieldActionCultivate();

        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_DIRT.id, AreaActionType.SOW, sow);

        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_DIRT.id, AreaActionType.CULTIVATE, cultivate);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_DIRT_PACKED.id, AreaActionType.CULTIVATE, cultivate);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_MOSS.id, AreaActionType.CULTIVATE, cultivate);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_GRASS.id, AreaActionType.CULTIVATE, cultivate);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_STEPPE.id, AreaActionType.CULTIVATE, cultivate);
        BetterFarmAPI.INSTANCE.addTileAreaHandler(Tiles.Tile.TILE_MYCELIUM.id, AreaActionType.CULTIVATE, cultivate);
    }
}
