package data.scripts.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficerSkills;
import data.scripts.officers.rsg_SecondOfficers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives the ship a second officer seat. The seat itself is tracked by rsg_SecondOfficers; this hullmod is what
 * applies the second officer's skills, since vanilla runs hullmod effects right after the captain's skills
 * (FleetMember.updateStats for campaign stats, the Ship constructor for combat, fighter launch for fighters).
 */
public class rsg_DualCommandInterface extends BaseHullMod {

    public static final String REQUIRED_ITEM = "rsg_gemini_core";
    // deployment cost increase per skill the second officer adds
    public static final float DP_PERCENT_PER_SKILL = 8f;
    private static final String BULLET = "•";

    /**
     * Installing needs a Gemini Core. Vanilla's HullModItemManager does the rest: it blocks installing without one,
     * takes it when the refit is saved, gives it back when the hullmod comes off, and BaseHullMod shows the
     * "Requires item" / "Using item" line in the tooltip.
     */
    @Override
    public CargoStackAPI getRequiredItem() {
        return Global.getSettings().createCargoStack(CargoItemType.SPECIAL, new SpecialItemData(REQUIRED_ITEM, null), null);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        FleetMemberAPI member = stats.getFleetMember();
        linkModules(member, stats.getVariant());
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) return;
        rsg_SecondOfficerSkills.applyToStats(second, member.getCaptain(), stats, hullSize);

