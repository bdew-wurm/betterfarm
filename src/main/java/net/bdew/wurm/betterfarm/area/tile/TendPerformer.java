package net.bdew.wurm.betterfarm.area.tile;

import com.wurmonline.mesh.FieldData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Crops;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.RuneUtilities;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import net.bdew.wurm.betterfarm.BetterFarmMod;
import net.bdew.wurm.betterfarm.area.TileAreaActionPerformer;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.lang.reflect.InvocationTargetException;

public class TendPerformer extends TileAreaActionPerformer {
    public TendPerformer(int radius, float skillLevel) {
        super(new ActionEntryBuilder((short) ModActions.getNextActionId(), String.format("Farm (%dx%d)", 2 * radius + 1, 2 * radius + 1), "farming", new int[]{
                1 /* ACTION_TYPE_NEED_FOOD */,
                4 /* ACTION_TYPE_FATIGUE */,
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                36 /* ACTION_TYPE_ALWAYS_USE_ACTIVE_ITEM */
        }).range(4).build(), radius, skillLevel, Actions.FARM);
    }

    @Override
    protected boolean canStartOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        if (!performer.isPlayer() || source == null || source.getTemplateId() != ItemList.rake) return false;
        if (performer.getSkills().getSkillOrLearn(SkillList.FARMING).getKnowledge() < skillLevel) return false;
        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        return t == Tiles.Tile.TILE_FIELD || t == Tiles.Tile.TILE_FIELD2;
    }

    @Override
    protected boolean canActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        if (!performer.isPlayer() || source == null || source.getTemplateId() != ItemList.rake || !onSurface)
            return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (t != Tiles.Tile.TILE_FIELD && t != Tiles.Tile.TILE_FIELD2) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since nothing is growing there.", t.getName().toLowerCase()));
            return false;
        }

        byte data = Tiles.decodeData(tile);

        if (Crops.decodeFieldState(data)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s since it already was tended.", FieldData.getTypeName(t, data).toLowerCase()));
            return false;
        }

        if (Crops.decodeFieldAge(data) >= 7) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage("You skip the weeds, they are far past saving.");
            return false;
        }


        return super.canActOnTile(performer, source, tilex, tiley, onSurface, tile, message);
    }


    @Override
    protected void doActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, float baseTime) {
        byte data = Tiles.decodeData(tile);
        byte type = Tiles.decodeType(tile);
        int crop = Crops.getCropNumber(type, data);
        double difficulty = 0;

        try {
            difficulty = ReflectionUtil.callPrivateMethod(null, ReflectionUtil.getMethod(Crops.class, "getDifficultyFor", new Class[]{int.class}), crop);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            BetterFarmMod.logException("Error getting crop difficulty", e);
        }

        Methods.sendSound(performer, "sound.work.farming.rake");
        source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier());
        performer.getStatus().modifyStamina(-1000.0F);
        byte rarity = performer.getRarity();
        if (rarity > 0)
            performer.playPersonalSound("sound.fx.drumroll");


        Skill farming = performer.getSkills().getSkillOrLearn(SkillList.FARMING);
        Skill rake = performer.getSkills().getSkillOrLearn(SkillList.RAKE);

        double bonus = rake.skillCheck(difficulty, source, 0.0, false, baseTime) / 10.0;
        double power = Math.max(0.0, farming.skillCheck(difficulty, source, bonus, false, baseTime));

        String name = FieldData.getTypeName(Tiles.getTile(type), data).toLowerCase();

        if (power <= 0.0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s is tended.", name));
        } else if (power < 25.0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s is now tended.", name));
        } else if (power < 50.0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s looks better after your tending.", name));
        } else if (power < 75.0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s is now groomed.", name));
        } else {
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s is now nicely groomed.", name));
        }

        Server.getInstance().broadCastAction(String.format("%s is pleased as the %s field is now in order.", performer.getName(), name), performer, 5);

        Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Crops.getTileType(crop), Crops.encodeFieldData(true, Crops.decodeFieldAge(data), crop));

        final int worldResource = Server.getWorldResource(tilex, tiley);
        int farmedCount = worldResource >>> 11;
        int farmedChance = worldResource & 0x7FF;
        if (farmedCount < 5) {
            ++farmedCount;
            farmedChance = (int) Math.min(farmedChance + power * 2.0 + rarity * 110 + source.getRarity() * 10, 2047.0);
        }
        if (source.getSpellEffects() != null) {
            final float extraChance = source.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_FARMYIELD) - 1F;
            if (extraChance > 0.0f && Server.rand.nextFloat() < extraChance) {
                performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " seems to have an extra effect on the field.");
                farmedChance = Math.min(farmedChance + 100, 2047);
                if (farmedCount < 5) {
                    ++farmedCount;
                }
            }
        }
        Server.setWorldResource(tilex, tiley, (farmedCount << 11) + farmedChance);
        if (performer.getPower() > 0) {
            performer.getCommunicator().sendNormalServerMessage("farmedCount is:" + farmedCount + " farmedChance is:" + farmedChance);
        }
        Players.getInstance().sendChangedTile(tilex, tiley, onSurface, false);
    }

    @Override
    protected void doAnimation(Creature performer) {
        performer.playAnimation("farm", false);
    }

    @Override
    protected float tileActionTime(Creature performer, Item source) {
        return Actions.getStandardActionTime(performer, performer.getSkills().getSkillOrLearn(SkillList.FARMING), source, 0.0D);
    }
}
