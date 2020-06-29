package net.bdew.wurm.betterfarm.api;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

public interface ITileAction {
    boolean checkSkill(Creature performer, float needed);

    boolean canStartOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile);

    boolean canActOn(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean sendMsg);

    float getActionTime(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile);

    boolean actionStarted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile);

    boolean actionCompleted(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile);
}
