package data.scripts.hullmods;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficerSkills;
import data.scripts.officers.rsg_SecondOfficers;

import java.util.List;

/**
 * Gives the ship a second officer seat. The seat itself is tracked by rsg_SecondOfficers; this hullmod is what
 * applies the second officer's skills, since vanilla runs hullmod effects right after the captain's skills
 * (FleetMember.updateStats for campaign stats, the Ship constructor for combat, fighter launch for fighters).
 */
public class rsg_DualCommandInterface extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        FleetMemberAPI member = stats.getFleetMember();
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) return;
        rsg_SecondOfficerSkills.applyToStats(second, member.getCaptain(), stats, hullSize);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        FleetMemberAPI member = getMember(ship);
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) return;
        rsg_SecondOfficerSkills.applyAfterShipCreation(second, member.getCaptain(), ship);
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        PersonAPI second = rsg_SecondOfficers.get(getMember(ship));
        if (second == null) return;
        // vanilla applies the carrier's current captain to its fighters, so compare against that captain
        rsg_SecondOfficerSkills.applyToFighter(second, ship.getCaptain(), fighter);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && !Misc.isAutomated(ship) && ship.getParentStation() == null;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship != null && ship.getParentStation() != null) return "Can't be installed on a ship module";
        return "Can't be installed on automated ships";
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if (isForModSpec || ship == null) return;
        FleetMemberAPI member = getMember(ship);
        if (member == null) return;
        float opad = 10f;
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) {
            tooltip.addPara("No second officer assigned.", Misc.getGrayColor(), opad);
            return;
        }

        TooltipMakerAPI text = tooltip.beginImageWithText(second.getPortraitSprite(), 48f);
        text.addPara("Second officer: %s, level %s", 0f, Misc.getHighlightColor(),
                second.getNameString(), "" + second.getStats().getLevel());

        List<SkillSpecAPI> skills = rsg_SecondOfficerSkills.getUniqueSkills(second, member.getCaptain());
        if (skills.isEmpty()) {
            text.addPara("All of their skills are already covered by the captain.", Misc.getGrayColor(), 3f);
        } else {
            StringBuilder names = new StringBuilder();
            for (SkillSpecAPI skill : skills) {
                if (names.length() > 0) names.append(", ");
                names.append(skill.getName());
                String part = rsg_SecondOfficerSkills.getAppliedPart(second, member.getCaptain(), skill);
                if (part != null) names.append(" (").append(part).append(")");
            }
            text.addPara("Adds: %s", 3f, Misc.getHighlightColor(), names.toString());
        }
        tooltip.addImageWithText(opad);
    }

    /**
     * ShipAPI.getFleetMember() looks the ship up among deployed ships, which a ship that is still being created
     * is not yet part of, so it returns null in applyEffectsAfterShipCreation. The stats get their member set
     * before any hullmod runs, so read it from there first.
     */
    private static FleetMemberAPI getMember(ShipAPI ship) {
        if (ship == null) return null;
        FleetMemberAPI member = ship.getMutableStats() != null ? ship.getMutableStats().getFleetMember() : null;
        return member != null ? member : ship.getFleetMember();
    }
}
