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
    public static void afterSetFleetMember(AfterContext context) {
        // a box subclass calling super would rebuild the slot twice
        if (!context.isMostDerivedCall()) return;
        context.getData().remove(KEY_SLOT);
        Object arg = context.getArg(0);
        // right-clicking the vanilla portrait empties the captain's seat and rebuilds this box; fill it with the
        // second officer before the slot is drawn (the vanilla portrait follows the captain by itself)
        if (arg instanceof FleetMemberAPI && Global.getCurrentState() == GameState.CAMPAIGN) {
            rsg_SecondOfficers.promoteIfCaptainless((FleetMemberAPI) arg);
        }
        if (arg instanceof FleetMemberAPI) updateSlot(context, (FleetMemberAPI) arg, ((FleetMemberAPI) arg).getVariant());
    }

    /**
     * Called with the variant being edited every time the refit screen changes, so adding or removing the hullmod
     * shows or hides the slot right away.
     */
    @After(target = @MethodMatch(methodName = "syncWithVariant", parameterCount = 1))
    public static void afterSyncWithVariant(AfterContext context) {
        if (!context.isMostDerivedCall()) return;
        if (!context.hasField(MEMBER)) return;
        Object member = context.getField(MEMBER).get();
        Object variant = context.getArg(0);
        if (member instanceof FleetMemberAPI) {
            updateSlot(context, (FleetMemberAPI) member, variant instanceof ShipVariantAPI ? (ShipVariantAPI) variant : null);
        }
    }

    /** The picker closing reports back here; apply the second officer choice before vanilla refreshes the screen. */
    @Before(target = @MethodMatch(methodName = "dialogDismissed", parameterCount = 2))
    public static void beforeDialogDismissed(BeforeContext context) {
        if (!context.isMostDerivedCall()) return;
        rsg_SecondOfficerPicker.onDismissed();
    }

    /** Adds or removes the slot so it matches whether the variant on screen has the hullmod. */
    private static void updateSlot(AfterContext context, FleetMemberAPI member, ShipVariantAPI variant) {
        if (Global.getCurrentState() == GameState.CAMPAIGN) rsg_SecondOfficers.setRefitVariant(member, variant);
        if (!context.hasField(PORTRAIT)) return;
        ButtonAPI portrait = context.<ButtonAPI>getField(PORTRAIT).get();
        if (portrait == null) return;
        UIPanelAPI box = (UIPanelAPI) context.getSelf();
        PatchData data = context.getData();
        UIComponentAPI slot = data.get(KEY_SLOT);

        boolean wanted = Global.getCurrentState() == GameState.CAMPAIGN
                && !member.isFighterWing()
                && variant != null && variant.hasHullMod(rsg_SecondOfficers.HULLMOD_ID)
                && rsg_SecondOfficers.isInPlayerFleet(member, rsg_SecondOfficers.getPlayerFleetData())
                && context.hasMethod(SHOW_PICKER);
        if (wanted == (slot != null)) return;

        if (slot != null) {
            box.removeComponent(slot);
            data.remove(KEY_SLOT);
            moveReadout(context, slot, portrait);
            return;
        }

        MethodRef showPicker = context.getMethod(SHOW_PICKER);
        Runnable refresh = createRefresh(context, member);
        CustomPanelAPI created = rsg_SecondOfficerSlot.create(member, portrait.getPosition().getWidth(),
                () -> rsg_SecondOfficerPicker.open(member, showPicker),
                () -> {
                    rsg_SecondOfficers.clear(member);
                    refresh.run();
                });
        box.addComponent(created).rightOfTop(portrait, GAP);
        data.putWeak(KEY_SLOT, created);
        moveReadout(context, created, created);
    }

    /** Puts the in-space refit CR readout right of the given anchor (the slot, or the vanilla portrait again). */
    private static void moveReadout(AfterContext context, UIComponentAPI slot, UIComponentAPI anchor) {
        if (!context.hasMethod(GET_CHILDREN)) return;
        List<?> children = context.getMethod(GET_CHILDREN).call();
        for (Object child : children) {
            if (child != slot && child instanceof UIComponentAPI && context.hasMethod(SET_FLIPPED, child)) {
                ((UIComponentAPI) child).getPosition().rightOfTop(anchor, GAP);
            }
        }
    }

    /** Rebuilds the refit screen the same way vanilla does after the captain changes, keeping its "edited" flags. */
    private static Runnable createRefresh(AfterContext context, FleetMemberAPI member) {
        Object refitPanel = context.hasMethod(GET_PARENT) ? context.getMethod(GET_PARENT).call() : null;
        if (refitPanel == null || !context.hasMethod(RECREATE_UI, refitPanel)
                || !context.hasMethod(IS_EDITED_SINCE_SAVE, refitPanel) || !context.hasMethod(SET_EDITED_SINCE_SAVE, refitPanel)
                || !context.hasMethod(IS_EDITED_SINCE_LOAD, refitPanel) || !context.hasMethod(SET_EDITED_SINCE_LOAD, refitPanel)) {
            return member::updateStats;
        }
        MethodRef recreate = context.getMethod(RECREATE_UI, refitPanel);
        MethodRef isEditedSinceSave = context.getMethod(IS_EDITED_SINCE_SAVE, refitPanel);
        MethodRef isEditedSinceLoad = context.getMethod(IS_EDITED_SINCE_LOAD, refitPanel);
        MethodRef setEditedSinceSave = context.getMethod(SET_EDITED_SINCE_SAVE, refitPanel);
        MethodRef setEditedSinceLoad = context.getMethod(SET_EDITED_SINCE_LOAD, refitPanel);
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
