package net.bdew.wurm.betterfarm.trees;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;

public class TreeActionPrune extends TreeActionBase {
    @Override
    public boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        return super.canStartOn(performer, source, tilex, tiley, onSurface, tile)
                && isTree(tile)
                && source != null && source.getTemplateId() == ItemList.sickle;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg) {
        if (!super.canActOn(performer, source, tilex, tiley, onSurface, tile, sendMsg)) return false;

        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));
        byte data = Tiles.decodeData(tile);
        FoliageAge age = FoliageAge.getFoliageAge(data);

        if (!age.isPrunable()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You skip the %s as it's not at the right age.", t.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayPrune();
    }

    @Override
    int getPrimarySkill() {
        return SkillList.FORESTRY;
    }

    @Override
    public float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        return Actions.getStandardActionTime(performer, forestry, source, 0);
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile) {
        Tiles.Tile tileObj = Tiles.getTile(Tiles.decodeType(tile));

        performer.getCommunicator().sendNormalServerMessage(String.format("You start to prune the %s.", tileObj.getName().toLowerCase()));
        Server.getInstance().broadCastAction(String.format("%s starts to prune the %s.", performer.getName(), tileObj.getName().toLowerCase()), performer, 5);
        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, byte rarity) {
        Tiles.Tile tileObj = Tiles.getTile(Tiles.decodeType(tile));
        byte data = Tiles.decodeData(tile);
        FoliageAge age = FoliageAge.getFoliageAge(data);

        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);

        if (!age.isPrunable()) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You try to prune the %s but discover it's at the wrong age.", tileObj.getName().toLowerCase()));
        }

        double bonus = Math.max(1.0, sickle.skillCheck(1.0, source, 0.0, false, 10));
        forestry.skillCheck(forestry.getKnowledge(0.0) - 10.0, source, bonus, false, 10);

        SoundPlayer.playSound("sound.forest.branchsnap", tilex, tiley, true, 3.0f);

        final FoliageAge newage = age.getPrunedAge();
        final int newData = newage.encodeAsData() + (data & 0xF) & 0xFF;
        Server.setSurfaceTile(tilex, tiley, Tiles.decodeHeight(tile), Tiles.decodeType(tile), (byte) newData);
        Players.getInstance().sendChangedTile(tilex, tiley, true, false);
        performer.getCommunicator().sendNormalServerMessage("You prune the " + tileObj.getName().toLowerCase() + ".");
        Server.getInstance().broadCastAction(performer.getName() + " prunes the " + tileObj.getName().toLowerCase() + ".", performer, 5);

        return afterActionCompleted(performer, source);
    }
}
