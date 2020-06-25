package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Constants;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.structures.Blocker;
import com.wurmonline.server.structures.Blocking;
import com.wurmonline.server.structures.BlockingResult;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import net.bdew.wurm.betterfarm.AbortAction;
import org.gotti.wurmunlimited.modsupport.actions.ActionPropagation;

public abstract class TileAreaActionPerformer extends BaseAreaActionPerformer {
    protected final short checkAction;

    public TileAreaActionPerformer(ActionEntry actionEntry, int radius, float skillLevel, short checkAction) {
        super(actionEntry, radius, skillLevel);
        this.checkAction = checkAction;
    }

    protected abstract boolean canStartOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile);

    protected boolean canActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, boolean message) {
        Tiles.Tile t = Tiles.getTile(Tiles.decodeType(tile));

        if (Zones.protectedTiles[tilex][tiley]) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("Some unnatural force stops you from %s the %s, you decide to skip it.", actionEntry.getVerbString(), t.getName().toLowerCase()));
            return false;
        }

        VolaTile vt = Zones.getOrCreateTile(tilex, tiley, onSurface);
        Village village = vt.getVillage();
        if (village != null && !village.isActionAllowed(checkAction, performer, false, tile, 0)) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("The local laws forbid you from %s the %s, you decide to skip it.", actionEntry.getVerbString(), t.getName().toLowerCase()));
            return false;
        }

        final BlockingResult blockers = Blocking.getBlockerBetween(performer, performer.getPosX(), performer.getPosY(), (tilex << 2) + 2, (tiley << 2) + 2, performer.getPositionZ(), Tiles.decodeHeightAsFloat(tile), onSurface, onSurface, false, Blocker.TYPE_ALL, -1L, performer.getBridgeId(), -10L, false);
        if (blockers != null && blockers.getFirstBlocker() != null) {
            if (message)
                performer.getCommunicator().sendNormalServerMessage(String.format("You decide to skip %s the %s since a %s blocks you.", actionEntry.getVerbString(), t.getName().toLowerCase(), blockers.getFirstBlocker().getName().toLowerCase()));
            return false;
        }

        return true;
    }

    protected abstract void doActOnTile(Creature performer, Item source, int tilex, int tiley, boolean onSurface, int tile, float baseTime) throws AbortAction;

    protected abstract void doAnimation(Creature performer);

    protected abstract float tileActionTime(Creature performer, Item source);

    @Override
    public boolean action(Action action, Creature performer, int tilex, int tiley, boolean onSurface, int tile, short num, float counter) {
        return action(action, performer, null, tilex, tiley, onSurface, 0, tile, num, counter);
    }

    private int xByTileNum(int num, int startX) {
        return startX + (num % (2 * radius + 1) - radius);
    }

    private int yByTileNum(int num, int startY) {
        return startY + (num / (2 * radius + 1) - radius);
    }

    private void updateNextTick(Action action, Creature performer, Item source, int startX, int startY, boolean onSurface) {
        int num = action.getTickCount();
        int x = xByTileNum(num, startX);
        int y = yByTileNum(num, startY);
        MeshIO mesh = (onSurface ? Server.surfaceMesh : Server.caveMesh);
        int tile = mesh.getTile(x, y);
        if (canActOnTile(performer, source, x, y, onSurface, tile, false)) {
            action.incNextTick(action.getData() * 0.0001f);
            doAnimation(performer);
        } else {
            action.incNextTick(0.1f);
        }
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short num, float counter) {
        if ((tilex - radius < 0) || (tilex + radius > 1 << Constants.meshSize) || (tiley - radius < 0) || (tiley + radius > 1 << Constants.meshSize)) {
            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
        }

        int totalTiles = (2 * radius + 1) * (2 * radius + 1);
        MeshIO mesh = (onSurface ? Server.surfaceMesh : Server.caveMesh);

        try {
            if (counter == 1f) {
                float baseTime = tileActionTime(performer, source);

                action.setData((long) (baseTime * 1000));

                if (!canStartOnTile(performer, source, tilex, tiley, onSurface, tile))
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);

                float totalTime = 0;
                for (int x = tilex - radius; x <= tilex + radius; x++) {
                    for (int y = tiley - radius; y <= tiley + radius; y++) {
                        int t = mesh.getTile(x, y);
                        if (canActOnTile(performer, source, x, y, onSurface, t, false)) {
                            totalTime += baseTime;
                        } else {
                            totalTime += 1;
                        }
                    }
                }

                if (totalTime == 0) {
                    performer.getCommunicator().sendNormalServerMessage(String.format("You stop %s after failing to find any good tiles.", actionEntry.getVerbString()));
                    return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
                }

                totalTime = (float) Math.ceil(totalTime);

                action.setTimeLeft((int) totalTime);
                performer.sendActionControl(actionEntry.getVerbString(), true, (int) totalTime);
                action.setNextTick(1);
                updateNextTick(action, performer, source, tilex, tiley, onSurface);
                performer.getCommunicator().sendNormalServerMessage(String.format("You start %s.", actionEntry.getVerbString()));
            } else {
                if (counter >= action.getNextTick()) {
                    int tickNum = action.getTickCount();
                    int x = xByTileNum(tickNum, tilex);
                    int y = yByTileNum(tickNum, tiley);
                    int t = mesh.getTile(x, y);
                    if (canActOnTile(performer, source, x, y, onSurface, t, true))
                        doActOnTile(performer, source, x, y, onSurface, t, action.getData() * 0.001f);
                    action.incTickCount();
                    if (action.getTickCount() >= totalTiles) {
                        performer.getCommunicator().sendNormalServerMessage(String.format("You finish %s.", actionEntry.getVerbString()));
                        return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
                    } else {
                        updateNextTick(action, performer, source, tilex, tiley, onSurface);
                    }
                }
            }
        } catch (AbortAction ignored) {
            return propagate(action, ActionPropagation.FINISH_ACTION, ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION, ActionPropagation.NO_SERVER_PROPAGATION);
        }

        return propagate(action, ActionPropagation.CONTINUE_ACTION);
    }
}
