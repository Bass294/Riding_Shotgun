package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;
import patchlib.api.context.ConstructorCallContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectConstructorCall;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The combat target reticle: ship name, hull and flux bars, and the captain's portrait above them. Vanilla leaves
 * the portrait off the player-controlled ship (and its modules) by comparing the ship against getPlayerShip(), once
 * when building the reticle and again every frame until a portrait exists. Answering null for those two
 * comparisons gives the player ship its portraits too. The class is obfuscated and identified by its fields; its
 * other getPlayerShip() call, which picks the ship to show a target for, is in a method neither redirect touches.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.renderers.A.null, the per-frame method Ò00000(float). The class,
// its subpackage and the method name will change between builds; the readable parent package and the field shape
// (a Ship and a CombatViewport field, unique under com.fs.starfarer.renderers on this build) should not.
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.renderers",
        packageRecursive = true,
        fieldMatches = {
                @FieldMatch(typeName = "com.fs.starfarer.combat.entities.Ship"),
                @FieldMatch(typeName = "com.fs.starfarer.combat.CombatViewport")
        }))
public class rsg_CombatReticlePatch {

    private static final Logger log = Global.getLogger(rsg_CombatReticlePatch.class);

    @RedirectMethodCall(
            target = @MethodMatch(methodType = MethodType.CONSTRUCTOR, parameterCount = 3),
            call = @MethodMatch(methodName = "getPlayerShip", parameterCount = 0),
            owner = @ClassMatch(subtype = CombatEngineAPI.class))
    public static void redirectPlayerShipOnBuild(MethodCallContext context) {
        // call down so other mods' layers on this call still run, then hide the player ship from the comparison
        context.call();
        context.setResult(null);
    }

    @RedirectMethodCall(
            target = @MethodMatch(methodType = MethodType.METHOD, parameterTypes = float.class),
            call = @MethodMatch(methodName = "getPlayerShip", parameterCount = 0),
            owner = @ClassMatch(subtype = CombatEngineAPI.class))
    public static void redirectPlayerShipOnAdvance(MethodCallContext context) {
        context.call();
        context.setResult(null);
    }

    // The reticle builds the captain portrait in the same two methods; it is the only 5-argument UI panel
    // constructed there (0.98a-RC8 Windows: new com.fs.starfarer.coreui.OOoo(captain, true, false, mode, color)).
    // Record which ship each portrait belongs to, so rsg_PortraitPatch can find the right second officer even when
    // the player captains two ships at once (Neural Link). The construction itself is left as it is.
    @RedirectConstructorCall(
            target = @MethodMatch(methodType = MethodType.CONSTRUCTOR, parameterCount = 3),
            type = @ClassMatch(subtype = UIPanelAPI.class),
            constructor = @MethodMatch(parameterCount = 5))
    public static void redirectPortraitOnBuild(ConstructorCallContext context) {
        recordShip(context);
    }

    @RedirectConstructorCall(
            target = @MethodMatch(methodType = MethodType.METHOD, parameterTypes = float.class),
            type = @ClassMatch(subtype = UIPanelAPI.class),
            constructor = @MethodMatch(parameterCount = 5))
    public static void redirectPortraitOnAdvance(ConstructorCallContext context) {
        recordShip(context);
    }

    // portrait -> its reticle's ship. A static map rather than getData(): the construction happens on the reticle,
    // and a patch can't write the portrait's own patch data. Both sides are weak so finished combats are collected.
    private static final Map<Object, WeakReference<ShipAPI>> PORTRAIT_SHIPS = new WeakHashMap<Object, WeakReference<ShipAPI>>();
    // the reticle's only Ship field
    private static final FieldQuerySpec SHIP = FieldQuery.create().typeName("com.fs.starfarer.combat.entities.Ship").build();

    /** The ship whose combat reticle this portrait belongs to, or null if it isn't a reticle portrait. */
    public static ShipAPI getShip(Object portrait) {
        WeakReference<ShipAPI> ref = PORTRAIT_SHIPS.get(portrait);
        return ref != null ? ref.get() : null;
    }

    private static void recordShip(ConstructorCallContext context) {
        Object portrait = context.call();
        context.setResult(portrait);
        try {
            if (portrait == null || !context.hasField(SHIP)) return;
            Object ship = context.getField(SHIP).get();
            if (ship instanceof ShipAPI) PORTRAIT_SHIPS.put(portrait, new WeakReference<ShipAPI>((ShipAPI) ship));
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't record a combat portrait's ship", t);
        }
    }
}
