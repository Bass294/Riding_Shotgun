package data.scripts.patches;

import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.officers.rsg_SecondOfficerPicker;
import patchlib.api.context.ConstructorCallContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectConstructorCall;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

/** The vanilla officer picker, when opened for a co-pilot seat: retitles it and highlights the current co-pilot. */
// 0.98a-RC8 Windows: the title label constructed here is com.fs.starfarer.ui.d (implements LabelAPI); its
// class name will change, which is why the redirect matches it by LabelAPI and constructor arity only.
@Patch(target = @ClassMatch(typeName = "com.fs.starfarer.coreui.CaptainPickerDialog"))
public class rsg_CaptainPickerPatch {

    private static final String VANILLA_TITLE = "Select captain for the ";
    private static final String TITLE = "Select co-pilot for the ";

    // the ship the picker was opened for; the dialog's only FleetMember field
    private static final FieldQuerySpec MEMBER = FieldQuery.create().typeName("com.fs.starfarer.campaign.fleet.FleetMember").build();

    @RedirectConstructorCall(
            target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2),
            type = @ClassMatch(subtype = LabelAPI.class),
            constructor = @MethodMatch(parameterCount = 5))
    public static void redirectTitleLabel(ConstructorCallContext context) {
        Object text = context.getCallArg(0);
        if (text instanceof String && ((String) text).startsWith(VANILLA_TITLE)
                && rsg_SecondOfficerPicker.isOpen() && context.hasField(MEMBER)
                && rsg_SecondOfficerPicker.isStandIn(context.getField(MEMBER).get())) {
            context.setCallArg(0, TITLE + ((String) text).substring(VANILLA_TITLE.length()));
        }
        context.setResult(context.call());
    }

    /**
     * Building its rows, the picker highlights the officer whose ship (getMemberWithCaptain) is the ship it was
     * opened for. The stand-in isn't in the fleet, so answer with it for the current co-pilot and vanilla's own
     * check highlights that row.
     */
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2),
            call = @MethodMatch(methodName = "getMemberWithCaptain", parameterCount = 1),
            owner = @ClassMatch(subtype = FleetDataAPI.class))
    public static void redirectCurrentOfficer(MethodCallContext context) {
        Object result = context.call();
        Object person = context.getCallArg(0);
        if (result == null && person instanceof PersonAPI) {
            FleetMemberAPI standIn = rsg_SecondOfficerPicker.getStandInFor((PersonAPI) person);
            if (standIn != null) result = standIn;
        }
        context.setResult(result);
    }
}
