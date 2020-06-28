package net.bdew.wurm.betterfarm;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import net.bdew.wurm.betterfarm.api.BetterFarmAPI;
import net.bdew.wurm.betterfarm.area.AreaActions;
import net.bdew.wurm.betterfarm.planter.PlanterHooks;
import net.bdew.wurm.betterfarm.planter.PlanterRackPickAction;
import net.bdew.wurm.betterfarm.planter.PlanterRackPlantAction;
import net.bdew.wurm.betterfarm.trellis.TrellisActions;
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

    public static List<ActionDef> cultivateLevels;
    public static List<ActionDef> sowLevels;
    public static List<ActionDef> tendLevels;
    public static List<ActionDef> harvestLevels;
    public static List<ActionDef> replantLevels;
    public static List<ActionDef> pickLevels;
    public static List<ActionDef> pruneLevels;

    private static float planterPlantSkill, planterPickSkill;
    private static String addPotables;

    public static ApiImplementation apiHandler;

    private List<ActionDef> parseDef(String str) {
        ArrayList<ActionDef> result = new ArrayList<>();
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
        pickLevels = parseDef(properties.getProperty("pick"));
        pruneLevels = parseDef(properties.getProperty("prune"));
        planterPlantSkill = Float.parseFloat(properties.getProperty("planterPlantSkill", "-1"));
        planterPickSkill = Float.parseFloat(properties.getProperty("planterPickSkill", "-1"));
        addPotables = properties.getProperty("addPotables", "");
    }

    @Override
    public void preInit() {
        try {
            ModActions.init();

            BetterFarmAPI.INSTANCE = apiHandler = new ApiImplementation();

            ClassPool classPool = HookManager.getInstance().getClassPool();
            CtClass ctBehaviourDispatcher = classPool.getCtClass("com.wurmonline.server.behaviours.BehaviourDispatcher");
            ctBehaviourDispatcher.getMethod("requestActionForTiles", "(Lcom/wurmonline/server/creatures/Creature;JZLcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Behaviour;)Lcom/wurmonline/server/behaviours/BehaviourDispatcher$RequestParam;")
                    .insertAfter("return net.bdew.wurm.betterfarm.area.AreaActions.tileBehaviourHook($_, $1, $2, $3, $4);");

            ctBehaviourDispatcher.getMethod("requestActionForItemsBodyIdsCoinIds", "(Lcom/wurmonline/server/creatures/Creature;JLcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Behaviour;)Lcom/wurmonline/server/behaviours/BehaviourDispatcher$RequestParam;")
                    .insertAfter("return net.bdew.wurm.betterfarm.area.AreaActions.itemBehaviourHook($_, $1, $2, $3);");

            classPool.getCtClass("com.wurmonline.server.behaviours.TileDirtBehaviour")
                    .getMethod("action", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZIISF)Z")
                    .instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getMethodName().equals("destroyItem"))
                                m.replace("source.setWeight(source.getWeightGrams() - source.getTemplate().getWeightGrams(), true);");
                        }
                    });

            // Fix new plantables
            CtClass ctPlanterBehaviour = classPool.getCtClass("com.wurmonline.server.behaviours.PlanterBehaviour");
            ctPlanterBehaviour.getMethod("getBehavioursFor", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;)Ljava/util/List;")
                    .instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getMethodName().equals("isRaw"))
                                m.replace("$_ = $proceed() || net.bdew.wurm.betterfarm.planter.PlanterHooks.isPotable($0.getTemplateId());");
                        }
                    });
            ctPlanterBehaviour.getMethod("action", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;SF)Z")
                    .instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getMethodName().equals("isSpice"))
                                m.replace("$_ = $proceed() || net.bdew.wurm.betterfarm.planter.PlanterHooks.isPotable($0.getTemplateId());");
                        }
                    });

            ExprEditor nameFixer = new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("getActualName")) {
                        m.replace("$_=$proceed(); if ($_.startsWith(\"pile of \")) $_=$_.replace(\"pile of \", \"\");");
                    }
                }
            };

            CtClass ctItem = classPool.getCtClass("com.wurmonline.server.items.Item");
            ctItem.getMethod("AddBulkItemToCrate", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z").instrument(nameFixer);
            ctItem.getMethod("AddBulkItem", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z").instrument(nameFixer);

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
        if (!addPotables.isEmpty())
            PlanterHooks.addPotables(addPotables);
        AreaActions.initActionLists();
        if (planterPlantSkill > 0)
            ModActions.registerAction(new PlanterRackPlantAction(planterPlantSkill));
        if (planterPickSkill > 0)
            ModActions.registerAction(new PlanterRackPickAction(planterPickSkill));
        TrellisActions.register();
    }
}
