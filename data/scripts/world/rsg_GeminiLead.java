package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import data.scripts.intel.rsg_GeminiLeadIntel;

import java.util.Random;

/**
 * The archivist's bar lead to Research Station Gemini, the Java behind the rsg_bar_* rows in rules.csv. Mirrors
 * Random Assortment of Things' Relics of the Past bar event: offered at non-pirate bars while the station is still
 * undiscovered and no lead has been taken; accepting gives intel pointing at the station.
 */
public class rsg_GeminiLead {

    public static final String ARCHIVIST_ID = "rsg_archivist";
    public static final String ACCEPTED_KEY = "$rsg_gemini_leadAccepted";
    // tokens the rows print
    public static final String STATION_NAME_KEY = "$rsg_geminiStationName";
    public static final String SYSTEM_NAME_KEY = "$rsg_geminiSystemName";

    // music set an earlier build stored on the station; cleared on load
    private static final String OLD_STATION_MUSIC = "music_tritach_encounter_hostile";

    // RAT's event is offered for 30-40 days at a time; each market re-rolls once per window
    private static final float WINDOW_DAYS = 35f;
    private static final float SHOW_CHANCE = 0.25f;

    /** Every load: make sure the archivist exists, and publish the station's name and system for the rows. */
    public static void onGameLoad() {
        getOrCreateArchivist();
        SectorEntityToken station = getStation();
        if (station == null || station.getStarSystem() == null) return;
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        global.set(STATION_NAME_KEY, station.getName());
        global.set(SYSTEM_NAME_KEY, station.getStarSystem().getNameWithNoType());
        // the station's music comes from its salvage_music tag; a save loaded with an earlier build may still hold
        // the music set that build put in its memory, which would win over the tag
        MemoryAPI stationMemory = station.getMemoryWithoutUpdate();
        if (OLD_STATION_MUSIC.equals(stationMemory.getString(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY))) {
            stationMemory.unset(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY);
        }
    }

    public static PersonAPI getOrCreateArchivist() {
        PersonAPI person = Global.getSector().getImportantPeople().getPerson(ARCHIVIST_ID);
        if (person != null) return person;
        person = Global.getFactory().createPerson();
        person.setId(ARCHIVIST_ID);
        person.setFaction(Factions.INDEPENDENT);
        person.setName(new FullName("Cecilia", "Corabelle", FullName.Gender.FEMALE));
        person.setRankId("rsg_archivist");
        person.setPostId(Ranks.POST_SCIENTIST);
        person.setPortraitSprite("graphics/portraits/portrait_league07.png");
        Global.getSector().getImportantPeople().addPerson(person);
        return person;
    }

    public static SectorEntityToken getStation() {
        Object station = Global.getSector().getMemoryWithoutUpdate().get(rsg_GeminiStationGenerator.STATION_KEY);
        return station instanceof SectorEntityToken ? (SectorEntityToken) station : null;
    }

    /** RAT's conditions for showing the event at this bar, plus a per-market roll standing in for its frequency. */
    public static boolean canShowAt(MarketAPI market) {
        if (market == null || market.isHidden()) return false;
        if (Factions.PIRATES.equals(market.getFactionId())) return false;
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(ACCEPTED_KEY)) return false;
        SectorEntityToken station = getStation();
        if (station == null || station.isExpired() || !station.isDiscoverable()) return false;

        long window = (long) (Global.getSector().getClock().getElapsedDaysSince(0L) / WINDOW_DAYS);
        long seed = Global.getSector().getSeedString().hashCode() * 31L + market.getId().hashCode() * 17L + window;
        return new Random(seed).nextFloat() < SHOW_CHANCE;
    }

    /** Accepting: the intel, printed into the dialog, and the flag that stops the event being offered again. */
    public static void accept(InteractionDialogAPI dialog) {
        SectorEntityToken station = getStation();
        if (station == null) return;
        rsg_GeminiLeadIntel intel = new rsg_GeminiLeadIntel(station, getOrCreateArchivist());
        intel.setImportant(true);
        Global.getSector().addScript(intel);
        Global.getSector().getIntelManager().addIntel(intel, false, dialog != null ? dialog.getTextPanel() : null);
        Global.getSector().getMemoryWithoutUpdate().set(ACCEPTED_KEY, true);
    }
}
