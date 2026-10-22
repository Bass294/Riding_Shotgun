package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.officers.rsg_SecondOfficerPicker;
import org.apache.log4j.Logger;
import patchlib.api.context.ConstructorCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectConstructorCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

/** Retitles the vanilla officer picker when it was opened for a second seat. */
// 0.98a-RC8 Windows: the title label constructed here is com.fs.starfarer.ui.d (implements LabelAPI); its
// class name will change, which is why the redirect matches it by LabelAPI and constructor arity only.
@Patch(target = @ClassMatch(typeName = "com.fs.starfarer.coreui.CaptainPickerDialog"))
public class rsg_CaptainPickerPatch {

    private static final Logger log = Global.getLogger(rsg_CaptainPickerPatch.class);
    private static final String VANILLA_TITLE = "Select captain for the ";
    private static final String TITLE = "Select second officer for the ";

    // the ship the picker was opened for; the dialog's only FleetMember field
    private static final FieldQuerySpec MEMBER = FieldQuery.create().typeName("com.fs.starfarer.campaign.fleet.FleetMember").build();

    @RedirectConstructorCall(
            target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2),
            type = @ClassMatch(subtype = LabelAPI.class),
            constructor = @MethodMatch(parameterCount = 5))
    public static void redirectTitleLabel(ConstructorCallContext ctx) {
        try {
            Object text = ctx.getCallArg(0);
            if (text instanceof String && ((String) text).startsWith(VANILLA_TITLE)
                    && rsg_SecondOfficerPicker.isOpen() && ctx.hasField(MEMBER)
                    && rsg_SecondOfficerPicker.isStandIn(ctx.getField(MEMBER).get())) {
                ctx.setCallArg(0, TITLE + ((String) text).substring(VANILLA_TITLE.length()));
            }
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't retitle the officer picker", t);
        }
        ctx.setResult(ctx.call());
    }
}
