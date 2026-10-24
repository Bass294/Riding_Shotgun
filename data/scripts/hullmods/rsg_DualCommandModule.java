package data.scripts.hullmods;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.officers.rsg_SecondOfficerSkills;
import data.scripts.officers.rsg_SecondOfficers;

/**
 * Hidden hullmod on the station modules of a ship with the Dual Command Interface. Vanilla builds each module as a
 * ship of its own with a temporary fleet member and the parent's captain, so captain skills reach modules but the
 * parent's hullmods don't. This applies the parent's co-pilot to the module at the same points the captain's skills
 * are: the module's stats as it is built, the built module, and its fighters.
 * The parent finds its modules' variants and adds this hullmod and a tag naming itself (rsg_DualCommandInterface).
 */
public class rsg_DualCommandModule extends BaseHullMod {

    public static final String ID = "rsg_dual_command_module";
    // variant tag on each module: this prefix plus the parent fleet member's id
    public static final String PARENT_TAG_PREFIX = "rsg_copilot_parent_";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        FleetMemberAPI parent = getParent(stats.getVariant());
        PersonAPI second = rsg_SecondOfficers.get(parent);
        if (second == null) return;
        rsg_SecondOfficerSkills.applyToStats(second, parent.getCaptain(), stats, hullSize);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // as on the parent: ships built without a captain get no captain skill effects from vanilla either
        if (ship.getCaptain() == null) return;
        FleetMemberAPI parent = getParent(ship.getVariant());
        PersonAPI second = rsg_SecondOfficers.get(parent);
        if (second == null) return;
        rsg_SecondOfficerSkills.applyAfterShipCreation(second, parent.getCaptain(), ship);
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        PersonAPI second = rsg_SecondOfficers.get(getParent(ship.getVariant()));
        if (second == null) return;
        rsg_SecondOfficerSkills.applyToFighter(second, ship.getCaptain(), fighter);
    }

    /** The player ship this module belongs to, from the tag its parent put on the module's variant. */
    private static FleetMemberAPI getParent(ShipVariantAPI variant) {
        if (variant == null) return null;
        for (String tag : variant.getTags()) {
            if (tag.startsWith(PARENT_TAG_PREFIX)) {
                return rsg_SecondOfficers.findMember(rsg_SecondOfficers.getPlayerFleetData(), tag.substring(PARENT_TAG_PREFIX.length()));
            }
        }
        return null;
    }
}
