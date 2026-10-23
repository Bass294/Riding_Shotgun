package data.scripts.patches;

import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.officers.rsg_SecondOfficerPicker;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

/**
 * The panel that opens when a ship is clicked on the fleet screen (portrait, mothball, scuttle, refit...). The
 * officer picker reports back to it when it closes; apply a second officer choice made from the portrait overlay.
 * The panel is obfuscated and matched by its stable method names.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.coreui.OOoOOO... (a 256-character o/O name). The class name will
// change; the package and the method names matched below should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = {
                @MethodMatch(methodName = "showCaptainPickerDialog", parameterCount = 1),
                @MethodMatch(methodName = "showScuttleDialog", parameterCount = 1),
                @MethodMatch(methodName = "showBuyConfirmDialog", parameterCount = 1)
        }))
public class rsg_FleetMemberPanelPatch {

    @Before(target = @MethodMatch(methodName = "dialogDismissed", parameterCount = 2))
    public static void beforeDialogDismissed(BeforeContext context) {
        // a panel subclass calling super would apply the choice twice
        if (!context.isMostDerivedCall()) return;
        rsg_SecondOfficerPicker.onDismissed();
    }
}
