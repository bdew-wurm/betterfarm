package net.bdew.wurm.betterfarm.api;

public interface IBetterFarmAPI {
    void addItemAreaHandler(int templateId, AreaActionType action, IItemAction handler);

    void addTileAreaHandler(byte tileType, AreaActionType action, ITileAction handler);

    int apiVersion();
}
