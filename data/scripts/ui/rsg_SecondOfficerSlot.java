package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficers;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.List;

/**
 * The second officer portrait in the refit screen's officer box. Works like the vanilla portrait next to it:
 * left-click opens the officer picker, right-click removes the officer.
 */
public class rsg_SecondOfficerSlot extends BaseCustomUIPanelPlugin {

    private static final Logger log = Global.getLogger(rsg_SecondOfficerSlot.class);

    // vanilla portrait buttons glow up to this much on hover
    private static final float HIGHLIGHT_BRIGHTNESS = 0.8f;

    private final PersonAPI second;
    private final Runnable onAssign;
    private final Runnable onRemove;
    private final FaderUtil hover = new FaderUtil(0f, 0.05f, 0.25f);
    private PositionAPI pos;
    private boolean hovered = false;
    private int pressed = -1;

    public static CustomPanelAPI create(FleetMemberAPI member, float size, Runnable onAssign, Runnable onRemove) {
        PersonAPI second = rsg_SecondOfficers.get(member);
        CustomPanelAPI panel = Global.getSettings().createCustom(size, size, new rsg_SecondOfficerSlot(second, onAssign, onRemove));
        FactionAPI faction = Global.getSector().getPlayerFaction();

        // an empty seat shows just the default portrait, like vanilla's
        TooltipMakerAPI text = panel.createUIElement(size - 6f, 14f, false);
        if (second != null) {
            text.setParaFont(Fonts.VICTOR_10);
            LabelAPI level = text.addPara("level " + second.getStats().getLevel(), faction.getBrightUIColor(), 0f);
            level.setAlignment(Alignment.RMID);
        }
        panel.addUIElement(text).inTR(3f, 1f);
        // attach to the whole slot, so hovering anywhere on it shows the tooltip
        text.addTooltipTo(new rsg_SecondOfficerTooltip(member), panel, TooltipMakerAPI.TooltipLocation.BELOW);
        return panel;
    }

    private rsg_SecondOfficerSlot(PersonAPI second, Runnable onAssign, Runnable onRemove) {
        this.second = second;
        this.onAssign = onAssign;
        this.onRemove = onRemove;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }

    @Override
    public void advance(float amount) {
        if (hovered) hover.fadeIn();
        else hover.fadeOut();
        hover.advance(amount);
    }

    private String getSprite() {
        return second != null ? second.getPortraitSprite() : rsg_Draw.getEmptySeatSprite();
    }

    private Color getTint() {
        return second != null ? null : rsg_Draw.getEmptySeatTint();
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) return;
        float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
        rsg_Draw.portrait(getSprite(), x + 1f, y + 1f, w - 2f, h - 2f, alphaMult, getTint());
    }

    /** Same as the vanilla portrait: the portrait redrawn additively on hover, and a fixed border that doesn't light up. */
    @Override
    public void render(float alphaMult) {
        if (pos == null) return;
        float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
        float glow = hover.getBrightness() * HIGHLIGHT_BRIGHTNESS;
        if (glow > 0f) {
            rsg_Draw.portraitAdditive(getSprite(), x + 1f, y + 1f, w - 2f, h - 2f, alphaMult * glow * 0.25f, getTint());
        }
        Color border = rsg_Draw.lerp(Global.getSettings().getColor("widgetBorderColorDark"), Color.black, 0.5f);
        rsg_Draw.border(x, y, w, h, border, alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isMouseMoveEvent()) {
                boolean inside = pos.containsEvent(e);
                if (inside && !hovered) Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f);
                hovered = inside;
            } else if ((e.isLMBUpEvent() || e.isRMBUpEvent()) && pressed >= 0) {
                // swallow the release of a press this slot acted on
                int button = e.isLMBUpEvent() ? 0 : 1;
                if (button != pressed) continue;
                pressed = -1;
                e.consume();
            } else if ((e.isLMBDownEvent() || e.isRMBDownEvent()) && pos.containsEvent(e)) {
                // like the vanilla portrait button, act on the press rather than the release
                int button = e.isLMBDownEvent() ? 0 : 1;
                pressed = button;
                e.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                if (button == 1 && second == null) return;
                // both actions can rebuild the refit screen, which removes this panel, so stop handling input here
                run(button == 0 ? onAssign : onRemove);
                return;
            }
        }
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            log.error("Riding Shotgun: second officer slot action failed", t);
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Couldn't change the co-pilot, see starsector.log", Misc.getNegativeHighlightColor());
        }
    }
}
