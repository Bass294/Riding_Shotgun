package data.scripts.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The archivist's lead to Research Station Gemini, structured like Random Assortment of Things' archivist intel:
 * points at the station, confirms it once the player discovers it (with a little reputation for the archivist),
 * and goes away when the station is gone or the player removes it.
 */
public class rsg_GeminiLeadIntel extends BaseIntelPlugin {

    private static final String ICON = "graphics/icons/intel/discovered_entity.png";
    private static final String BUTTON_REMOVE = "rsg_remove";
    private static final float FOUND_REP = 0.1f;

    private final SectorEntityToken station;
    private final PersonAPI archivist;
    private final StarSystemAPI system;
    private boolean found = false;
    private boolean remove = false;

    public rsg_GeminiLeadIntel(SectorEntityToken station, PersonAPI archivist) {
        this.station = station;
        this.archivist = archivist;
        this.system = station.getStarSystem();
    }

    @Override
    protected String getName() {
        return "Research Station Gemini";
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        info.addSpacer(2f);
        info.addPara(station.getName(), 0f, Misc.getGrayColor(), Misc.getHighlightColor(), station.getName());
        info.addPara("In " + system.getName(), 0f, Misc.getGrayColor(), Misc.getHighlightColor(), system.getName());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addSpacer(10f);
        TooltipMakerAPI img = info.beginImageWithText(archivist.getPortraitSprite(), 64f);
        img.addPara(archivist.getNameString() + " has asked you to confirm that " + station.getName() + " still exists in the "
                        + system.getNameWithNoType() + " system. There is no payment, and no condition on what you do with the station.",
                0f, Misc.getTextColor(), Misc.getHighlightColor(),
                archivist.getNameString(), station.getName(), system.getNameWithNoType());
        info.addImageWithText(0f);

        info.addSpacer(5f);
        info.addRelationshipBar(archivist, width, 0f);
        info.addSpacer(10f);

        if (found) {
            info.addPara("The station has been confirmed to be in the system.", Misc.getGrayColor(), 0f);
        } else {
            info.addPara("The archivist had no location data beyond the system itself.", Misc.getGrayColor(), 0f);
        }

        ButtonAPI delete = addGenericButton(info, width, "Remove Intel", BUTTON_REMOVE);
        delete.setShortcut(Keyboard.KEY_G, true);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_REMOVE.equals(buttonId)) {
            remove = true;
            ui.recreateIntelUI();
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!found && !station.isDiscoverable()) {
            found = true;
            Global.getSector().getCampaignUI().addMessage("Confirmed the existence of " + station.getName(),
                    Misc.getTextColor(), station.getName(), null, Misc.getHighlightColor(), null);
            archivist.getRelToPlayer().adjustRelationship(FOUND_REP, null);
        }
        if (station.isExpired()) remove = true;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return remove;
    }

    @Override
    public void reportRemovedIntel() {
        super.reportRemovedIntel();
        Global.getSector().removeScript(this);
    }

    @Override
    public String getIcon() {
        return ICON;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        tags.add(Tags.INTEL_EXPLORATION);
        tags.add(Tags.INTEL_ACCEPTED);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (!station.isExpired()) return station;
        return system.getCenter();
    }
}
