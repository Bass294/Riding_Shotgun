package data.scripts.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.officers.rsg_SecondOfficers;
import data.scripts.ui.rsg_PipPortrait;
import data.scripts.ui.rsg_SecondOfficerTooltip;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;
import patchlib.api.store.PatchData;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The vanilla officer portrait (portrait, level and level-up marker), used by the fleet screen's ship cards, the
 * combat ship info and officer tooltips. In combat the second officer's portrait goes to the right of the
 * captain's at the same size; elsewhere it is a small overlay in the bottom-left corner, the one corner vanilla
 * leaves free. Skipped on the refit screen, which has its own second officer slot.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.coreui.OOoo; its parent, which declares getButton(), is
// com.fs.starfarer.ui.m$Oo. Both names will change; the package and the method names matched below should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = {
                @MethodMatch(methodName = "setWithSkillList", parameterCount = 1),
                @MethodMatch(methodName = "canLevelUp", parameterCount = 0),
                @MethodMatch(methodName = "getPortrait", parameterCount = 0),
                @MethodMatch(methodName = "setMember", parameterCount = 1)
        }))
public class rsg_PortraitPatch {

    private static final Logger log = Global.getLogger(rsg_PortraitPatch.class);

    private static final float SIZE_FRACTION = 0.42f;
    private static final float MIN_SIZE = 14f;
    private static final float SIDE_GAP = 3f;
    // vanilla puts the skill list this far right of the portrait
    private static final float SKILL_LIST_GAP = 10f;
    private static final float REFRESH_INTERVAL = 0.25f;

    private static final String KEY_PIP = "rsg_pip";
    private static final String KEY_PERSON = "rsg_pip_person";
    private static final String KEY_LEVEL = "rsg_pip_level";
    private static final String KEY_TINT = "rsg_pip_tint";
    private static final String KEY_EMPTY = "rsg_pip_empty";
    private static final String KEY_TIMER = "rsg_pip_timer";

    private static final MethodQuerySpec GET_MEMBER = MethodQuery.create().methodName("getMember").parameterCount(0).build();
    private static final MethodQuerySpec GET_PERSON = MethodQuery.create().methodName("getPerson").parameterCount(0).build();
    private static final MethodQuerySpec GET_COLOR = MethodQuery.create().methodName("getColor").parameterCount(0).build();
    private static final MethodQuerySpec GET_PORTRAIT = MethodQuery.create().methodName("getPortrait").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();
    // the button the portrait is drawn in, and its tooltip and listener
    private static final MethodQuerySpec GET_BUTTON = MethodQuery.create().methodName("getButton").parameterCount(0).build();
    private static final MethodQuerySpec GET_TOOLTIP = MethodQuery.create().methodName("getTooltip").parameterCount(0).build();
    private static final MethodQuerySpec SET_TOOLTIP = MethodQuery.create().methodName("setTooltip").parameterCount(2).build();
    private static final MethodQuerySpec REMOVE_TOOLTIP = MethodQuery.create().methodName("forceRemoveTooltip").parameterCount(1).build();
    private static final MethodQuerySpec GET_LISTENER = MethodQuery.create().methodName("getListener").parameterCount(0).build();
    private static final MethodQuerySpec SHOW_PICKER = MethodQuery.create().methodName("showCaptainPickerDialog").parameterCount(1).build();

    // corner overlays by the vanilla portrait they sit on, for rsg_PortraitButtonPatch to find clicks on them.
    // A static map rather than getData(): the button patch runs on the button, and a patch can only read the patch
    // data of its own instance, not of the portrait drawn inside the button. Keys are weak so closed screens are
    // collected, and values are weak so an overlay never keeps its portrait alive. Other mods' patches can't see it.
    private static final Map<Object, WeakReference<CustomPanelAPI>> OVERLAYS = new WeakHashMap<Object, WeakReference<CustomPanelAPI>>();

    /** The corner overlay on this vanilla portrait panel, or null. */
    public static CustomPanelAPI getOverlay(Object portrait) {
        WeakReference<CustomPanelAPI> ref = OVERLAYS.get(portrait);
        return ref != null ? ref.get() : null;
    }

