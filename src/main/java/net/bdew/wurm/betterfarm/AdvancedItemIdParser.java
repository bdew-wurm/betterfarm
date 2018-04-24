package net.bdew.wurm.betterfarm;

import com.wurmonline.server.items.ItemTemplateFactory;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.items.ItemIdParser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class AdvancedItemIdParser extends ItemIdParser {
    private Map<String, Integer> idMap = new HashMap<>();

    public AdvancedItemIdParser() {
        IdFactory.getIdsFor(IdType.ITEMTEMPLATE).forEach(e -> idMap.put(e.getKey(), e.getValue()));
    }

    @Override
    protected int unparsable(String name) {
        Integer res = idMap.get(name);
        if (res != null) {
            return res;
        } else {
            return super.unparsable(name);
        }
    }

    public IntStream parseSafe(String name) {
        try {
            return IntStream.of(parse(name));
        } catch (Exception e) {
            BetterFarmMod.logWarning(String.format("Error parsing item id '%s': %s", name, e.toString()));
            return IntStream.empty();
        }
    }

    public int[] parseListSafe(String str) {
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .flatMapToInt(this::parseSafe)
                .filter(i -> ItemTemplateFactory.getInstance().getTemplateOrNull(i) != null)
                .toArray();
    }

}
