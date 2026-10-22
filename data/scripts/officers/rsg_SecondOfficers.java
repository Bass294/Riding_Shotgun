package data.scripts.officers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which officer sits in the second seat of which player ship.
 * Second officers stay in the fleet's normal officer list (so officer count and salary work as vanilla);
 * this only records the seat. Entries that stop being valid are ignored and later pruned, which leaves the
 * officer unassigned, the same as vanilla does when a ship is stored or sold.
 */
public class rsg_SecondOfficers {

    public static final String HULLMOD_ID = "rsg_dual_command";
    public static final String DATA_KEY = "rsg_second_officers";

    /** Returns the second officer of this member, or null if none is assigned or the assignment is no longer valid. */
    public static PersonAPI get(FleetMemberAPI member) {
        if (member == null) return null;
        Map<String, PersonAPI> map = getMap(false);
        if (map == null) return null;
        PersonAPI person = map.get(member.getId());
        if (person == null) return null;
        return getInvalidReason(member, person) == null ? person : null;
    }

    /**
     * Assigns the second officer. If the ship has no captain, the officer becomes its captain instead, since a
     * second seat without a first one would leave vanilla treating the ship as unofficered.
     * Returns null on success, otherwise the reason it was refused.
     */
    public static String set(FleetMemberAPI member, PersonAPI person) {
        if (member == null || person == null) return "No ship or officer given.";
        String reason = getInvalidReason(member, person);
        if (reason != null) return reason;

        Map<String, PersonAPI> map = getMap(true);
        FleetDataAPI fleetData = getPlayerFleetData();
        Iterator<Map.Entry<String, PersonAPI>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PersonAPI> entry = it.next();
            if (entry.getValue() == person && !entry.getKey().equals(member.getId())) {
                it.remove();
                updateStats(findMember(fleetData, entry.getKey()));
            }
        }
        if (member.getCaptain() == null || member.getCaptain().isDefault()) {
            map.remove(member.getId());
            member.setCaptain(person);
            return null;
        }
        map.put(member.getId(), person);
        member.updateStats();
        return null;
    }

    /** Returns the player ship this officer is the second officer of, or null. */
    public static FleetMemberAPI getMemberWithSecondOfficer(PersonAPI person) {
        if (person == null) return null;
        Map<String, PersonAPI> map = getMap(false);
        if (map == null || !map.containsValue(person)) return null;
        FleetDataAPI fleetData = getPlayerFleetData();
        for (Map.Entry<String, PersonAPI> entry : map.entrySet()) {
            if (entry.getValue() != person) continue;
            FleetMemberAPI member = findMember(fleetData, entry.getKey());
            if (member != null && get(member) == person) return member;
        }
        return null;
    }

    public static void clear(FleetMemberAPI member) {
        if (member == null) return;
        Map<String, PersonAPI> map = getMap(false);
        if (map == null) return;
        if (map.remove(member.getId()) != null) {
            member.updateStats();
        }
    }

    /** Drops every assignment that is no longer valid. */
    public static void prune() {
        Map<String, PersonAPI> map = getMap(false);
        if (map == null || map.isEmpty()) return;
        FleetDataAPI fleetData = getPlayerFleetData();
        if (fleetData == null) return;

        Set<PersonAPI> seated = new HashSet<PersonAPI>();
        Iterator<Map.Entry<String, PersonAPI>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PersonAPI> entry = it.next();
            FleetMemberAPI member = findMember(fleetData, entry.getKey());
            PersonAPI person = entry.getValue();
            if (member == null || person == null || getInvalidReason(member, person) != null || !seated.add(person)) {
                it.remove();
                updateStats(member);
            }
        }
    }

    /** Returns null if the officer can sit in this member's second seat, otherwise a player-readable reason. */
    public static String getInvalidReason(FleetMemberAPI member, PersonAPI person) {
        FleetDataAPI fleetData = getPlayerFleetData();
        if (fleetData == null) return "No player fleet.";
        if (member.getFleetData() != fleetData) return "The ship is not in your fleet.";
        if (member.isFighterWing() || member.getVariant() == null) return "Not a ship.";
        if (!hasDualCommand(member)) return "The ship does not have the Dual Command Interface.";
        if (Misc.isAutomated(member)) return "Automated ships can't take a second officer.";
        if (person.isPlayer()) return "You can't be a second officer.";
        if (person.isAICore() || person.isDefault()) return "Only officers can be second officers.";
        if (fleetData.getOfficerData(person) == null) return person.getNameString() + " is not an officer in your fleet.";
        for (FleetMemberAPI other : fleetData.getMembersListCopy()) {
            if (other.getCaptain() == person) {
                return person.getNameString() + " is already the captain of the " + other.getShipName() + ".";
            }
        }
        if (isPastMax(fleetData, person)) return person.getNameString() + " is over your officer limit.";
        return null;
    }

    /**
     * The refit screen edits a working copy of the variant and only writes it back to the ship on some actions
     * (switching ships, the hullmod picker, ...), so the ship being refit counts as having the hullmod if the
     * copy on screen has it. The refit officer box reports that copy here.
     */
    private static String refitMemberId;
    private static WeakReference<ShipVariantAPI> refitVariant;

    public static void setRefitVariant(FleetMemberAPI member, ShipVariantAPI variant) {
        refitMemberId = member != null ? member.getId() : null;
        refitVariant = variant != null ? new WeakReference<ShipVariantAPI>(variant) : null;
    }

    public static boolean hasDualCommand(FleetMemberAPI member) {
        if (member.getVariant().hasHullMod(HULLMOD_ID)) return true;
        if (refitVariant == null || !member.getId().equals(refitMemberId)) return false;
        ShipVariantAPI variant = refitVariant.get();
        return variant != null && variant.hasHullMod(HULLMOD_ID);
    }

    /** Same rule as the vanilla officer list: non-mercenary officers beyond the officer limit, in list order, can't serve. */
    public static boolean isPastMax(FleetDataAPI fleetData, PersonAPI person) {
        if (Misc.isMercenary(person)) return false;
        int max = Global.getSector().getPlayerStats().getOfficerNumber().getModifiedInt();
        int count = 0;
        for (OfficerDataAPI od : fleetData.getOfficersCopy()) {
            PersonAPI p = od.getPerson();
            if (p.isAICore() || p.isPlayer() || Misc.isMercenary(p)) continue;
            if (p == person) return count >= max;
            count++;
        }
        return false;
    }

    public static FleetMemberAPI findMember(FleetDataAPI fleetData, String memberId) {
        if (fleetData == null || memberId == null) return null;
        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (memberId.equals(member.getId())) return member;
        }
        return null;
    }

    public static FleetDataAPI getPlayerFleetData() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet == null ? null : fleet.getFleetData();
    }

    private static void updateStats(FleetMemberAPI member) {
        if (member != null) member.updateStats();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PersonAPI> getMap(boolean create) {
        if (Global.getSector() == null) return null;
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object map = data.get(DATA_KEY);
        if (map instanceof Map) return (Map<String, PersonAPI>) map;
        if (!create) return null;
        Map<String, PersonAPI> created = new HashMap<String, PersonAPI>();
        data.put(DATA_KEY, created);
        return created;
    }
}
