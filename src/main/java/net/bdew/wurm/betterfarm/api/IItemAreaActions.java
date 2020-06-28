package net.bdew.wurm.betterfarm.api;

import java.util.Map;

public interface IItemAreaActions {
    Map<AreaActionType, IItemAction> getActions();
}
