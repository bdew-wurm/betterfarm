package net.bdew.wurm.betterfarm.fields;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Crops;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.CropTilePoller;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

public class FieldActionSow extends FieldActionBase {
    private static final Set<Integer> normalSeeds = new HashSet<>();
    private static final Set<Integer> waterSeeds = new HashSet<>();

    static {
        normalSeeds.add(ItemList.barley);
        normalSeeds.add(ItemList.wheat);
        normalSeeds.add(ItemList.rye);
        normalSeeds.add(ItemList.oat);
        normalSeeds.add(ItemList.corn);
        normalSeeds.add(ItemList.pumpkinSeed);
        normalSeeds.add(ItemList.potato);
        normalSeeds.add(ItemList.cottonSeed);
        normalSeeds.add(ItemList.wempSeed);
        normalSeeds.add(ItemList.garlic);
        normalSeeds.add(ItemList.onion);
        waterSeeds.add(ItemList.reedSeed);
        waterSeeds.add(ItemList.rice);
        normalSeeds.add(ItemList.strawberrySeed);
        normalSeeds.add(ItemList.carrotSeeds);
        normalSeeds.add(ItemList.cabbageSeeds);
        normalSeeds.add(ItemList.tomatoSeeds);
        normalSeeds.add(ItemList.sugarBeetSeeds);
        normalSeeds.add(ItemList.lettuceSeeds);
        normalSeeds.add(ItemList.pea);
        normalSeeds.add(ItemList.cucumberSeeds);
    }

    private Item findSeed(Item container, boolean water) {
        for (Item item : container.getAllItems(true)) {
            if (item.isSeed()) {
                if (item.getWeightGrams() < item.getTemplate().getWeightGrams() || item.getAuxData() != 0 || item.getRarity() > 0)
                    continue;
                if ((!water && normalSeeds.contains(item.getTemplateId())) || (water && waterSeeds.contains(item.getTemplateId())))
                    return item;
            } else if (item.isHollow()) {
                Item found = findSeed(item, water);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.maySowFields();
    }

    @Override
    public boolean checkSkill(Creature performer, float needed) {
        return performer.getSkills().getSkillOrLearn(SkillList.FARMING).getRealKnowledge() >= needed;
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        if (!performer.isPlayer() || source == null || !source.isHollow() || !onSurface) return false;
        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        return t == Tiles.Tile.TILE_DIRT;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, message)) return false;

        if (source == null || !source.isHollow()) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (t == Tiles.Tile.TILE_FIELD || t == Tiles.Tile.TILE_FIELD2) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since something already grows there.", t.getName().toLowerCase()));
            return false;
        }

        if (t != Tiles.Tile.TILE_DIRT) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since nothing can be sown there.", t.getName().toLowerCase()));
            return false;
        }

        final VolaTile vtile = Zones.getTileOrNull(tilex, tiley, onSurface);
        if (vtile != null && vtile.getStructure() != null) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it's inside a structure.", t.getName().toLowerCase()));
            return false;
        }

        if (!Terraforming.isFlat(tilex, tiley, onSurface, 4)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it's not flat enough.", t.getName().toLowerCase()));
            return false;
        }

        final boolean isUnderWater = Terraforming.isCornerUnderWater(tilex, tiley, onSurface);

        if (isUnderWater && !Terraforming.isAllCornersInsideHeightRange(tilex, tiley, onSurface, (short) (-1), (short) (-4))) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it's too wet.", t.getName().toLowerCase()));
            return false;
        }


        Item seed = findSeed(source, isUnderWater);

        if (seed == null) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since you don't have any matching seed in your %s.", t.getName().toLowerCase(), source.getName()));
            return false;
        }

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        final boolean isUnderWater = Terraforming.isCornerUnderWater(tilex, tiley, onSurface);
        Item seed = findSeed(source, isUnderWater);
        if (seed == null) return false;

        performer.getStatus().modifyStamina(-2000.0f);

        int crop = 0;
        double diff = 0;

        try {
            crop = ReflectionUtil.callPrivateMethod(null, ReflectionUtil.getMethod(Crops.class, "getNumber", new Class[]{int.class}), seed.getTemplateId());
            diff = ReflectionUtil.callPrivateMethod(null, ReflectionUtil.getMethod(Crops.class, "getDifficultyFor", new Class[]{int.class}), crop);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            BetterFarmMod.logException("Error getting crop", e);
            return false;
        }

        byte rarity = performer.getRarity();
        if (rarity > 0) performer.playPersonalSound("sound.fx.drumroll");

        Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Crops.getTileType(crop), Crops.encodeFieldData(true, 0, crop));
        final Skill farming = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        farming.skillCheck(diff, 0.0, false, 1.0f);
        Players.getInstance().sendChangedTile(tilex, tiley, onSurface, false);
        final int resource = (int) (100.0 - farming.getKnowledge() + seed.getQualityLevel() + seed.getRarity() * 20 + rarity * 50);
        Server.setWorldResource(tilex, tiley, resource);
        CropTilePoller.addCropTile(tile, tilex, tiley, crop, onSurface);
        performer.getCommunicator().sendNormalServerMessage("You sow the " + Crops.getCropName(crop) + ".");
        Server.getInstance().broadCastAction(performer.getName() + " sows some seeds.", performer, 5);
        seed.setWeight(seed.getWeightGrams() - seed.getTemplate().getWeightGrams(), true);
        return true;
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        performer.playAnimation("drop", false);
        final boolean isUnderWater = Terraforming.isCornerUnderWater(tilex, tiley, onSurface);
        Item seed = findSeed(source, isUnderWater);
        if (seed == null) return false;
        return true;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return (float) ((130.0 - performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge()) / 10f / Servers.localServer.getActionTimer());
    }
}