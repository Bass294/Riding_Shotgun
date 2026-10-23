package data.scripts.officers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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

    // captain -> co-pilot as of each player ship's creation for combat, so post-battle XP can still find the
    // co-pilot of a ship that was lost (lost ships leave the fleet before XP is handed out). Weak keys.
    private static final Map<PersonAPI, WeakReference<PersonAPI>> COMBAT_PAIRS = new WeakHashMap<PersonAPI, WeakReference<PersonAPI>>();

    public static void recordCombatPair(PersonAPI captain, PersonAPI second) {
        if (captain == null || second == null) return;
        COMBAT_PAIRS.put(captain, new WeakReference<PersonAPI>(second));
    }

    /** The co-pilot flying with this captain: their current ship's, or failing that, the one recorded in combat. */
    public static PersonAPI getSecondOfficerOfCaptain(PersonAPI captain) {
        if (captain == null) return null;
        FleetDataAPI fleetData = getPlayerFleetData();
        if (fleetData != null) {
            for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
                if (member.getCaptain() == captain) {
                    PersonAPI second = get(member);
                    if (second != null) return second;
                }
            }
        }
        WeakReference<PersonAPI> ref = COMBAT_PAIRS.get(captain);
        return ref != null ? ref.get() : null;
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
            } else if (hasNoCaptain(member)) {
                it.remove();
                member.setCaptain(person);
            }
        }
    }

    /**
     * If the ship lost its captain (unassigned, a mercenary leaving, recovery after battle) but still has a second
     * officer, moves the second officer into the captain's seat. Returns true if it did.
     */
    public static boolean promoteIfCaptainless(FleetMemberAPI member) {
        if (member == null || !hasNoCaptain(member)) return false;
        PersonAPI second = get(member);
        if (second == null) return false;
        Map<String, PersonAPI> map = getMap(false);
        if (map != null) map.remove(member.getId());
        member.setCaptain(second);
        return true;
    }

    private static boolean hasNoCaptain(FleetMemberAPI member) {
        return member.getCaptain() == null || member.getCaptain().isDefault();
    }

    /** Returns null if the officer can sit in this member's second seat, otherwise a player-readable reason. */
    public static String getInvalidReason(FleetMemberAPI member, PersonAPI person) {
        FleetDataAPI fleetData = getPlayerFleetData();
        if (fleetData == null) return "No player fleet.";
        if (member.getFleetData() != fleetData) return "The ship is not in your fleet.";
        if (member.isFighterWing() || member.getVariant() == null) return "Not a ship.";
        if (!hasDualCommand(member)) return "The ship does not have the Dual Command Interface.";
        if (Misc.isAutomated(member)) return "Automated ships can't take a co-pilot.";
        if (person.isPlayer()) return "You can't be a co-pilot.";
        if (person.isAICore() || person.isDefault()) return "Only officers can be co-pilots.";
        if (fleetData.getOfficerData(person) == null) return person.getNameString() + " is not an officer in your fleet.";
        for (FleetMemberAPI other : fleetData.getMembersListCopy()) {
            if (other.getCaptain() == person) {
                return person.getNameString() + " is already the captain of the " + other.getShipName() + ".";
            }
        }
        if (person.hasTag(OE_SUSPENDED_TAG)) return person.getNameString() + " is suspended.";
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

    // Officer Extension (officerExtension) lets the player keep any number of officers and applies the limit only to
    // officers serving on a ship; it marks suspended officers with this tag
    private static final String OE_MOD_ID = "officerExtension";
    private static final String OE_SUSPENDED_TAG = "$officerextension_IsSuspended";
    // a flat bonus Officer Extension adds to the limit while an interaction dialog is open; not part of the real limit
    private static final String OE_TEMP_LIMIT_ID = "officerextension_TempOfficerCount";

    /**
     * Same rule as the vanilla officer list: non-mercenary officers beyond the officer limit, in list order, can't
     * serve. With Officer Extension, its rule instead: the limit counts officers serving on a ship, and co-pilots count
     * as serving, so the second seat can't be used to go past it.
     */
    public static boolean isPastMax(FleetDataAPI fleetData, PersonAPI person) {
        if (Misc.isMercenary(person)) return false;
        if (Global.getSettings().getModManager().isModEnabled(OE_MOD_ID)) return isPastServingMax(fleetData, person);
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

    /** Officer Extension's rule: past the limit if the other serving non-mercenary officers already fill it. */
    private static boolean isPastServingMax(FleetDataAPI fleetData, PersonAPI person) {
        Set<PersonAPI> serving = new HashSet<PersonAPI>();
        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (member.getCaptain() != null) serving.add(member.getCaptain());
        }
        Map<String, PersonAPI> map = getMap(false);
        // the seat assignments as stored; checking each one's validity here would recurse back into this check
        if (map != null) serving.addAll(map.values());
        serving.remove(person);

        int count = 0;
        for (OfficerDataAPI od : fleetData.getOfficersCopy()) {
            PersonAPI p = od.getPerson();
            if (p.isAICore() || p.isPlayer() || Misc.isMercenary(p)) continue;
            if (serving.contains(p)) count++;
        }
        MutableStat limit = Global.getSector().getPlayerStats().getOfficerNumber();
        StatMod temp = limit.getFlatStatMod(OE_TEMP_LIMIT_ID);
        int max = (int) (limit.getModifiedValue() - (temp != null ? temp.getValue() : 0f));
        return count >= max;
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
