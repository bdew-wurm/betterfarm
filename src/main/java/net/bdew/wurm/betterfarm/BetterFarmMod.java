package net.bdew.wurm.betterfarm;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BetterFarmMod implements WurmServerMod, Configurable, PreInitable, Initable, ServerStartedListener, ModListener {
    private static final Logger logger = Logger.getLogger("BetterFarm");

    public static int extraHarvest = 0;

    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static void logWarning(String msg) {
        if (logger != null)
            logger.log(Level.WARNING, msg);
    }

    public static void logInfo(String msg) {
        if (logger != null)
            logger.log(Level.INFO, msg);
    }

    static List<ActionDef> cultivateLevels;
    static List<ActionDef> sowLevels;
    static List<ActionDef> tendLevels;
    static List<ActionDef> harvestLevels;
    static List<ActionDef> replantLevels;

    private List<ActionDef> parseDef(String str) {
        ArrayList<ActionDef> result = new ArrayList<ActionDef>();
        if (str == null || str.isEmpty()) return result;
        String[] parts = str.trim().split(",");
        for (String part : parts) {
            if (!part.contains("@")) {
                logWarning("Invalid skill spec: " + part);
            } else {
                String[] split = part.split("@");
                int radius = Integer.parseInt(split[0]);
                float level = Float.parseFloat(split[1]);
                result.add(new ActionDef(radius, level));
            }
        }
        return result;
    }

    @Override
    public void configure(Properties properties) {
        cultivateLevels = parseDef(properties.getProperty("cultivate"));
        sowLevels = parseDef(properties.getProperty("sow"));
        tendLevels = parseDef(properties.getProperty("farm"));
        harvestLevels = parseDef(properties.getProperty("harvest"));
        replantLevels = parseDef(properties.getProperty("replant"));
    }

    @Override
    public void preInit() {
        try {
            ModActions.init();

            ClassPool classPool = HookManager.getInstance().getClassPool();
            classPool.getCtClass("com.wurmonline.server.behaviours.BehaviourDispatcher")
                    .getMethod("requestActionForTiles", "(Lcom/wurmonline/server/creatures/Creature;JZLcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Behaviour;)Lcom/wurmonline/server/behaviours/BehaviourDispatcher$RequestParam;")
                    .insertAfter("return net.bdew.wurm.betterfarm.AreaActions.tileBehaviourHook($_, $1, $2, $3, $4);");

        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void modInitialized(ModEntry<?> modEntry) {
        if (modEntry.getWurmMod().getClass().getName().equals("org.gotti.wurmunlimited.mods.cropmod.CropMod")) {
            extraHarvest = Integer.parseInt(modEntry.getProperties().getProperty("extraHarvest", "0"));
            logInfo("Cropmod detected, extraHarvest = " + extraHarvest);
        }
    }

    @Override
    public void onServerStarted() {
        AreaActions.initActionLists();
    }
}
