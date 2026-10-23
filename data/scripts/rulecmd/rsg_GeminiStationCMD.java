package data.scripts.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import data.scripts.hullmods.rsg_DualCommandInterface;
import data.scripts.officers.rsg_SecondOfficers;
import data.scripts.world.rsg_GeminiLead;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * rsg_GeminiStationCMD prepareDefenders | addStationLoot | barCanShow | acceptLead
 *
 * Puts the Research Station Gemini defender fleet in the station's $defenderFleet, where vanilla's
 * SalvageDefenderInteraction picks it up. The fleet is built when the player arrives so it scales to their fleet,
 * and is reused if the player retreated and the damaged defenders are still around.
 */
public class rsg_GeminiStationCMD extends BaseCommandPlugin {

    // Remnant combat points per point of the player's fleet, clamped; the Radiant flagship comes on top
    private static final float FLEET_POINT_MULT = 1f;
    private static final float MIN_COMBAT_POINTS = 150f;
    private static final float MAX_COMBAT_POINTS = 450f;

    // vanilla's hullmod schematic item; its data is the hullmod id
    private static final String MODSPEC_ITEM = "modspec";

    private static final String FLAGSHIP_VARIANT = "radiant_Standard";
    // the variant's own hullmods, made s-mods
    private static final String[] FLAGSHIP_SMODS = {"targetingunit", "hardenedshieldemitter", "stabilizedshieldemitter"};

    // every vanilla combat officer skill that isn't deprecated
    private static final String[] CORE_SKILLS = {
            Skills.HELMSMANSHIP, Skills.COMBAT_ENDURANCE, Skills.IMPACT_MITIGATION, Skills.DAMAGE_CONTROL,
            Skills.FIELD_MODULATION, Skills.POINT_DEFENSE, Skills.TARGET_ANALYSIS, Skills.BALLISTIC_MASTERY,
            Skills.SYSTEMS_EXPERTISE, Skills.MISSILE_SPECIALIZATION, Skills.GUNNERY_IMPLANTS,
            Skills.ENERGY_WEAPON_MASTERY, Skills.ORDNANCE_EXPERTISE, Skills.POLARIZED_ARMOR
    };

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        String verb = params.get(0).getString(memoryMap);
        if ("prepareDefenders".equals(verb)) return prepareDefenders(dialog, memoryMap);
        if ("addStationLoot".equals(verb)) return addStationLoot(dialog);
        if ("barCanShow".equals(verb)) return rsg_GeminiLead.canShowAt(dialog.getInteractionTarget().getMarket());
        if ("acceptLead".equals(verb)) {
            rsg_GeminiLead.accept(dialog);
            return true;
        }
        return false;
    }

    /**
     * The station's guaranteed loot: one Dual Command Interface schematic and one Gemini Core, added as vanilla
     * "extra salvage" so SalvageEntity performSalvage puts them in the loot screen next to the random drops.
     */
    private boolean addStationLoot(InteractionDialogAPI dialog) {
        CargoAPI loot = Global.getFactory().createCargo(true);
        loot.addSpecial(new SpecialItemData(MODSPEC_ITEM, rsg_SecondOfficers.HULLMOD_ID), 1f);
        loot.addSpecial(new SpecialItemData(rsg_DualCommandInterface.REQUIRED_ITEM, null), 1f);
        BaseSalvageSpecial.addExtraSalvage(dialog.getInteractionTarget(), loot);
        return true;
    }

    private boolean prepareDefenders(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = getEntityMemory(memoryMap);
        // a retreat leaves the damaged defenders in memory for a while; fight those instead of fresh ones
        if (memory.getFleet("$defenderFleet") != null) return true;

        SectorEntityToken station = dialog.getInteractionTarget();
        Random random = Misc.getRandom(Misc.getSalvageSeed(station), 11);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        float points = player.getFleetPoints() * FLEET_POINT_MULT;
        points = Math.max(MIN_COMBAT_POINTS, Math.min(MAX_COMBAT_POINTS, points));
        FleetParamsV3 fleetParams = new FleetParamsV3(null, Factions.REMNANTS, 1f, FleetTypes.TASK_FORCE,
                points, 0f, 0f, 0f, 0f, 0f, 0f);
        fleetParams.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(fleetParams);
        if (fleet == null) return false;
        fleet.setNoFactionInName(true);
        fleet.setName("Gemini Defense Network");

        addFlagship(fleet, random);
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }

        memory.set("$defenderFleet", fleet);
        memory.set("$hasDefenders", true);
        return true;
    }

    /** The fleet's leader: a Radiant with three s-mods, commanded by the Gemini Core. */
    private static void addFlagship(CampaignFleetAPI fleet, Random random) {
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, FLAGSHIP_VARIANT);
        ShipVariantAPI variant = flagship.getVariant().clone();
        variant.setSource(VariantSource.REFIT);
        for (String mod : FLAGSHIP_SMODS) {
            variant.addPermaMod(mod, true);
        }
        // the Gemini Core is not an ordinary alpha core, so don't drop one when the ship is destroyed
        variant.addTag(Tags.VARIANT_DO_NOT_DROP_AI_CORE_FROM_CAPTAIN);
        flagship.setVariant(variant, false, true);

        PersonAPI core = createGeminiCore(random);
        flagship.setCaptain(core);
        fleet.getFleetData().addFleetMember(flagship);
        fleet.setCommander(core);
        fleet.getFleetData().setFlagship(flagship);
        fleet.getFleetData().sort();
    }

    private static PersonAPI createGeminiCore(Random random) {
        PersonAPI core = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE)
                .createPerson(Commodities.ALPHA_CORE, Factions.REMNANTS, random);
        core.setName(new FullName("Gemini Core", "", FullName.Gender.ANY));
        // registered in settings.json under graphics > characters so the game loads it
        core.setPortraitSprite(Global.getSettings().getSpriteName("characters", "rsg_gemini_core"));
        core.getStats().setSkipRefresh(true);
        List<String> current = new ArrayList<String>();
        for (com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI sl : core.getStats().getSkillsCopy()) {
            current.add(sl.getSkill().getId());
        }
        for (String id : current) {
            core.getStats().setSkillLevel(id, 0f);
        }
        for (String id : CORE_SKILLS) {
            core.getStats().setSkillLevel(id, 2f);
        }
        core.getStats().setLevel(CORE_SKILLS.length);
        core.getStats().setSkipRefresh(false);
        return core;
    }
}
