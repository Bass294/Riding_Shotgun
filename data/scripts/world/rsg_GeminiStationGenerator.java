package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Random;

/**
 * Places the one Research Station Gemini in the sector, the way Random Assortment of Things places its relic
 * stations: in a Ruins, Derelict, Remnant or Misc themed system (Ruins most likely), at a free orbit picked by
 * vanilla's theme generator. Runs from onGameLoad, so it also fills in saves made before the mod was added.
 */
public class rsg_GeminiStationGenerator {

    private static final Logger log = Global.getLogger(rsg_GeminiStationGenerator.class);

    public static final String ENTITY_TYPE = "rsg_gemini_station";
    public static final String GENERATED_KEY = "$rsg_gemini_generated";
    public static final String STATION_KEY = "$rsg_gemini_station";
    public static final String SYSTEM_TAG = "rsg_has_relic";
    // RAT's own relic system tag: keep to systems without one of its stations
    private static final String RAT_SYSTEM_TAG = "rat_has_relic";

    public static void generateIfNeeded() {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(GENERATED_KEY)) return;
        SectorEntityToken station = generate(new Random());
        if (station == null) {
            log.warn("Riding Shotgun: found no system for Research Station Gemini");
            return;
        }
        Global.getSector().getMemoryWithoutUpdate().set(GENERATED_KEY, true);
        Global.getSector().getMemoryWithoutUpdate().set(STATION_KEY, station);
        log.info("Riding Shotgun: placed Research Station Gemini in " + station.getStarSystem().getName());
    }

    private static SectorEntityToken generate(Random random) {
        CustomEntitySpecAPI spec = Global.getSettings().getCustomEntitySpec(ENTITY_TYPE);

        WeightedRandomPicker<StarSystemAPI> systems = new WeightedRandomPicker<StarSystemAPI>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            float weight = getSystemWeight(system);
            if (weight > 0f) systems.add(system, weight);
        }

        // RAT's default spread: mostly planet orbits, sometimes asteroid belts or the star
        LinkedHashMap<LocationType, Float> locations = new LinkedHashMap<LocationType, Float>();
        locations.put(LocationType.PLANET_ORBIT, 5f);
        locations.put(LocationType.IN_ASTEROID_BELT, 1f);
        locations.put(LocationType.STAR_ORBIT, 1f);

        while (!systems.isEmpty()) {
            StarSystemAPI system = systems.pickAndRemove();
            WeightedRandomPicker<EntityLocation> spots =
                    BaseThemeGenerator.getLocations(random, system, 100f + spec.getDefaultRadius(), locations);
            EntityLocation spot = spots.pick();
            if (spot == null || spot.orbit == null) continue;

            SectorEntityToken station = system.addCustomEntity(ENTITY_TYPE, null, ENTITY_TYPE, Factions.NEUTRAL);
            if (station == null) continue;
            station.setOrbit(spot.orbit);
            Misc.getSalvageSeed(station);
            system.addTag(SYSTEM_TAG);
            return station;
        }
        return null;
    }

    private static float getSystemWeight(StarSystemAPI system) {
        if (system.hasTag(Tags.SYSTEM_ABYSSAL) || system.hasPulsar()) return 0f;
        if (system.hasTag(SYSTEM_TAG) || system.hasTag(RAT_SYSTEM_TAG)) return 0f;
        if (system.hasTag(Tags.THEME_RUINS)) return 70f;
        if (system.hasTag(Tags.THEME_DERELICT)) return 15f;
        if (system.hasTag(Tags.THEME_REMNANT)) return 15f;
        if (system.hasTag(Tags.THEME_MISC)) return 5f;
        return 0f;
    }
}
