package net.bdew.wurm.betterfarm.fields;

import com.wurmonline.mesh.FieldData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Crops;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.CropTilePoller;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;

public class FieldActionHarvest extends FieldActionBase {
    private final boolean replant;

    public FieldActionHarvest(boolean replant) {
        this.replant = replant;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayHarvestFields();
    }

    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        if (!performer.isPlayer() || !onSurface) return false;
        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        return t == Tiles.Tile.TILE_FIELD || t == Tiles.Tile.TILE_FIELD2;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, message)) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (t != Tiles.Tile.TILE_FIELD && t != Tiles.Tile.TILE_FIELD2) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since nothing is growing there.", t.getName().toLowerCase()));
            return false;
        }

        byte data = Tiles.decodeData(tile);
        int crop = FieldData.getType(t, data);

        if (Crops.decodeFieldAge(data) < 5) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it's not ripe yet.", FieldData.getTypeName(t, data).toLowerCase()));
            return false;
        }

        if (Crops.decodeFieldAge(data) >= 7) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage("You skip the weeds, they are far past harvesting.");
            return false;
        }

        if (!performer.getInventory().mayCreatureInsertItem()) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as your inventory is full.", FieldData.getTypeName(t, data).toLowerCase()));
            return false;
        }

        if (crop <= 3 && (source == null || source.getTemplateId() != ItemList.scythe)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as it needs a scythe to harvest.", FieldData.getTypeName(t, data).toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        int crop = Crops.getCropNumber(Tiles.decodeType(tile), Tiles.decodeData(tile));
        double difficulty = 0;
        int templateId;
        try {
            difficulty = ReflectionUtil.callPrivateMethod(null, ReflectionUtil.getMethod(Crops.class, "getDifficultyFor", new Class[]{int.class}), crop);
            templateId = ReflectionUtil.callPrivateMethod(null, ReflectionUtil.getMethod(Crops.class, "getProductTemplate", new Class[]{int.class}), crop);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            BetterFarmMod.logException("Error getting crop difficulty or template", e);
            return true;
        }

        if (crop > 3) source = null;

        Skill farming = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        Skill tool = (source == null) ? null : performer.getSkills().getSkillOrLearn(SkillList.SCYTHE);

        performer.getStatus().modifyStamina(-2000.0f);
        if (source != null) {
            Methods.sendSound(performer, "sound.work.farming.scythe");
        } else {
            Methods.sendSound(performer, "sound.work.farming.harvest");
        }

        byte rarity = performer.getRarity();
        if (rarity > 0)
            performer.playPersonalSound("sound.fx.drumroll");

        final double power = farming.skillCheck(difficulty, 0.0, false, 10);
        if (tool != null) tool.skillCheck(difficulty, source, 0.0, false, 10);

        byte itemRarity = (source == null) ? 0 : source.getRarity();

        float knowledge = (float) farming.getKnowledge(0.0);
        float ql = knowledge + (100.0f - knowledge) * ((float) power / 500.0f);

        final float realKnowledge = (float) farming.getRealKnowledge();
        final int worldResource = Server.getWorldResource(tilex, tiley);
        final int farmedCount = worldResource >>> 11;
        final int farmedChance = worldResource & 0x7FF;
        final short resource = (short) (farmedChance + rarity * 110 + itemRarity * 50 + Math.min(5, farmedCount) * 50);

        final float div = 100.0f - realKnowledge / 15.0f;
        final short bonusYield = (short) (resource / div / 1.5f);
        final float baseYield = realKnowledge / 15.0f;
        int quantity = (int) (baseYield + bonusYield + BetterFarmMod.extraHarvest);

        if (source != null && source.getSpellEffects() != null) {
            ql *= source.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }

        if (quantity == 0)
            quantity = 1;

        if (quantity == 1 && farmedCount > 0)
            ++quantity;

        if (quantity == 2 && farmedCount >= 4)
            ++quantity;

        final String name = Crops.getCropName(crop);

        ql = Math.max(Math.min(ql, 100.0f), 1.0f);

        try {
            int enc = ReflectionUtil.getPrivateField(performer, ReflectionUtil.getField(Creature.class, "encumbered"));
            if (performer.getCarriedWeight() + quantity * ItemTemplateFactory.getInstance().getTemplate(templateId).getWeightGrams() > enc) {
                performer.getCommunicator().sendNormalServerMessage("You stop harvesting as carrying more produce would make you encumbered.");
                return false;
            }
        } catch (IllegalAccessException | NoSuchFieldException | NoSuchTemplateException e) {
            BetterFarmMod.logException("Error checking encumbered", e);
        }

        if (replant) {
            if (quantity <= 1) {
                quantity = 0;
                performer.getCommunicator().sendNormalServerMessage("You use up all the yield of " + name + " to replant the field.");
            } else {
                quantity -= 1;
                performer.getCommunicator().sendNormalServerMessage("You managed to get a yield of " + quantity + " " + name + " after using some to replant.");
            }
            Server.getInstance().broadCastAction(performer.getName() + " has harvested and replanted the field.", performer, 5);
        } else {
            performer.getCommunicator().sendNormalServerMessage("You managed to get a yield of " + quantity + " " + name + ".");
            Server.getInstance().broadCastAction(performer.getName() + " has harvested the field.", performer, 5);
        }

        if (quantity > 0)
            Utils.addStackedItems(performer.getInventory(), templateId, ql, quantity, name);

        if (replant) {
            Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Crops.getTileType(crop), Crops.encodeFieldData(true, 0, crop));
            farming.skillCheck(difficulty, 0.0, false, 1.0f);
            final int replantResource = (int) (100.0 - farming.getKnowledge() + ql + rarity * 50);
            Server.setWorldResource(tilex, tiley, replantResource);
            CropTilePoller.addCropTile(tile, tilex, tiley, crop, onSurface);
        } else {
            Server.setWorldResource(tilex, tiley, 0);
            Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Tiles.Tile.TILE_DIRT.id, (byte) 0);
        }

        performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(tilex, tiley, onSurface, false);

        if (source != null) {
            if (source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier())) {
                performer.getCommunicator().sendNormalServerMessage(String.format("Your %s broke!", source.getName().toLowerCase()));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        performer.playAnimation("farm", false);
        return true;
    }

    @Override
    public boolean checkSkill(Creature performer, float needed) {
        return performer.getSkills().getSkillOrLearn(SkillList.FARMING).getRealKnowledge() >= needed;
    }
    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.FARMING), source, 0.0D);
    }
}
