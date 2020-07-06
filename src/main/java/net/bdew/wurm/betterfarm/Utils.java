package net.bdew.wurm.betterfarm;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import net.bdew.wurm.betterfarm.area.AreaActionPerformer;

import java.util.List;

public class Utils {
    public static Item getExistingProduce(Item container, int template) {
        for (Item item : container.getAllItems(true)) {
            if (item.getTemplateId() == template && item.getAuxData() == 0 && item.getRarity() == 0)
                return item;
        }
        return null;
    }

    public static void addStackedItems(Item container, int template, float ql, float amount, String name) {
        Item existing = getExistingProduce(container, template);
        if (existing != null) {
            int addWeight = (int) (amount * existing.getTemplate().getWeightGrams());
            int sumWeight = existing.getWeightGrams() + addWeight;
            float sumQl = (existing.getQualityLevel() * existing.getWeightGrams() / sumWeight) + (ql * addWeight / sumWeight);
            existing.setWeight(sumWeight, false);
            existing.setQualityLevel(sumQl);
            if (!existing.getName().contains("pile of"))
                existing.setName("pile of " + name);
            existing.sendUpdate();
        } else {
            final Item result;
            try {
                result = ItemFactory.createItem(template, ql, null);
                if (amount > 1) {
                    result.setWeight((int) (result.getTemplate().getWeightGrams() * amount), false);
                    result.setName("pile of " + name);
                }
                container.insertItem(result, true, false);
            } catch (FailedException | NoSuchTemplateException e) {
                BetterFarmMod.logException("Error creating stacked item", e);
            }
        }

    }

    public static void addOrReplaceActions(List<ActionEntry> list, int actionNumber, String name, List<AreaActionPerformer> available, String singleName, String goesUnder) {
        if (available.isEmpty()) return;
        if (actionNumber > 0) {
            int p = 0;
            for (ActionEntry actionEntry : list) {
                if (actionEntry.getNumber() == actionNumber) {
                    ActionEntry old = list.remove(p);
                    list.add(p++, new ActionEntry((short) (-1 - available.size()), old.getActionString(), ""));
                    list.add(p++, new ActionEntry(old.getNumber(), singleName, old.getVerbString()));
                    for (AreaActionPerformer act : available) {
                        list.add(p++, new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
                    }
                    return;
                }
                p++;
            }
        }
        if (goesUnder != null) {
            int p = 0;
            for (ActionEntry actionEntry : list) {
                if (actionEntry.getActionString().equals(goesUnder) && actionEntry.getNumber() < 0) {
                    ActionEntry old = list.remove(p);
                    list.add(p++, new ActionEntry((short) (old.getNumber() - 1), old.getActionString(), old.getVerbString()));
                    for (int toSkip = -old.getNumber(); toSkip > 0; toSkip--) {
                        if (list.get(p).getNumber() < 0) toSkip += -list.get(p).getNumber();
                        p++;
                    }
                    if (available.size() > 1) {
                        list.add(p++, new ActionEntry((short) -available.size(), name, ""));
                        for (AreaActionPerformer act : available) {
                            list.add(p++, new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
                        }
                    } else {
                        AreaActionPerformer act = available.get(0);
                        list.add(p, new ActionEntry(act.actionEntry.getNumber(), String.format("%s (%dx%x)", name, act.radius * 2 + 1, act.radius * 2 + 1), ""));
                    }
                    return;
                }
                p++;
            }
            list.add(new ActionEntry((short) -1, goesUnder, ""));
            if (available.size() > 1) {
                list.add(new ActionEntry((short) -available.size(), name, ""));
                for (AreaActionPerformer act : available) {
                    list.add(new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
                }
            } else {
                list.add(available.get(0).actionEntry);
            }
        } else {
            if (available.size() > 1) {
                list.add(new ActionEntry((short) -available.size(), name, ""));
                for (AreaActionPerformer act : available) {
                    list.add(new ActionEntry(act.actionEntry.getNumber(), String.format("%dx%d Area", act.radius * 2 + 1, act.radius * 2 + 1), act.actionEntry.getVerbString()));
                }
            } else {
                AreaActionPerformer act = available.get(0);
                list.add(new ActionEntry(act.actionEntry.getNumber(), String.format("%s (%dx%x)", name, act.radius * 2 + 1, act.radius * 2 + 1), ""));
            }
        }
    }
}