    /** Vanilla rebuilds every child here, so the old picture-in-picture is already gone. */
    @After(target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2))
    public static void afterSizeChanged(AfterContext ctx) {
        try {
            PatchData data = ctx.getData();
            restoreTooltip(ctx, getOverlay(ctx.getSelf()));
            OVERLAYS.remove(ctx.getSelf());
            data.remove(KEY_PIP);
            data.remove(KEY_PERSON);
            data.remove(KEY_LEVEL);
            update(ctx);
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't add the second officer portrait", t);
        }
    }

    /** Catches assignment and level changes while the portrait is shown, a few times a second. */
    @After(target = @MethodMatch(methodName = "advanceImpl", parameterCount = 1))
    public static void afterAdvance(AfterContext ctx) {
        try {
            updateTooltip(ctx);
            PatchData data = ctx.getData();
            Float timer = data.get(KEY_TIMER);
            float elapsed = (timer != null ? timer : 0f) + (Float) ctx.getArg(0);
            if (elapsed < REFRESH_INTERVAL) {
                data.put(KEY_TIMER, elapsed);
                return;
            }
            data.put(KEY_TIMER, 0f);
            update(ctx);
        } catch (Throwable t) {
            log.error("Riding Shotgun: couldn't update the second officer portrait", t);
        }
    }

    private static void update(AfterContext ctx) {
        PatchData data = ctx.getData();
        UIPanelAPI portrait = (UIPanelAPI) ctx.getSelf();
        PersonAPI second = findSecondOfficer(ctx);
        int level = second != null ? second.getStats().getLevel() : -1;
        // combat portraits carry the ship's side color; everywhere else this is null
        Color tint = ctx.hasMethod(GET_COLOR) ? ctx.getMethod(GET_COLOR).call() : null;
        boolean sideBySide = Global.getCurrentState() == GameState.COMBAT;
        // an empty second seat gets a clickable empty portrait, but only where clicking can assign officers
        boolean emptySeat = second == null && !sideBySide && hasEmptySeat(ctx);

        UIComponentAPI pip = data.get(KEY_PIP);
        Integer shownLevel = data.get(KEY_LEVEL);
        if (pip != null && second == data.get(KEY_PERSON) && shownLevel != null && shownLevel == level
                && Objects.equals(tint, data.get(KEY_TINT)) && emptySeat == data.has(KEY_EMPTY)) {
            // vanilla re-adds its portrait when the captain changes, which would cover ours
            portrait.bringComponentToTop(pip);
            return;
        }
        UIComponentAPI vanillaPortrait = getVanillaPortrait(ctx);
        if (pip != null) {
            if (pip instanceof CustomPanelAPI) restoreTooltip(ctx, (CustomPanelAPI) pip);
            portrait.removeComponent(pip);
            if (sideBySide && second == null) moveSkillList(ctx, vanillaPortrait, vanillaPortrait);
        }
        data.remove(KEY_PIP);
        data.remove(KEY_PERSON);
        data.remove(KEY_LEVEL);
        data.remove(KEY_TINT);
        data.remove(KEY_EMPTY);
        OVERLAYS.remove(portrait);
        if (second == null && !emptySeat) return;

        float width = portrait.getPosition().getWidth();
        float height = portrait.getPosition().getHeight();
        CustomPanelAPI created;
        if (sideBySide) {
            // same size and inset as the vanilla portrait, placed to its right
            float inset = vanillaPortrait != null
                    ? Math.max(0f, (Math.min(width, height) - vanillaPortrait.getPosition().getWidth()) / 2f) : 1f;
            created = rsg_PipPortrait.create(second, width, height, inset, tint, true);
            portrait.addComponent(created).inBL(width + SIDE_GAP, 0f);
            moveSkillList(ctx, vanillaPortrait, created);
        } else {
            float size = Math.round(Math.min(width, height) * SIZE_FRACTION);
            if (size < MIN_SIZE) return;
            created = rsg_PipPortrait.create(second, size, size, 1f, tint, false);
            portrait.addComponent(created).inBL(1f, 1f);
            OVERLAYS.put(portrait, new WeakReference<CustomPanelAPI>(created));
        }
        data.putWeak(KEY_PIP, created);
        if (second != null) data.putWeak(KEY_PERSON, second);
        data.put(KEY_LEVEL, level);
        if (tint != null) data.put(KEY_TINT, tint);
        if (emptySeat) data.put(KEY_EMPTY, Boolean.TRUE);
    }

    /**
     * True for a fleet-screen portrait whose ship has the Dual Command Interface, and whose button can open the
     * officer picker, so an empty second seat can be shown and clicked.
     */
    private static boolean hasEmptySeat(AfterContext ctx) {
        if (Global.getCurrentState() != GameState.CAMPAIGN || isRefitTab()) return false;
        Object member = ctx.hasMethod(GET_MEMBER) ? ctx.getMethod(GET_MEMBER).call() : null;
        if (!(member instanceof FleetMemberAPI)) return false;
        FleetMemberAPI m = (FleetMemberAPI) member;
        if (m.isFighterWing() || m.getVariant() == null || Misc.isAutomated(m)) return false;
        if (m.getFleetData() == null || m.getFleetData() != rsg_SecondOfficers.getPlayerFleetData()) return false;
        if (!rsg_SecondOfficers.hasDualCommand(m)) return false;
        return canAssign(ctx, getButton(ctx));
    }

    /** Whether this portrait's button belongs to a screen that can open the officer picker. */
    private static boolean canAssign(AfterContext ctx, Object button) {
        if (button == null || !ctx.hasMethod(GET_LISTENER, button)) return false;
        Object listener = ctx.getMethod(GET_LISTENER, button).call();
        return listener != null && ctx.hasMethod(SHOW_PICKER, listener);
    }

    private static boolean isRefitTab() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        return ui != null && ui.getCurrentCoreTab() == CoreUITabId.REFIT;
    }

    /**
     * The portrait is drawn inside a vanilla button, and the button's tooltip is the captain's. While the mouse is
     * over the corner overlay, give the button the second officer's tooltip instead, and put the captain's back when
     * it leaves. Only on portraits that assign officers, the same ones rsg_PortraitButtonPatch makes clickable.
     */
    private static void updateTooltip(AfterContext ctx) {
        CustomPanelAPI overlay = getOverlay(ctx.getSelf());
        if (overlay == null || !(overlay.getPlugin() instanceof rsg_PipPortrait)) return;
        rsg_PipPortrait plugin = (rsg_PipPortrait) overlay.getPlugin();
        boolean over = Global.getCurrentState() == GameState.CAMPAIGN && isMouseOver(overlay);
        float lastX = plugin.getLastMouseX(), lastY = plugin.getLastMouseY();
        plugin.setLastMouse(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
        if (over == plugin.isTooltipSwapped()) return;
        if (!over) {
            restoreTooltip(ctx, overlay);
            // moved from the overlay onto the rest of the portrait (not off it): sound like entering the portrait
            Object button = getButton(ctx);
            if (button instanceof UIComponentAPI) {
                PositionAPI p = ((UIComponentAPI) button).getPosition();
                float x = Global.getSettings().getMouseX(), y = Global.getSettings().getMouseY();
                if (x >= p.getX() && x <= p.getX() + p.getWidth() && y >= p.getY() && y <= p.getY() + p.getHeight()) {
                    Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f);
                }
            }
            return;
        }

        Object button = getButton(ctx);
        if (!canSwapTooltip(ctx, button)) return;
        if (!canAssign(ctx, button)) return;
        Object member = ctx.hasMethod(GET_MEMBER) ? ctx.getMethod(GET_MEMBER).call() : null;
        if (!(member instanceof FleetMemberAPI)) return;

        Object captainTooltip = clearTooltip(ctx, button);
        plugin.getElement().addTooltipTo(new rsg_SecondOfficerTooltip((FleetMemberAPI) member),
                (UIComponentAPI) button, TooltipMakerAPI.TooltipLocation.BELOW);
        plugin.setTooltipSwapped(true, captainTooltip);

        // vanilla plays the hover sound when the mouse enters the portrait; when it comes onto the overlay from the
        // rest of the portrait, play it again so the overlay sounds like a portrait of its own
        PositionAPI p = ((UIComponentAPI) button).getPosition();
        if (lastX >= p.getX() && lastX <= p.getX() + p.getWidth() && lastY >= p.getY() && lastY <= p.getY() + p.getHeight()) {
            Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f);
        }
    }

    /** Puts the captain's tooltip back on the portrait button if the overlay had swapped it out. */
    private static void restoreTooltip(AfterContext ctx, CustomPanelAPI overlay) {
        if (overlay == null || !(overlay.getPlugin() instanceof rsg_PipPortrait)) return;
        rsg_PipPortrait plugin = (rsg_PipPortrait) overlay.getPlugin();
        if (!plugin.isTooltipSwapped()) return;
        Object saved = plugin.getSavedTooltip();
        plugin.setTooltipSwapped(false, null);
        Object button = getButton(ctx);
        if (!canSwapTooltip(ctx, button)) return;
        clearTooltip(ctx, button);
        ctx.getMethod(SET_TOOLTIP, button).call(0f, saved);
    }

    private static boolean canSwapTooltip(AfterContext ctx, Object button) {
        return button instanceof UIComponentAPI && ctx.hasMethod(GET_TOOLTIP, button)
                && ctx.hasMethod(REMOVE_TOOLTIP, button) && ctx.hasMethod(SET_TOOLTIP, button);
    }

    /**
     * Takes the button's tooltip off: detaches it if it's showing, then sets none. Setting none is what resets the
     * tooltip's hover logic; skipping it leaves the old logic registered as the screen's active tooltip, which
     * blocks the next tooltip from showing until the mouse leaves the button. Returns the tooltip taken off.
     */
    private static Object clearTooltip(AfterContext ctx, Object button) {
        Object current = ctx.getMethod(GET_TOOLTIP, button).call();
        if (current != null) ctx.getMethod(REMOVE_TOOLTIP, button).call(current);
        ctx.getMethod(SET_TOOLTIP, button).call(0f, null);
        return current;
    }

    private static Object getButton(AfterContext ctx) {
        return ctx.hasMethod(GET_BUTTON) ? ctx.getMethod(GET_BUTTON).call() : null;
    }

    private static boolean isMouseOver(CustomPanelAPI overlay) {
        PositionAPI p = overlay.getPosition();
        float x = Global.getSettings().getMouseX();
        float y = Global.getSettings().getMouseY();
        return x >= p.getX() && x <= p.getX() + p.getWidth() && y >= p.getY() && y <= p.getY() + p.getHeight();
    }

    private static UIComponentAPI getVanillaPortrait(AfterContext ctx) {
        if (!ctx.hasMethod(GET_PORTRAIT)) return null;
        Object portrait = ctx.getMethod(GET_PORTRAIT).call();
        return portrait instanceof UIComponentAPI ? (UIComponentAPI) portrait : null;
    }

    /**
     * Combat portraits can show the captain's skill list to the right of the portrait, which is where the second
     * portrait goes, so move the list past it (or back next to the vanilla portrait). The list is the only child
     * panel besides the vanilla portrait and ours; the level labels are not panels.
     */
    private static void moveSkillList(AfterContext ctx, UIComponentAPI vanillaPortrait, UIComponentAPI anchor) {
        if (anchor == null || !ctx.hasMethod(GET_CHILDREN)) return;
        List<?> children = ctx.getMethod(GET_CHILDREN).call();
        for (Object child : children) {
            if (child == anchor || child == vanillaPortrait || !(child instanceof UIPanelAPI)) continue;
            if (child instanceof CustomPanelAPI) continue;
            ((UIComponentAPI) child).getPosition().rightOfBottom(anchor, SKILL_LIST_GAP).setYAlignOffset(-1f);
        }
    }

    private static PersonAPI findSecondOfficer(AfterContext ctx) {
        GameState state = Global.getCurrentState();
        if (state == GameState.CAMPAIGN && isRefitTab()) return null;

        // the fleet screen tells the portrait which ship it belongs to
        if (ctx.hasMethod(GET_MEMBER)) {
            Object member = ctx.getMethod(GET_MEMBER).call();
            if (member instanceof FleetMemberAPI) return rsg_SecondOfficers.get((FleetMemberAPI) member);
        }

        // a combat reticle portrait knows its ship; the captain lookup below can't tell apart two ships the player
        // captains at once (Neural Link)
        if (state == GameState.COMBAT) {
            ShipAPI ship = rsg_CombatReticlePatch.getShip(ctx.getSelf());
            if (ship != null) return rsg_SecondOfficers.get(ship.getFleetMember());
        }

        // otherwise (tooltips, other combat portraits) find the ship this captain commands
        if (!ctx.hasMethod(GET_PERSON)) return null;
        Object captain = ctx.getMethod(GET_PERSON).call();
        if (!(captain instanceof PersonAPI) || ((PersonAPI) captain).isDefault()) return null;
        if (state == GameState.COMBAT) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return null;
            for (ShipAPI ship : engine.getShips()) {
                if (ship.getCaptain() == captain && !ship.isFighter()) return rsg_SecondOfficers.get(ship.getFleetMember());
            }
            return null;
        }
        FleetDataAPI fleetData = rsg_SecondOfficers.getPlayerFleetData();
        if (fleetData == null) return null;
        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (member.getCaptain() == captain) return rsg_SecondOfficers.get(member);
        }
        return null;
    }
}
