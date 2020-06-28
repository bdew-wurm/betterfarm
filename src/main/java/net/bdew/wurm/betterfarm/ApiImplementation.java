package net.bdew.wurm.betterfarm;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import net.bdew.wurm.betterfarm.api.IBetterFarmAPI;
import net.bdew.wurm.betterfarm.api.IItemAreaActions;

import java.util.HashMap;

public class ApiImplementation implements IBetterFarmAPI {
    private final HashMap<Integer, IItemAreaActions> handlers = new HashMap<>();

    public IItemAreaActions findHandler(Item item) {
        return handlers.get(item.getTemplateId());
    }

    @Override
    public void addItemAreaHandler(int templateId, IItemAreaActions handler) {
        try {
            ItemTemplate tpl = ItemTemplateFactory.getInstance().getTemplate(templateId);
            BetterFarmMod.logInfo(String.format("Added handler for %s - %s", tpl.getName(), handler.getClass().getName()));
            handlers.put(templateId, handler);
        } catch (NoSuchTemplateException e) {
            BetterFarmMod.logWarning(String.format("Attempt to add handler for non-existing template %d - %s", templateId, handler.getClass().getName()));
        }
    }

    @Override
    public int apiVersion() {
        return 1;
    }
}
