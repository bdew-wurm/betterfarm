package net.bdew.wurm.betterfarm.api;

import com.wurmonline.server.behaviours.Actions;

public enum AreaActionType {
    CULTIVATE(Actions.CULTIVATE, "Cultivate", "cultivating", null),
    SOW(Actions.SOW, "Sow", "sowing", null),
    FARM(Actions.FARM, "Farm", "farming", null),
    HARVEST(Actions.HARVEST, "Harvest", "harvesting", null),
    HARVEST_AND_REPLANT((short) -1, "Harvest and replant", "harvesting", null),
    PICK_SPROUT(Actions.PICKSPROUT, "Pick sprout", "picking", "Nature"),
    PRUNE(Actions.PRUNE, "Prune", "pruning", "Nature"),
    PLANT(Actions.PLANT, "Plant", "planting", "Nature");

    public final short baseAction;
    public final String name;
    public final String verb;
    public final String goesUnder;

    AreaActionType(short baseAction, String name, String verb, String goesUnder) {
        this.baseAction = baseAction;
        this.name = name;
        this.verb = verb;
        this.goesUnder = goesUnder;
    }
}
