package net.bdew.wurm.betterfarm.trees;

import com.wurmonline.mesh.BushData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.zones.Zones;

public class TreeUtils {
    public static int getBushProduce(final int tilex, final int tiley, final BushData.BushType type) {
        switch (type) {
            case LAVENDER:
                return ItemList.flowerLavender;
            case ROSE:
                return ItemList.flowerRose;
            case GRAPE:
                return tiley > Zones.worldTileSizeY / 2 ? ItemList.grapesBlue : ItemList.grapesGreen;
            case CAMELLIA:
                return ItemList.leavesCamellia;
            case OLEANDER:
                return ItemList.leavesOleander;
            case HAZELNUT:
                return ItemList.nutHazel;
            case RASPBERRY:
                return ItemList.raspberries;
            case BLUEBERRY:
                return ItemList.blueberry;
            case LINGONBERRY:
                return ItemList.lingonberry;
            default:
                return -10;
        }
    }

    public static int getTreeProduce(final int tilex, final int tiley, final TreeData.TreeType type) {
        switch (type) {
            case MAPLE:
                return ItemList.sapMaple;
            case APPLE:
                return ItemList.appleGreen;
            case LEMON:
                return ItemList.lemon;
            case OLIVE:
                return ItemList.olive;
            case CHERRY:
                return ItemList.cherries;
            case CHESTNUT:
                return ItemList.chestnut;
            case WALNUT:
                return ItemList.walnut;
            case PINE:
                return ItemList.pineNuts;
            case OAK:
                return ItemList.acorn;
            case ORANGE:
                return ItemList.orange;
            default:
                return -10;
        }
    }

    public static int getProduce(int encoded, int tilex, int tiley) {
        Tiles.Tile tile = Tiles.getTile(Tiles.decodeType(encoded));
        byte data = Tiles.decodeData(encoded);
        if (tile.isTree() && TreeData.hasFruit(data))
            return getTreeProduce(tilex, tiley, tile.getTreeType(data));
        else if (tile.isBush() && TreeData.hasFruit(data))
            return getBushProduce(tilex, tiley, tile.getBushType(data));
        else return -10;
    }

    public static boolean checkFillBucket(Creature performer, Item bucket, int fillTemplate, int fillAmount, boolean sendMessage) {
        if (fillAmount > performer.getCarryingCapacityLeft()) {
            if (sendMessage)
                performer.getCommunicator().sendNormalServerMessage("You stop harvesting as you wouldn't be able to carry all the liquid.");
            return false;
        }

        if (fillAmount > bucket.getFreeVolume()) {
            if (sendMessage)
                performer.getCommunicator().sendNormalServerMessage("You stop harvesting as your bucket is too full.");
            return false;
        }

        for (Item check : bucket.getItems()) {
            if (check.getTemplateId() != fillTemplate) {
                if (sendMessage)
                    performer.getCommunicator().sendNormalServerMessage("You stop harvesting as your bucket contains something else.");
                return false;
            }
        }

        return true;
    }

    public static void fillBucket(Creature performer, Item bucket, int fillTemplate, int fillAmount, float ql, byte rarity) throws NoSuchTemplateException, FailedException {
        Methods.sendSound(performer, "sound.liquid.fillcontainer.bucket");
        for (Item existing : bucket.getItems()) {
            if (existing.getTemplateId() == fillTemplate) {
                int sumWeight = existing.getWeightGrams() + fillAmount;
                float sumQl = (existing.getQualityLevel() * existing.getWeightGrams() / sumWeight) + (ql * fillAmount / sumWeight);
                existing.setWeight(sumWeight, true);
                existing.setQualityLevel(sumQl);
                if (existing.getRarity() > rarity)
                    existing.setRarity(rarity);
                return;
            }
        }
        final Item harvested = ItemFactory.createItem(fillTemplate, ql, rarity, null);
        harvested.setWeight(fillAmount, true);
        bucket.insertItem(harvested);
    }
}
