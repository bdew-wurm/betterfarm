package net.bdew.wurm.betterfarm.trellis;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.structures.Blocker;
import com.wurmonline.server.structures.Blocking;
import com.wurmonline.server.structures.BlockingResult;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.api.IItemAction;

public abstract class TrellisActionBase implements IItemAction {
    final TrellisType type;

    public TrellisActionBase(TrellisType type) {
        this.type = type;
    }

    @Override
    public boolean checkSkill(Creature performer, float needed) {
        return performer.getSkills().getSkillOrLearn(SkillList.GARDENING).getRealKnowledge() >= needed;
    }

    abstract boolean checkRole(VillageRole role);

    @Override
    public boolean canStartOn(Creature performer, Item source, Item target) {
        if (!performer.isPlayer()
                || target.getTemplateId() != type.trellisId
                || source == null || source.getTemplateId() != ItemList.sickle)
            return false;

        return target.getParentOrNull() == null;
    }

    @Override
    public boolean canActOn(Creature performer, Item source, Item target, boolean sendMsg) {
        if (!canStartOn(performer, source, target)) return false;

        VolaTile vt = Zones.getTileOrNull(target.getTilePos(), target.isOnSurface());
        if (vt == null) return false;

        final BlockingResult blockers = Blocking.getBlockerBetween(performer, performer.getPosX(), performer.getPosY(), target.getPosX(), target.getPosY(), performer.getPositionZ(), target.getPosZ(), performer.isOnSurface(), target.isOnSurface(), false, Blocker.TYPE_ALL, -1L, performer.getBridgeId(), -10L, false);
        if (blockers != null && blockers.getFirstBlocker() != null) {
            if (sendMsg)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s since a %s blocks you.", target.getName().toLowerCase(), blockers.getFirstBlocker().getName().toLowerCase()));
            return false;
        }

        Village village = vt.getVillage();
        if (village != null) {
            VillageRole role = village.getRoleFor(performer);
            if (role == null || !checkRole(role)) {
                if (sendMsg)
                    performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip the %s as it's against local laws.", target.getName().toLowerCase()));
                return false;
            }
        }

        return true;
    }

    boolean afterActionCompleted(Creature performer, Item source) {
        if (source.setDamage(source.getDamage() + 0.003f * source.getDamageModifier())) {
            performer.getCommunicator().sendNormalServerMessage(String.format("Your %s broke!", source.getName()));
            return false;
        } else return true;
    }
}
