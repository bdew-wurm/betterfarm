package net.bdew.wurm.betterfarm.api;

public interface IBetterFarmAPI {
    void addItemAreaHandler(int templateId, IItemAreaActions handler);

    int apiVersion();
}
