package net.bdew.wurm.betterfarm.api;

public final class ActionEntryOverride {
    public final short number;
    public final String name;
    public final String verb;
    public final String goesUnder;

    public ActionEntryOverride(short number, String name, String verb, String goesUnder) {
        this.number = number;
        this.name = name;
        this.verb = verb;
        this.goesUnder = goesUnder;
    }
}
