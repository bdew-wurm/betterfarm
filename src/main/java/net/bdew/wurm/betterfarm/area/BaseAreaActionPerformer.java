package net.bdew.wurm.betterfarm.area;

import com.wurmonline.server.behaviours.ActionEntry;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class BaseAreaActionPerformer implements ActionPerformer {
    public final int radius;
    public final ActionEntry actionEntry;
    public final float skillLevel;

    public BaseAreaActionPerformer(ActionEntry actionEntry, int radius, float skillLevel) {
        this.radius = radius;
        this.actionEntry = actionEntry;
        this.skillLevel = skillLevel;
        ModActions.registerAction(actionEntry);
        ModActions.registerActionPerformer(this);
    }

    @Override
    public short getActionId() {
        return actionEntry.getNumber();
    }
}
