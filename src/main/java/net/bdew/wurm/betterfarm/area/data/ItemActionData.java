package net.bdew.wurm.betterfarm.area.data;

import com.wurmonline.server.items.Item;
import net.bdew.wurm.betterfarm.api.IItemAction;

import java.util.Iterator;

public class ItemActionData {
    public final IItemAction handler;
    public final Iterator<Entry> targets;
    public Entry current = null;

    public static class Entry {
        public final Item item;
        public final float time;

        public Entry(Item item, float time) {
            this.item = item;
            this.time = time;
        }
    }

    public ItemActionData(IItemAction handler, Iterator<Entry> targets) {
        this.handler = handler;
        this.targets = targets;
    }
}
