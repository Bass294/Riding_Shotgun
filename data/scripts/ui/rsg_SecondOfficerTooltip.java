package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficerSkills;
import data.scripts.officers.rsg_SecondOfficers;
import data.scripts.officers.rsg_SkillWhitelist;

import java.util.ArrayList;
import java.util.List;

/**
 * Officer tooltip for the second seat, laid out like the vanilla one (title, personality, combat skills, salary).
 * Skills the captain already has are listed separately and greyed out, since they add nothing to the ship, and so
 * are modded skills that are not on the co-pilot whitelist (rsg_SkillWhitelist), which are not applied.
 */
public class rsg_SecondOfficerTooltip implements TooltipMakerAPI.TooltipCreator {

    private static final float WIDTH = 425f;
    private static final float DUPLICATE_OPACITY = 0.35f;

    private final FleetMemberAPI member;

    public rsg_SecondOfficerTooltip(FleetMemberAPI member) {
        this.member = member;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return WIDTH;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        float pad = 3f;
        float opad = 10f;
        PersonAPI second = rsg_SecondOfficers.get(member);
        FactionAPI player = Global.getSector().getPlayerFaction();

        if (second == null) {
            tooltip.addTitle("Co-pilot", player.getBaseUIColor());
            if (Misc.isAutomated(member)) {
                tooltip.addPara("Assign an AI core to serve alongside the captain. The ship benefits from its "
                        + "non-duplicate combat skills.", opad);
            } else {
                tooltip.addPara("Assign an officer to serve alongside the captain. The ship benefits from their "
                        + "non-duplicate combat skills.", opad);
            }
            return;
        }

        FactionAPI faction = second.getFaction() != null ? second.getFaction() : player;
        boolean merc = Misc.isMercenary(second);
        String suffix = " (right-click to unassign)";
        String rank = second.getRank() != null ? second.getRank() + " " : "";
        LabelAPI title = tooltip.addTitle(rank + second.getNameString() + suffix,
                merc ? Misc.getStoryOptionColor() : faction.getBaseUIColor());
        title.setHighlight(suffix);
        title.setHighlightColor(Misc.getGrayColor());

        tooltip.addPara("Level %s, co-pilot of the %s", opad, Misc.getHighlightColor(),
                "" + second.getStats().getLevel(), member.getShipName());

        tooltip.addSectionHeading("Personality: " + second.getPersonalityAPI().getDisplayName().toLowerCase(),
                faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, 20f);
        tooltip.addPara(second.getPersonalityAPI().getDescription(), opad);

        tooltip.addSectionHeading("Combat-related skills", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
        addSkills(tooltip, second, member.getCaptain(), opad, pad);

        if (!second.isAICore()) {
            tooltip.addPara("Monthly salary: %s", opad, Misc.getHighlightColor(), Misc.getDGSCredits(Misc.getOfficerSalary(second)));
        }
        if (merc) {
            float daysLeft = Global.getSettings().getFloat("officerMercContractDur") - Misc.getMercDaysSinceHired(second);
            if (daysLeft >= 1f) {
                LabelAPI label = tooltip.addPara("This officer is a mercenary with %s " + ((int) daysLeft == 1 ? "day" : "days")
                        + " left on their contract.", opad, Misc.getHighlightColor(), "" + (int) daysLeft);
                label.setHighlight("mercenary", "" + (int) daysLeft);
                label.setHighlightColors(Misc.getStoryOptionColor(), Misc.getHighlightColor());
            } else {
                tooltip.addPara("This officer is a mercenary with a contract that's about to expire. They may wish to "
                        + "leave next time the fleet is in port.", opad, Misc.getStoryOptionColor(), "mercenary");
            }
        }
    }

    /**
     * Uses the vanilla skill grid (icon, elite frame, name, per-skill tooltip) through addSkillPanel, once for the
     * skills that apply and once, faded, for those the captain already covers. Each grid is built from a stand-in
     * person holding just those skills at the second officer's levels.
     */
    private static void addSkills(TooltipMakerAPI tooltip, PersonAPI second, PersonAPI captain, float opad, float pad) {
        PersonAPI applies = createSkillHolder();
        PersonAPI covered = createSkillHolder();
        PersonAPI unsupported = createSkillHolder();
        List<String> eliteOnly = new ArrayList<String>();
        boolean anyApplies = false, anyCovered = false, anyUnsupported = false;

        for (SkillLevelAPI sl : second.getStats().getSkillsCopy()) {
            SkillSpecAPI skill = sl.getSkill();
            if (!skill.isCombatOfficerSkill() || sl.getLevel() <= 0) continue;
            // modded skills not on the whitelist aren't applied (rsg_SkillWhitelist)
            if (!rsg_SkillWhitelist.isSupported(skill.getId())) {
                unsupported.getStats().setSkillLevel(skill.getId(), sl.getLevel());
                anyUnsupported = true;
                continue;
            }
            String part = rsg_SecondOfficerSkills.getAppliedPart(second, captain, skill);
            if (part != null) {
                applies.getStats().setSkillLevel(skill.getId(), sl.getLevel());
                anyApplies = true;
                if ("elite effects only".equals(part)) eliteOnly.add(skill.getName());
            } else {
                covered.getStats().setSkillLevel(skill.getId(), sl.getLevel());
                anyCovered = true;
            }
        }

        if (anyApplies) {
            tooltip.addSkillPanel(applies, opad);
        } else {
            tooltip.addPara("    None", pad);
        }
        if (!eliteOnly.isEmpty()) {
            String names = Misc.getAndJoined(eliteOnly);
            tooltip.addPara("Only the elite effects of %s apply; the skill is already known by the captain.",
                    opad, Misc.getGrayColor(), Misc.getHighlightColor(), names);
        }
        if (anyCovered) {
            tooltip.addPara("Already known by the captain:", Misc.getGrayColor(), opad);
            UIComponentAPI panel = tooltip.addSkillPanel(covered, pad);
            panel.setOpacity(DUPLICATE_OPACITY);
        }
        if (anyUnsupported) {
            tooltip.addPara("Unsupported:", Misc.getGrayColor(), opad);
            UIComponentAPI panel = tooltip.addSkillPanel(unsupported, pad);
            panel.setOpacity(DUPLICATE_OPACITY);
        }
    }

    private static PersonAPI createSkillHolder() {
        PersonAPI person = Global.getFactory().createPerson();
        // the skill grid reads the person's faction colors
        person.setFaction(Factions.PLAYER);
        return person;
    }
}
