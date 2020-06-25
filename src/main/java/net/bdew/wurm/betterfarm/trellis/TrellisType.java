package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.Materials;

public enum TrellisType {
    IVY(ItemList.ivyTrellis, ItemList.ivySeedling, "seedling", 0, Materials.MATERIAL_WOOD_IVY),
    GRAPE(ItemList.grapeTrellis, ItemList.sprout, "sprout", ItemList.grapesBlue, Materials.MATERIAL_WOOD_GRAPE),
    ROSE(ItemList.roseTrellis, ItemList.sprout, "sprout", ItemList.flowerRose, Materials.MATERIAL_WOOD_ROSE),
    HOPS(ItemList.hopsTrellis, ItemList.hopsSeedling, "seedling", ItemList.hops, Materials.MATERIAL_UNDEFINED);

    public final int trellisId;
    public final int sproutId;
    public final String sproutName;
    public final int productId;
    public final byte material;

    TrellisType(int trellisId, int sproutId, String sproutName, int productId, byte material) {
        this.trellisId = trellisId;
        this.sproutId = sproutId;
        this.sproutName = sproutName;
        this.productId = productId;
        this.material = material;
    }

    public static TrellisType fromItem(Item item) {
        for (TrellisType t : values())
            if (t.trellisId == item.getTemplateId()) return t;
        return null;
    }
}
