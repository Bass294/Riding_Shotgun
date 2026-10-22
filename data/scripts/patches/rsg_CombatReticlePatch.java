package data.scripts.patches;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

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
}
