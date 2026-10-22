package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

/**
 * The second officer's portrait and level, shown with a vanilla officer portrait. Either a small overlay in the
 * portrait's corner, or (side by side) a copy of the vanilla look: tinted portrait, level in the tint color and a
 * border of the tint darkened halfway to black.
 */
public class rsg_PipPortrait extends BaseCustomUIPanelPlugin {

    private final PersonAPI person;
    private final float inset;
    private final Color tint;
    private final boolean sideBySide;
    private PositionAPI pos;
    private TooltipMakerAPI element;
    // while the mouse is over the overlay, the vanilla portrait shows the second officer's tooltip; this holds
    // the captain's tooltip to put back afterwards
    private boolean tooltipSwapped = false;
    private Object savedTooltip;
    // mouse position last frame, to tell whether the mouse came onto the overlay from the rest of the portrait
    private float lastMouseX = -1f, lastMouseY = -1f;

    /**
     * @param inset gap between the panel edge and the portrait
     * @param tint  the vanilla portrait's color, or null if it has none
     */
    public static CustomPanelAPI create(PersonAPI person, float width, float height, float inset, Color tint, boolean sideBySide) {
        rsg_PipPortrait plugin = new rsg_PipPortrait(person, inset, tint, sideBySide);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, plugin);
        TooltipMakerAPI levelText = panel.createUIElement(width - 2f * inset - 2f, 12f, false);
        // an empty seat (person null) shows only the default portrait, like vanilla's empty officer portrait
        if (person != null) {
            levelText.setParaFont(Fonts.VICTOR_10);
            LabelAPI level = levelText.addPara("" + person.getStats().getLevel(), tint != null ? tint : getBrightColor(), 0f);
            level.setAlignment(Alignment.RMID);
        }
        panel.addUIElement(levelText).inTR(2f + inset, -2f + inset);
        plugin.element = levelText;
        return panel;
    }

    /** A tooltip maker to call addTooltipTo on; it can attach tooltips to any component. */
    public TooltipMakerAPI getElement() {
        return element;
    }

    public boolean isTooltipSwapped() {
        return tooltipSwapped;
    }

    public Object getSavedTooltip() {
        return savedTooltip;
    }

    public float getLastMouseX() {
        return lastMouseX;
    }

    public float getLastMouseY() {
        return lastMouseY;
    }

    public void setLastMouse(float x, float y) {
        lastMouseX = x;
        lastMouseY = y;
    }

    public void setTooltipSwapped(boolean swapped, Object saved) {
        this.tooltipSwapped = swapped;
        this.savedTooltip = saved;
    }

    private rsg_PipPortrait(PersonAPI person, float inset, Color tint, boolean sideBySide) {
        this.person = person;
        this.inset = inset;
        this.tint = tint;
        this.sideBySide = sideBySide;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) return;
        float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
        // the overlay sits on top of another portrait, so give it a backing to separate the two
        if (!sideBySide) rsg_Draw.quad(x, y, w, h, Color.black, alphaMult, false);
        String sprite = person != null ? person.getPortraitSprite() : Global.getSettings().getSpriteName("misc", "default_portrait");
        rsg_Draw.portrait(sprite, x + inset, y + inset, w - 2f * inset, h - 2f * inset, alphaMult, tint);
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null) return;
        Color border;
        if (sideBySide) {
            Color base = tint != null ? tint : Global.getSector().getPlayerFaction().getBaseUIColor();
            border = rsg_Draw.lerp(base, Color.black, 0.5f);
        } else {
            border = tint != null ? tint : getBrightColor();
        }
        rsg_Draw.border(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), border, alphaMult);
    }

    private static Color getBrightColor() {
        return Global.getSector().getPlayerFaction().getBrightUIColor();
    }
}
