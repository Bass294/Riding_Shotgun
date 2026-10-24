package data.scripts.officers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import patchlib.api.ref.MethodRef;

/**
 * Lets the vanilla captain picker choose a second officer. The picker is opened for a stand-in fleet member whose
 * captain is the current second officer; whoever the picker makes its captain becomes the new second officer.
 * The stand-in never joins a fleet, so the picker's own swap logic (taking an officer off another ship and giving
 * that ship the previous occupant) works unchanged on the real ships.
 */
public class rsg_SecondOfficerPicker {

    private static FleetMemberAPI standIn;
    private static FleetMemberAPI target;
    private static PersonAPI original;

    /** Opens the picker through the refit officer box's own showCaptainPickerDialog(FleetMember). */
    public static void open(FleetMemberAPI member, MethodRef showCaptainPickerDialog) {
        FleetMemberAPI dummy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, member.getVariant());
        dummy.setShipName(member.getShipName());
        PersonAPI current = rsg_SecondOfficers.get(member);
        dummy.setCaptain(current != null ? current : Global.getFactory().createPerson());

        standIn = dummy;
        target = member;
        original = current;
        try {
            showCaptainPickerDialog.call(dummy);
        } catch (RuntimeException e) {
            reset();
            throw e;
        }
    }

    public static boolean isOpen() {
        return standIn != null;
    }

    /** The stand-in if this officer is its captain, i.e. the officer currently in the seat being picked for. */
    public static FleetMemberAPI getStandInFor(PersonAPI person) {
        return standIn != null && person != null && standIn.getCaptain() == person ? standIn : null;
    }

    public static boolean isStandIn(Object member) {
        return member != null && member == standIn;
    }

    /**
     * The ship whose second seat this officer is in. While the picker is open this follows the picker's current
     * choice, since the picker stays open after a click and the choice is only saved when it closes.
     */
    public static FleetMemberAPI getSecondSeatOf(PersonAPI person) {
        if (standIn != null) {
            if (standIn.getCaptain() == person) return target;
            if (person == original) return null;
        }
        return rsg_SecondOfficers.getMemberWithSecondOfficer(person);
    }

    /**
     * Applies the picker's choice. Called when the refit officer box is told the dialog closed. For AI cores the
     * picker has already moved the cores themselves (the new one out of cargo or off its ship, the old one back), so
     * only the seat changes here.
     */
    public static void onDismissed() {
        if (standIn == null) return;
        PersonAPI picked = standIn.getCaptain();
        FleetMemberAPI member = target;
        PersonAPI previous = original;
        reset();

        if (picked == null || picked.isDefault()) {
            if (previous != null) rsg_SecondOfficers.release(member);
            return;
        }
        if (picked == previous) return;
        String error = rsg_SecondOfficers.set(member, picked);
        if (error != null) {
            if (picked.isAICore()) {
                // the picker took the core and gave the old one back already; don't let either go missing
                rsg_SecondOfficers.release(member);
                rsg_SecondOfficers.returnCoreToCargo(picked);
            }
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(error, Misc.getNegativeHighlightColor());
        }
    }

    private static void reset() {
        standIn = null;
        target = null;
        original = null;
    }
}
