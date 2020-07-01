package net.bdew.wurm.betterfarm.api;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

public interface IItemAction {
    boolean checkSkill(Creature performer, float needed);

    boolean canStartOn(Creature performer, Item source, Item target);

    boolean canActOn(Creature performer, Item source, Item target, boolean sendMsg);

    float getActionTime(Creature performer, Item source, Item target);

    boolean actionStarted(Creature performer, Item source, Item target);

    boolean actionCompleted(Creature performer, Item source, Item target, byte rarity);
}
