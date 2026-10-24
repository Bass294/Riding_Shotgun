package data.scripts.patches;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import data.scripts.officers.rsg_SecondOfficers;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

/**
 * The Automated Ships skill limit counts each automated ship's deployment points (times its captain core's
 * multiplier). The Dual Command Interface raises deployment points for each skill the co-pilot adds; an AI core
 * co-pilot shouldn't make the ship weigh more against that limit, so the ship's points are counted without that
 * increase. Its deployment cost in battle is unchanged.
 */
@Patch(target = @ClassMatch(type = BaseSkillEffectDescription.class))
public class rsg_AutomatedPointsPatch {

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "getAutomatedPoints", parameterCount = 2),
            call = @MethodMatch(methodName = "getPoints", parameterCount = 2),
            owner = @ClassMatch(type = BaseSkillEffectDescription.class))
    public static void redirectPoints(MethodCallContext context) {
        context.setResult(withoutCoPilotCost(context));
    }

    // the per-ship breakdown shown in the skill's tooltip
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "getAutomatedPointsDetail", parameterCount = 2),
            call = @MethodMatch(methodName = "getPoints", parameterCount = 2),
            owner = @ClassMatch(type = BaseSkillEffectDescription.class))
    public static void redirectPointsDetail(MethodCallContext context) {
        context.setResult(withoutCoPilotCost(context));
    }

    private static Object withoutCoPilotCost(MethodCallContext context) {
        Object result = context.call();
        Object arg = context.getCallArg(0);
        // points by ordnance points don't see the deployment increase
        if (!BaseSkillEffectDescription.USE_RECOVERY_COST) return result;
        if (!(result instanceof Float) || !(arg instanceof FleetMemberAPI)) return result;
        FleetMemberAPI member = (FleetMemberAPI) arg;
        // cheap check first: this runs for every automated ship whenever the limit is counted
        PersonAPI stored = rsg_SecondOfficers.getStored(member);
        if (stored == null || !stored.isAICore() || rsg_SecondOfficers.get(member) == null) return result;

        // getPoints is the ship's deployment points: round(computeEffective(base)) of DEPLOYMENT_POINTS_MOD, where
        // computeEffective(b) = (b + b * percent / 100 + flat) * mult. Recompute it without our percent.
        StatBonus mod = member.getStats().getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD);
        StatMod ours = mod.getPercentBonus(rsg_SecondOfficers.HULLMOD_ID);
        if (ours == null) return result;
        float base = member.getStats().getSuppliesToRecover().getBaseValue();
        float without = Math.round((base + base * (mod.getPercentMod() - ours.getValue()) / 100f + mod.getFlatBonus()) * mod.getMult());
        return Math.min((Float) result, without);
    }
}
