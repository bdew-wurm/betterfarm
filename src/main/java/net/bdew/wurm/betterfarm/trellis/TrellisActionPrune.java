package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.villages.VillageRole;

public class TrellisActionPrune extends TrellisActionBase {
    @Override
    public boolean canActOn(Creature performer, Item source, Item target, boolean sendMsg) {
        if (!super.canActOn(performer, source, target, sendMsg)) return false;

        final FoliageAge age = FoliageAge.getFoliageAge(target.getAuxData());

        if (!age.isPrunable()) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's not of the right age.", target.getName().toLowerCase()));
            return false;
        }

        return true;
    }

    @Override
    boolean checkRole(VillageRole role) {
        return role.mayPrune();
    }

    @Override
    public float getActionTime(Creature performer, Item source, Item target) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);
        return Actions.getStandardActionTime(performer, forestry, source, sickle.getKnowledge(0.0));
    }

    @Override
    public boolean actionStarted(Creature performer, Item source, Item target) {
        performer.getCommunicator().sendNormalServerMessage(String.format("You start to prune the %s.", target.getName().toLowerCase()));
        Server.getInstance().broadCastAction(String.format("%s starts to prune the %s.", performer.getName(), target.getName().toLowerCase()), performer, 5);

        return true;
    }

    @Override
    public boolean actionCompleted(Creature performer, Item source, Item target) {
        Skill forestry = performer.getSkills().getSkillOrLearn(SkillList.FORESTRY);
        Skill sickle = performer.getSkills().getSkillOrLearn(SkillList.SICKLE);

        final FoliageAge age = FoliageAge.getFoliageAge(target.getAuxData());
        double bonus = Math.max(1.0, sickle.skillCheck(1.0, source, 0.0, false, 10));
        double power = forestry.skillCheck(forestry.getKnowledge(0.0) - 10.0, source, bonus, false, 10);
        SoundPlayer.playSound("sound.forest.branchsnap", target.getTileX(), target.getTileY(), true, 3.0f);
        if (power < 0.0) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You make a lot of errors and failed to prune the %s.", target.getName().toLowerCase()));
            return true;
        }
        target.setLeftAuxData(age.getPrunedAge().getAgeId());
        target.updateName();
        performer.getCommunicator().sendNormalServerMessage(String.format("You prune the %s.", target.getName().toLowerCase()));
        Server.getInstance().broadCastAction(String.format("%s prunes the %s.", performer.getName(), target.getName().toLowerCase()), performer, 5);

        return afterActionCompleted(performer, source);
    }
}