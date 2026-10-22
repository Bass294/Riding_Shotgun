package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficerPicker;
import org.apache.log4j.Logger;
import patchlib.api.context.BeforeContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

/**
 * One officer row in the vanilla officer picker. Second officers are shown as assigned
 * ("..., second officer of the X") instead of "unassigned", and when the picker was opened for a second seat,
 * the player and AI cores can't be picked.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.coreui.ooo0, the row class of CaptainPickerDialog. The class name
// will change; the package and the method names matched below should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = {
                @MethodMatch(methodName = "updateStatusString", parameterCount = 0),
                @MethodMatch(methodName = "isPastMax", parameterCount = 0),
                @MethodMatch(methodName = "getOfficerData", parameterCount = 0)
        }))
public class rsg_OfficerRowPatch {

    private static final Logger log = Global.getLogger(rsg_OfficerRowPatch.class);
    private static final String ASSIGNED = ", assigned to the ";
    private static final String SECOND = ", second officer of the ";

    private static final MethodQuerySpec GET_PERSON = MethodQuery.create().methodName("getPerson").parameterCount(0).build();
    // the ship the picker was opened for; the row's only FleetMember field
    private static final FieldQuerySpec MEMBER = FieldQuery.create().typeName("com.fs.starfarer.campaign.fleet.FleetMember").build();

    /** The status line looks up the officer's ship; for second officers, answer with the ship they serve on. */
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "updateStatusString", parameterCount = 0),
            call = @MethodMatch(methodName = "getMemberWithCaptain", parameterCount = 1),
            owner = @ClassMatch(subtype = FleetDataAPI.class))
    public static void redirectMemberWithCaptain(MethodCallContext ctx) {
        Object result = ctx.call();
        if (result == null) {
            try {
                Object person = ctx.getCallArg(0);
                if (person instanceof PersonAPI) result = rsg_SecondOfficerPicker.getSecondSeatOf((PersonAPI) person);
            } catch (Throwable t) {
                log.error("Riding Shotgun: couldn't look up a second officer's ship", t);
            }
        }
        ctx.setResult(result);
    }

    /** Rewords "assigned to the X" for second officers. */
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "updateStatusString", parameterCount = 0),
            call = @MethodMatch(methodName = "setText", parameterCount = 1),
            owner = @ClassMatch(subtype = LabelAPI.class))
    public static void redirectSetText(MethodCallContext ctx) {
        try {
            Object text = ctx.getCallArg(0);
            if (text instanceof String && ((String) text).contains(ASSIGNED) && ctx.hasMethod(GET_PERSON)) {
                Object person = ctx.getMethod(GET_PERSON).call();
                if (person instanceof PersonAPI && rsg_SecondOfficerPicker.getSecondSeatOf((PersonAPI) person) != null) {
                    ctx.setCallArg(0, ((String) text).replace(ASSIGNED, SECOND));
                }
            }
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't reword a second officer's status", t);
        }
        ctx.call();
    }

    /**
     * Picking yourself makes vanilla move your flagship onto the picked ship through that ship's fleet, which the
     * stand-in doesn't have (and AI cores aren't officers), so refuse both while picking a second officer.
     */
    @Before(target = @MethodMatch(methodName = "actionPerformed", parameterCount = 2))
    public static void beforeActionPerformed(BeforeContext ctx) {
        try {
            // another mod's earlier hook already skipped this click
            if (ctx.isSkipOriginal()) return;
            if (!rsg_SecondOfficerPicker.isOpen() || !ctx.hasField(MEMBER) || !ctx.hasMethod(GET_PERSON)) return;
            if (!rsg_SecondOfficerPicker.isStandIn(ctx.getField(MEMBER).get())) return;
            Object person = ctx.getMethod(GET_PERSON).call();
            if (!(person instanceof PersonAPI)) return;
            PersonAPI p = (PersonAPI) person;
            if (p.isPlayer() || p.isAICore()) {
                Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                        p.isPlayer() ? "You can't serve as a second officer" : "AI cores can't serve as second officers",
                        Misc.getNegativeHighlightColor());
                Global.getSoundPlayer().playUISound("ui_button_disabled_pressed", 1f, 1f);
                ctx.skipOriginal(null);
            }
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't check the second officer pick", t);
        }
    }
}
