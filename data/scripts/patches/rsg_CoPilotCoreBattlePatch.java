package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import data.scripts.officers.rsg_SecondOfficers;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.store.PatchData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI core co-pilots on ships lost in battle, matching what vanilla does with a captain's core. Vanilla keeps the
 * core with a ship that is recovered (it restores the original captain), puts it in the loot when the ship was
 * offered for recovery but not taken, and loses it otherwise (unrecoverable hulls, battles that forbid recovery,
 * battles lost, wrecks not salvaged). A co-pilot core stays in its seat for the first case by itself; this marks the
 * ship as lost, remembers which lost ships were offered for recovery, and adds the core to the loot for the second.
 * Seats still marked when the dialog closes are written off by rsg_SecondOfficers.prune.
 * A subtype match, because Nexerelin replaces the encounter context with its own subclass.
 */
@Patch(target = @ClassMatch(subtype = FleetEncounterContext.class))
public class rsg_CoPilotCoreBattlePatch {

    // ids of the player's lost ships with an AI core co-pilot that were offered for recovery, on this encounter
    private static final String KEY_RECOVERABLE = "rsg_recoverableCoPilotCores";

    /** After each engagement: the player's ships disabled or destroyed in it so far. */
    @After(target = @MethodMatch(methodName = "processEngagementResults", parameterCount = 1))
    public static void afterProcessEngagementResults(AfterContext context) {
        // a subclass calling super would get here twice
        if (!context.isMostDerivedCall()) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Object self = context.getSelf();
        if (player == null || !(self instanceof FleetEncounterContext)) return;
        DataForEncounterSide side = ((FleetEncounterContext) self).getDataFor(player);
        if (side == null) return;
        for (FleetMemberData data : side.getOwnCasualties()) {
            if (data.getMember() != null) rsg_SecondOfficers.markLostInBattle(data.getMember());
        }
    }

    /**
     * The ships offered in the recovery dialog. Vanilla only keeps a captain's core for the loot when its ship is
     * offered here; the same goes for the co-pilot's.
     */
    @After(target = @MethodMatch(methodName = "getRecoverableShips", parameterCount = 3))
    public static void afterGetRecoverableShips(AfterContext context) {
        // Nexerelin overrides this and calls super; its result is the complete one
        if (!context.isMostDerivedCall()) return;
        Object self = context.getSelf();
        Object result = context.getReturnValue();
        if (!(self instanceof FleetEncounterContext)) return;
        List<FleetMemberAPI> offered = new ArrayList<FleetMemberAPI>();
        if (result instanceof List) {
            for (Object o : (List<?>) result) {
                if (o instanceof FleetMemberAPI) offered.add((FleetMemberAPI) o);
            }
        }
        offered.addAll(((FleetEncounterContext) self).getStoryRecoverableShips());

        Set<String> ids = new HashSet<String>();
        for (FleetMemberAPI member : offered) {
            PersonAPI stored = rsg_SecondOfficers.getStored(member);
            if (stored != null && stored.isAICore()) ids.add(member.getId());
        }
        PatchData data = context.getData();
        if (ids.isEmpty()) data.remove(KEY_RECOVERABLE);
        else data.put(KEY_RECOVERABLE, ids);
    }

    /**
     * Vanilla salvages each of the player's lost ships that wasn't recovered here (own = true), adding its captain's
     * core to the loot if the ship was offered for recovery. Do the same for the co-pilot's core.
     */
    @After(target = @MethodMatch(methodName = "lootWeapons", parameterCount = 5))
    public static void afterLootWeapons(AfterContext context) {
        if (!context.isMostDerivedCall()) return;
        Object member = context.getArg(0);
        boolean own = Boolean.TRUE.equals(context.getArg(2));
        boolean lootingModule = Boolean.TRUE.equals(context.getArg(4));
        Object self = context.getSelf();
        if (!own || lootingModule || !(member instanceof FleetMemberAPI) || !(self instanceof FleetEncounterContext)) return;
        FleetMemberAPI m = (FleetMemberAPI) member;
        Set<String> recoverable = context.getData().get(KEY_RECOVERABLE);
        if (recoverable == null || !recoverable.remove(m.getId())) return;
        PersonAPI core = rsg_SecondOfficers.takeLostCore(m);
        if (core != null && core.getAICoreId() != null) {
            ((FleetEncounterContext) self).getLoot().addCommodity(core.getAICoreId(), 1f);
        }
    }
}
