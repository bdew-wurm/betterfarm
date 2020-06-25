package net.bdew.wurm.betterfarm.api;

public interface BetterFarmAPIHandler {
    void addItemAreaHandler(int templateId, ItemAreaHandler handler);

    int apiVersion();
}