        // deployment cost, the same two stats vanilla's Neural Integrator raises
        float percent = getDeploymentIncreasePercent(second, member.getCaptain());
        if (percent > 0f) {
            stats.getSuppliesToRecover().modifyPercent(id, percent);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyPercent(id, percent);
        }
    }

    /**
     * Gives each station module the hidden rsg_DualCommandModule and a tag naming this ship, so the co-pilot's skills
     * reach the modules when the game builds them. Only the player's own refit variants are edited; a module variant
     * that isn't one of those yet (a shared stock variant) is replaced by a refit copy first, as vanilla's refit does.
     */
    private static void linkModules(FleetMemberAPI member, ShipVariantAPI variant) {
        if (member == null || variant == null || variant.getSource() != VariantSource.REFIT) return;
        if (!rsg_SecondOfficers.isInPlayerFleet(member, rsg_SecondOfficers.getPlayerFleetData())) return;
        String tag = rsg_DualCommandModule.PARENT_TAG_PREFIX + member.getId();
        for (String slot : variant.getModuleSlots()) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module == null || (module.hasHullMod(rsg_DualCommandModule.ID) && module.hasTag(tag))) continue;
            if (module.getSource() != VariantSource.REFIT) {
                module = module.clone();
                module.setSource(VariantSource.REFIT);
                variant.setModuleVariant(slot, module);
            }
            // a module variant copied from another ship would still name that ship
            for (String old : new ArrayList<String>(module.getTags())) {
                if (old.startsWith(rsg_DualCommandModule.PARENT_TAG_PREFIX)) module.removeTag(old);
            }
            module.addTag(tag);
            if (!module.hasHullMod(rsg_DualCommandModule.ID)) module.addPermaMod(rsg_DualCommandModule.ID, false);
        }
    }

    /** DP_PERCENT_PER_SKILL for each skill the second officer adds; skills the captain already has don't count. */
    public static float getDeploymentIncreasePercent(PersonAPI second, PersonAPI captain) {
        if (second == null) return 0f;
        return DP_PERCENT_PER_SKILL * rsg_SecondOfficerSkills.getUniqueSkills(second, captain).size();
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        FleetMemberAPI member = getMember(ship);
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) return;
        // ships built without a captain (fleet screen previews and the like) get no captain skill effects from vanilla
        // either, and some modded skills read ship.getCaptain() unchecked
        if (ship.getCaptain() == null) return;
        rsg_SecondOfficerSkills.applyAfterShipCreation(second, member.getCaptain(), ship);
        // so the co-pilot still gets post-battle XP if this ship is lost (rsg_OfficerXPPatch)
        if (Global.getCurrentState() == GameState.COMBAT) rsg_SecondOfficers.recordCombatPair(member.getCaptain(), second);
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        PersonAPI second = rsg_SecondOfficers.get(getMember(ship));
        if (second == null) return;
        // vanilla applies the carrier's current captain to its fighters, so compare against that captain
        rsg_SecondOfficerSkills.applyToFighter(second, ship.getCaptain(), fighter);
    }

    // automated ships take an AI core co-pilot instead of an officer
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getParentStation() == null;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Can't be installed on a ship module";
    }

    // the description from hull_mods.csv first, then the headed sections below, like Arma Armatura's hullmods
    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color h = Misc.getHighlightColor();

        tooltip.addSectionHeading("Effect", Alignment.MID, pad);
        tooltip.addPara("%s Allows assigning a second officer to the ship.", pad, h, BULLET);
        tooltip.addPara("%s The co-pilot's combat skills also apply their effects.", padS, h, BULLET);
        tooltip.addPara("%s Duplicate skills have no additional effect.", padS, h, BULLET);
        tooltip.addPara("%s Skills gain their elite effect if either officer has the elite skill.", padS, h, BULLET);
        tooltip.addPara("%s Requires a %s to install. The core is returned when the hullmod is removed.",
                padS, h, BULLET, "Gemini Core");
        tooltip.addPara("%s An AI core co-pilot does not increase auto-ship point cost.", padS, h, BULLET);

        tooltip.addSectionHeading("Deployment Point Penalty", Alignment.MID, pad);
        tooltip.addPara("%s %s %s for each non-duplicate skill added by the co-pilot.", pad,
                new Color[] {h, Misc.getNegativeHighlightColor(), h},
                BULLET, "Deployment cost is increased by", Misc.getRoundedValueMaxOneAfterDecimal(DP_PERCENT_PER_SKILL) + "%");

        FleetMemberAPI member = isForModSpec ? null : getMember(ship);
        PersonAPI second = member != null ? rsg_SecondOfficers.get(member) : null;
        if (member != null) {
            float percent = getDeploymentIncreasePercent(second, member.getCaptain());
            float dp = member.getHullSpec().getSuppliesToRecover() * percent / 100f;
            int skills = second != null ? rsg_SecondOfficerSkills.getUniqueSkills(second, member.getCaptain()).size() : 0;
            tooltip.addPara("%s Currently adding %s. %s (%s) deployment point cost", padS, h, BULLET,
                    skills == 1 ? "1 skill" : skills + " skills",
                    "+" + Misc.getRoundedValueMaxOneAfterDecimal(percent) + "%",
                    "+" + Misc.getRoundedValueMaxOneAfterDecimal(dp));
        }

        if (member == null) return;
        tooltip.addSectionHeading("Co-pilot", Alignment.MID, pad);
        if (second == null) {
            tooltip.addPara("No co-pilot assigned.", Misc.getGrayColor(), pad);
            return;
        }
        TooltipMakerAPI text = tooltip.beginImageWithText(second.getPortraitSprite(), 48f);
        text.addPara("%s, level %s", 0f, h, second.getNameString(), "" + second.getStats().getLevel());
        List<SkillSpecAPI> skills = rsg_SecondOfficerSkills.getUniqueSkills(second, member.getCaptain());
        if (skills.isEmpty()) {
            text.addPara("All of their skills are already covered by the captain.", Misc.getGrayColor(), 3f);
        } else {
            StringBuilder names = new StringBuilder();
            for (SkillSpecAPI skill : skills) {
                if (names.length() > 0) names.append(", ");
                names.append(skill.getName());
                String part = rsg_SecondOfficerSkills.getAppliedPart(second, member.getCaptain(), skill);
                // only the elite markers are shown here
                if (part != null && !"base".equals(part)) names.append(" (").append(part).append(")");
            }
            text.addPara("Adding: %s", 3f, h, names.toString());
        }
        tooltip.addImageWithText(pad);
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
