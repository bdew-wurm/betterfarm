package net.bdew.wurm.betterfarm;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import net.bdew.wurm.betterfarm.api.AreaActionType;
import net.bdew.wurm.betterfarm.api.IBetterFarmAPI;
import net.bdew.wurm.betterfarm.api.IItemAction;
import net.bdew.wurm.betterfarm.api.ITileAction;

import java.util.HashMap;
import java.util.Map;

public class ApiImplementation implements IBetterFarmAPI {
    private final HashMap<Integer, HashMap<AreaActionType, IItemAction>> itemHandlers = new HashMap<>();
    private final HashMap<Byte, HashMap<AreaActionType, ITileAction>> tileHandlers = new HashMap<>();

    public Map<AreaActionType, IItemAction> getItemActions(Item item) {
        return itemHandlers.get(item.getTemplateId());
    }

    public Map<AreaActionType, ITileAction> getTileActions(byte tileType) {
        return tileHandlers.get(tileType);
    }

    @Override
    public void addItemAreaHandler(int templateId, AreaActionType action, IItemAction handler) {
        try {
            ItemTemplate tpl = ItemTemplateFactory.getInstance().getTemplate(templateId);
            BetterFarmMod.logInfo(String.format("Added item handler for %s (%s) - %s", tpl.getName(), action, handler.getClass().getName()));
            itemHandlers.computeIfAbsent(templateId, (t) -> new HashMap<>()).put(action, handler);
        } catch (NoSuchTemplateException e) {
            BetterFarmMod.logWarning(String.format("Attempt to add handler for non-existing template %d - %s", templateId, handler.getClass().getName()));
        }
    }

    @Override
    public void addTileAreaHandler(byte tileType, AreaActionType action, ITileAction handler) {
        Tiles.Tile type = Tiles.getTile(tileType);
        if (type == null) {
            BetterFarmMod.logWarning(String.format("Attempt to add handler for non-existing tile type %d - %s", tileType, handler.getClass().getName()));
        } else {
            BetterFarmMod.logInfo(String.format("Added tile handler for %s (%s) - %s", type.getName(), action, handler.getClass().getName()));
            tileHandlers.computeIfAbsent(tileType, (t) -> new HashMap<>()).put(action, handler);
        }
    }

    @Override
    public int apiVersion() {
        return 1;
    }
}
