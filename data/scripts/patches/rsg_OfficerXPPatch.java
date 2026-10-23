package data.scripts.patches;

import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import data.scripts.officers.rsg_SecondOfficers;
import patchlib.api.context.AfterContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.store.PatchData;

import java.util.HashMap;
import java.util.Map;

/**
 * Post-battle officer XP. Vanilla pays each officer from FleetEncounterContext.gainOfficerXP by the time their ship
 * was deployed; co-pilots aren't in that list, so pay each officer's co-pilot the same amount right after them.
 * A subtype match, because mods replace the method: Nexerelin's NexFleetEncounterContext overrides it without
 * calling super (and adds its own frigate/destroyer bonus, which the co-pilot then shares).
 */
@Patch(target = @ClassMatch(subtype = FleetEncounterContext.class))
public class rsg_OfficerXPPatch {

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "gainOfficerXP", parameterCount = 2),
            call = @MethodMatch(methodName = "addXP", parameterCount = 2),
            owner = @ClassMatch(subtype = OfficerDataAPI.class))
    public static void redirectOfficerXP(MethodCallContext context) {
        context.call();
        Object officer = context.getCallReceiver();
        Object xp = context.getCallArg(0);
        if (!(officer instanceof OfficerDataAPI) || !(xp instanceof Long)) return;
        PersonAPI second = rsg_SecondOfficers.getSecondOfficerOfCaptain(((OfficerDataAPI) officer).getPerson());
        if (second == null) return;
        FleetDataAPI fleetData = rsg_SecondOfficers.getPlayerFleetData();
        OfficerDataAPI secondData = fleetData != null ? fleetData.getOfficerData(second) : null;
        if (secondData == null) return;
        Object panel = context.getCallArg(1);
        secondData.addXP((Long) xp, panel instanceof TextPanelAPI ? (TextPanelAPI) panel : null);
    }

    private static final String KEY_FLAGSHIP_TIME = "rsg_flagshipDeployedTime";

    /**
     * Vanilla's per-engagement tally skips a ship whose deployed time was all under player control, which is usually
     * the flagship's, so it never records the flagship's time. Record its full deployed time here, per encounter.
     */
    @After(target = @MethodMatch(methodName = "tallyOfficerTime", parameterCount = 2))
    public static void afterTallyOfficerTime(AfterContext context) {
        if (!context.isMostDerivedCall()) return;
        Object resultArg = context.getArg(1);
        if (!(resultArg instanceof EngagementResultForFleetAPI)) return;
        EngagementResultForFleetAPI result = (EngagementResultForFleetAPI) resultArg;
        if (result.getFleet() == null || !result.getFleet().isPlayerFleet()) return;

        PatchData store = context.getData();
        Map<FleetMemberAPI, Float> times = store.get(KEY_FLAGSHIP_TIME);
        if (times == null) {
            times = new HashMap<FleetMemberAPI, Float>();
            store.put(KEY_FLAGSHIP_TIME, times);
        }
        for (DeployedFleetMemberAPI deployed : result.getAllEverDeployedCopy()) {
            FleetMemberAPI member = deployed.getMember();
            if (member == null || !member.isFlagship() || deployed.getShip() == null) continue;
            Float before = times.get(member);
            times.put(member, (before != null ? before : 0f) + deployed.getShip().getFullTimeDeployed());
        }
    }

    /**
     * The flagship's co-pilot. The flagship's captain is the player, who isn't an officer, so the redirect above
     * never fires for it. Pay the co-pilot what an officer deployed that long would get, by vanilla's formula:
     * time / longest deployment, times xp / number of officers.
     */
    @After(target = @MethodMatch(methodName = "gainOfficerXP", parameterCount = 2))
    public static void afterGainOfficerXP(AfterContext context) {
        // a subclass calling super would get here twice
        if (!context.isMostDerivedCall()) return;
        Object dataArg = context.getArg(0);
        Object xpArg = context.getArg(1);
        if (!(dataArg instanceof DataForEncounterSide) || !(xpArg instanceof Float)) return;
        DataForEncounterSide data = (DataForEncounterSide) dataArg;
        Map<FleetMemberAPI, Float> times = context.getData().get(KEY_FLAGSHIP_TIME);
        FleetDataAPI fleetData = rsg_SecondOfficers.getPlayerFleetData();
        if (times == null || fleetData == null) return;

        float xp = (Float) xpArg;
        float max = Math.max(1f, data.getMaxTimeDeployed());
        float officers = Math.max(1f, data.getOfficerData().size());
        for (Map.Entry<FleetMemberAPI, Float> entry : times.entrySet()) {
            FleetMemberAPI member = entry.getKey();
            PersonAPI second = rsg_SecondOfficers.get(member);
            if (second == null) second = rsg_SecondOfficers.getSecondOfficerOfCaptain(member.getCaptain());
            OfficerDataAPI secondData = second != null ? fleetData.getOfficerData(second) : null;
            if (secondData == null) continue;

            float f = Math.max(0f, Math.min(1f, entry.getValue() / max));
            long amount = (long) (f * xp / officers);
            if (amount > 0) secondData.addXP(amount, getTextPanel(context));
        }
    }

    private static TextPanelAPI getTextPanel(AfterContext context) {
        Object self = context.getSelf();
        return self instanceof FleetEncounterContext ? ((FleetEncounterContext) self).textPanelForXPGain : null;
    }
}
