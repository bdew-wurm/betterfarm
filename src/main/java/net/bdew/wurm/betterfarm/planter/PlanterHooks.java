package net.bdew.wurm.betterfarm.planter;

import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.server.items.NoSuchTemplateException;
import net.bdew.wurm.betterfarm.AdvancedItemIdParser;
import net.bdew.wurm.betterfarm.BetterFarmMod;

import java.util.HashSet;
import java.util.Set;

public class PlanterHooks {
    public static Set<Integer> potables = new HashSet<>();

    public static boolean isPotable(int tpl) {
        return potables.contains(tpl);
    }

    public static void addPotables(String names) {
        AdvancedItemIdParser parser = new AdvancedItemIdParser();
        for (int id : parser.parseListSafe(names)) {
            try {
                ItemTemplate tpl = ItemTemplateFactory.getInstance().getTemplate(id);
                BetterFarmMod.logInfo("Allowing growing in planters: " + tpl.getName());
                potables.add(id);
                tpl.assignTypes(new short[]{ItemTypes.ITEM_TYPE_POTABLE});
            } catch (NoSuchTemplateException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
