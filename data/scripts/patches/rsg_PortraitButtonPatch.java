package data.scripts.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.officers.rsg_SecondOfficerPicker;
import data.scripts.officers.rsg_SecondOfficers;
import org.apache.log4j.Logger;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;

/**
 * Makes the second officer overlay on officer portraits clickable. The portrait is drawn inside a vanilla button,
 * which doesn't pass input on to what it draws, so the overlay can't take clicks itself. Instead, when the button
 * is pressed with the mouse over the overlay, the click goes to the second seat: left-click opens the officer
 * picker for it (through the button listener's own showCaptainPickerDialog, as on the fleet screen's ship panel),
 * right-click removes the second officer. Clicks elsewhere on the portrait behave as vanilla.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.ui.n, the game's standard button (implements ButtonAPI); the portrait
// it draws is com.fs.starfarer.coreui.OOoo. Both names will change; ButtonAPI, the package and the method names
// matched below should not.
@Patch(target = @ClassMatch(
        subtype = ButtonAPI.class,
        packageName = "com.fs.starfarer.ui",
        methodMatches = {
                @MethodMatch(methodName = "buttonPressed", parameterCount = 2),
                @MethodMatch(methodName = "getButtonPanel", parameterCount = 0),
                @MethodMatch(methodName = "getRendererPanel", parameterCount = 0)
        }))
public class rsg_PortraitButtonPatch {

    private static final Logger log = Global.getLogger(rsg_PortraitButtonPatch.class);

    private static final MethodQuerySpec GET_BUTTON_PANEL = MethodQuery.create().methodName("getButtonPanel").parameterCount(0).build();
    private static final MethodQuerySpec GET_LISTENER = MethodQuery.create().methodName("getListener").parameterCount(0).build();
    private static final MethodQuerySpec GET_MEMBER = MethodQuery.create().methodName("getMember").parameterCount(0).build();
    private static final MethodQuerySpec SHOW_PICKER = MethodQuery.create().methodName("showCaptainPickerDialog").parameterCount(1).build();

    @Before(target = @MethodMatch(methodName = "buttonPressed", parameterCount = 2))
    public static void beforeButtonPressed(BeforeContext ctx) {
        try {
            // a button subclass calling super would run this twice and open two pickers
            if (!ctx.isMostDerivedCall()) return;
            // another mod's earlier hook already took this press
            if (ctx.isSkipOriginal()) return;
            if (Global.getCurrentState() != GameState.CAMPAIGN || !ctx.hasMethod(GET_BUTTON_PANEL)) return;
            Object portrait = ctx.getMethod(GET_BUTTON_PANEL).call();
            if (portrait == null) return;
            CustomPanelAPI overlay = rsg_PortraitPatch.getOverlay(portrait);
            if (overlay == null || !isMouseOver(overlay)) return;
            Object event = ctx.getArg(0);
            if (!(event instanceof InputEventAPI) || ((InputEventAPI) event).isKeyboardEvent()) return;
            if (!ctx.hasMethod(GET_MEMBER, portrait)) return;
            Object member = ctx.getMethod(GET_MEMBER, portrait).call();
            if (!(member instanceof FleetMemberAPI)) return;
            // only portraits that assign officers (their listener can open the picker); not ones in tooltips etc
            Object listener = ctx.hasMethod(GET_LISTENER) ? ctx.getMethod(GET_LISTENER).call() : null;
            if (listener == null || !ctx.hasMethod(SHOW_PICKER, listener)) return;

            if (((InputEventAPI) event).isRMBEvent()) {
                rsg_SecondOfficers.clear((FleetMemberAPI) member);
            } else {
                rsg_SecondOfficerPicker.open((FleetMemberAPI) member, ctx.getMethod(SHOW_PICKER, listener));
            }
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
            ctx.skipOriginal(null);
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't handle a click on the second officer portrait", t);
        }
    }

    private static boolean isMouseOver(CustomPanelAPI overlay) {
        PositionAPI p = overlay.getPosition();
        float x = Global.getSettings().getMouseX();
        float y = Global.getSettings().getMouseY();
        return x >= p.getX() && x <= p.getX() + p.getWidth() && y >= p.getY() && y <= p.getY() + p.getHeight();
    }
}
