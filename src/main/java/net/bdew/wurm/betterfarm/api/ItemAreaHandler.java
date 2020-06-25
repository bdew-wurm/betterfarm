package net.bdew.wurm.betterfarm.api;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

public interface ItemAreaHandler {
    boolean checkSkill(Creature performer, AreaActionType action, float needed);

    boolean canStartOn(Creature performer, AreaActionType action, Item source, Item target);

    boolean canActOn(Creature performer, AreaActionType action, Item source, Item target, boolean sendMsg);

    float getActionTime(Creature performer, AreaActionType action, Item source, Item target);

    boolean actionStarted(Creature performer, AreaActionType action, Item source, Item target);

    boolean actionCompleted(Creature performer, AreaActionType action, Item source, Item target);
}
