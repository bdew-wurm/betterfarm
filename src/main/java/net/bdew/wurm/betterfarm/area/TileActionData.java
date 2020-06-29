package net.bdew.wurm.betterfarm.area;

import com.wurmonline.mesh.MeshIO;
import net.bdew.wurm.betterfarm.api.ITileAction;

import java.util.Iterator;

public class TileActionData {
    public final ITileAction handler;
    public final MeshIO mesh;
    public final Iterator<Entry> targets;
    public Entry current = null;

    public static class Entry {
        public final int x, y;
        public final float time;

        public Entry(int x, int y, float time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }

    public TileActionData(ITileAction handler, MeshIO mesh, Iterator<Entry> targets) {
        this.handler = handler;
        this.mesh = mesh;
        this.targets = targets;
    }
}
