package data.scripts.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.officers.rsg_SecondOfficerPicker;
import data.scripts.officers.rsg_SecondOfficers;
import data.scripts.ui.rsg_SecondOfficerSlot;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.ref.MethodRef;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;
import patchlib.api.store.PatchData;

import java.util.List;

/**
 * Adds the second officer slot to the refit screen's officer box (CR bar, captain portrait and in-space refit CR
 * readout). The box is an obfuscated class, matched by its stable method names. The slot goes right of the
 * vanilla portrait, and the CR readout that normally sits there moves right of the slot.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.coreui.refit.oOOoOOO... (a 256-character o/O name), placed by the
// refit panel com.fs.starfarer.coreui.refit.U. Both names will change; the package and the method names matched
// below (showCaptainPickerDialog, showPersonalityDialog, getCRBar) should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui.refit",
        methodMatches = {
                @MethodMatch(methodName = "showCaptainPickerDialog", parameterCount = 1),
                @MethodMatch(methodName = "showPersonalityDialog", parameterCount = 0),
                @MethodMatch(methodName = "getCRBar", parameterCount = 0)
        }))
public class rsg_RefitOfficerPatch {

    private static final Logger log = Global.getLogger(rsg_RefitOfficerPatch.class);
    private static final float GAP = 10f;
    private static final String KEY_SLOT = "rsg_second_officer_slot";

    // the vanilla captain portrait is the box's only button field
    private static final FieldQuerySpec PORTRAIT = FieldQuery.create().subtype(ButtonAPI.class).build();
    // the ship being refit; the box's only FleetMember field
    private static final FieldQuerySpec MEMBER = FieldQuery.create().typeName("com.fs.starfarer.campaign.fleet.FleetMember").build();
    private static final MethodQuerySpec SHOW_PICKER = MethodQuery.create().methodName("showCaptainPickerDialog").parameterCount(1).build();
    private static final MethodQuerySpec GET_PARENT = MethodQuery.create().methodName("getParent").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();
    // the in-space refit CR readout is the only child with this method
    private static final MethodQuerySpec SET_FLIPPED = MethodQuery.create().methodName("setFlipped").parameterCount(2).build();
    private static final MethodQuerySpec RECREATE_UI = MethodQuery.create().methodName("recreateUI").parameterCount(0).build();
    private static final MethodQuerySpec IS_EDITED_SINCE_SAVE = MethodQuery.create().methodName("isEditedSinceSave").parameterCount(0).build();
    private static final MethodQuerySpec IS_EDITED_SINCE_LOAD = MethodQuery.create().methodName("isEditedSinceLoad").parameterCount(0).build();
    private static final MethodQuerySpec SET_EDITED_SINCE_SAVE = MethodQuery.create().methodName("setEditedSinceSave").parameterCount(1).build();
    private static final MethodQuerySpec SET_EDITED_SINCE_LOAD = MethodQuery.create().methodName("setEditedSinceLoad").parameterCount(1).build();

    /** The box clears and rebuilds its children here, so any slot added before is gone. */
    @After(target = @MethodMatch(methodName = "setFleetMember", parameterCount = 1))
    public static void afterSetFleetMember(AfterContext ctx) {
        try {
            ctx.getData().remove(KEY_SLOT);
            Object arg = ctx.getArg(0);
            if (arg instanceof FleetMemberAPI) updateSlot(ctx, (FleetMemberAPI) arg, ((FleetMemberAPI) arg).getVariant());
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't add the second officer slot to the refit screen", t);
        }
    }

    /**
     * Called with the variant being edited every time the refit screen changes, so adding or removing the hullmod
     * shows or hides the slot right away.
     */
    @After(target = @MethodMatch(methodName = "syncWithVariant", parameterCount = 1))
    public static void afterSyncWithVariant(AfterContext ctx) {
        try {
            if (!ctx.hasField(MEMBER)) return;
            Object member = ctx.getField(MEMBER).get();
            Object variant = ctx.getArg(0);
            if (member instanceof FleetMemberAPI) {
                updateSlot(ctx, (FleetMemberAPI) member, variant instanceof ShipVariantAPI ? (ShipVariantAPI) variant : null);
            }
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't update the second officer slot", t);
        }
    }

    /** The picker closing reports back here; apply the second officer choice before vanilla refreshes the screen. */
    @Before(target = @MethodMatch(methodName = "dialogDismissed", parameterCount = 2))
    public static void beforeDialogDismissed(BeforeContext ctx) {
        try {
            rsg_SecondOfficerPicker.onDismissed();
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't apply the second officer choice", t);
        }
    }

    /** Adds or removes the slot so it matches whether the variant on screen has the hullmod. */
    private static void updateSlot(AfterContext ctx, FleetMemberAPI member, ShipVariantAPI variant) {
        if (Global.getCurrentState() == GameState.CAMPAIGN) rsg_SecondOfficers.setRefitVariant(member, variant);
        if (!ctx.hasField(PORTRAIT)) return;
        ButtonAPI portrait = ctx.<ButtonAPI>getField(PORTRAIT).get();
        if (portrait == null) return;
        UIPanelAPI box = (UIPanelAPI) ctx.getSelf();
        PatchData data = ctx.getData();
        UIComponentAPI slot = data.get(KEY_SLOT);

        boolean wanted = Global.getCurrentState() == GameState.CAMPAIGN
                && !member.isFighterWing()
                && variant != null && variant.hasHullMod(rsg_SecondOfficers.HULLMOD_ID)
                && member.getFleetData() != null && member.getFleetData() == rsg_SecondOfficers.getPlayerFleetData()
                && ctx.hasMethod(SHOW_PICKER);
        if (wanted == (slot != null)) return;

        if (slot != null) {
            box.removeComponent(slot);
            data.remove(KEY_SLOT);
            moveReadout(ctx, slot, portrait);
            return;
        }

        MethodRef showPicker = ctx.getMethod(SHOW_PICKER);
        Runnable refresh = createRefresh(ctx, member);
        CustomPanelAPI created = rsg_SecondOfficerSlot.create(member, portrait.getPosition().getWidth(),
                () -> rsg_SecondOfficerPicker.open(member, showPicker),
                () -> {
                    rsg_SecondOfficers.clear(member);
                    refresh.run();
                });
        box.addComponent(created).rightOfTop(portrait, GAP);
        data.putWeak(KEY_SLOT, created);
        moveReadout(ctx, created, created);
    }

    /** Puts the in-space refit CR readout right of the given anchor (the slot, or the vanilla portrait again). */
    private static void moveReadout(AfterContext ctx, UIComponentAPI slot, UIComponentAPI anchor) {
        if (!ctx.hasMethod(GET_CHILDREN)) return;
        List<?> children = ctx.getMethod(GET_CHILDREN).call();
        for (Object child : children) {
            if (child != slot && child instanceof UIComponentAPI && ctx.hasMethod(SET_FLIPPED, child)) {
                ((UIComponentAPI) child).getPosition().rightOfTop(anchor, GAP);
            }
        }
    }

    /** Rebuilds the refit screen the same way vanilla does after the captain changes, keeping its "edited" flags. */
    private static Runnable createRefresh(AfterContext ctx, FleetMemberAPI member) {
        Object refitPanel = ctx.hasMethod(GET_PARENT) ? ctx.getMethod(GET_PARENT).call() : null;
        if (refitPanel == null || !ctx.hasMethod(RECREATE_UI, refitPanel)
                || !ctx.hasMethod(IS_EDITED_SINCE_SAVE, refitPanel) || !ctx.hasMethod(SET_EDITED_SINCE_SAVE, refitPanel)
                || !ctx.hasMethod(IS_EDITED_SINCE_LOAD, refitPanel) || !ctx.hasMethod(SET_EDITED_SINCE_LOAD, refitPanel)) {
            return member::updateStats;
        }
        MethodRef recreate = ctx.getMethod(RECREATE_UI, refitPanel);
        MethodRef isEditedSinceSave = ctx.getMethod(IS_EDITED_SINCE_SAVE, refitPanel);
        MethodRef isEditedSinceLoad = ctx.getMethod(IS_EDITED_SINCE_LOAD, refitPanel);
        MethodRef setEditedSinceSave = ctx.getMethod(SET_EDITED_SINCE_SAVE, refitPanel);
        MethodRef setEditedSinceLoad = ctx.getMethod(SET_EDITED_SINCE_LOAD, refitPanel);
        return () -> {
            Object save = isEditedSinceSave.call();
            Object load = isEditedSinceLoad.call();
            member.updateStats();
            recreate.call();
            setEditedSinceSave.call(save);
            setEditedSinceLoad.call(load);
        };
    }
}
