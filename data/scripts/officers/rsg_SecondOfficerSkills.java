package data.scripts.officers;

import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillEffectType;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI.SkillEffectSpecAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a second officer's skills the same way vanilla applies a captain's (CharacterStats.applyPersonalToStats
 * and friends), with the same effect ids, but skips every effect the captain already gets.
 * Skipping matters: some skills (Combat Endurance, Polarized Armor...) add a listener on every apply and remove all
 * listeners of their class on unapply, so applying a shared skill twice would stack or cancel it.
 * Modded skills apply only if rsg_SkillWhitelist supports them.
 */
public class rsg_SecondOfficerSkills {

    public static void applyToStats(PersonAPI second, PersonAPI captain, MutableShipStatsAPI stats, HullSize hullSize) {
        for (Effect e : getEffects(second, captain, SkillEffectType.SHIP, "_ship_")) {
            if (e.spec.getAsShipEffect() != null) {
                e.spec.getAsShipEffect().apply(stats, hullSize, e.id, e.level);
            }
        }
    }

    public static void applyAfterShipCreation(PersonAPI second, PersonAPI captain, ShipAPI ship) {
        for (Effect e : getEffects(second, captain, SkillEffectType.SHIP, "_ship_")) {
            AfterShipCreationSkillEffect effect = e.spec.getAsAfterShipCreationEffect();
            if (effect != null) {
                effect.unapplyEffectsAfterShipCreation(ship, e.id);
                effect.applyEffectsAfterShipCreation(ship, e.id);
            }
        }
    }

    public static void applyToFighter(PersonAPI second, PersonAPI captain, ShipAPI fighter) {
        for (Effect e : getEffects(second, captain, SkillEffectType.SHIP_FIGHTERS, "_ship_fighters_")) {
            if (e.spec.getAsShipEffect() != null) {
                e.spec.getAsShipEffect().apply(fighter.getMutableStats(), fighter.getHullSize(), e.id, e.level);
            }
        }
    }

    /** Skills the second officer has that the captain does not, for showing in tooltips. */
    public static List<SkillSpecAPI> getUniqueSkills(PersonAPI second, PersonAPI captain) {
        List<SkillSpecAPI> result = new ArrayList<SkillSpecAPI>();
        for (SkillLevelAPI sl : second.getStats().getSkillsCopy()) {
            SkillSpecAPI skill = sl.getSkill();
            if (!skill.isCombatOfficerSkill() || sl.getLevel() <= 0) continue;
            if (!rsg_SkillWhitelist.isSupported(skill.getId())) continue;
            boolean adds = false;
            for (SkillEffectSpecAPI effect : skill.getEffectsAPI()) {
                String governing = getGoverningSkill(effect, skill);
                float level = second.getStats().getSkillLevel(governing);
                if (level >= effect.getRequiredSkillLevel() && !captainHas(captain, governing, effect)) {
                    adds = true;
                    break;
                }
            }
            if (adds) result.add(skill);
        }
        return result;
    }

    /** Which part of a skill the second officer adds: "elite", "elite effects only", "base", or null if nothing. */
    public static String getAppliedPart(PersonAPI second, PersonAPI captain, SkillSpecAPI skill) {
        if (!rsg_SkillWhitelist.isSupported(skill.getId())) return null;
        float secondLevel = second.getStats().getSkillLevel(skill.getId());
        float captainLevel = captain != null && captain.getStats() != null ? captain.getStats().getSkillLevel(skill.getId()) : 0f;
        if (secondLevel <= 0 || captainLevel >= secondLevel) return null;
        if (secondLevel >= 2) return captainLevel >= 1 ? "elite effects only" : "elite";
        return "base";
    }

    private static List<Effect> getEffects(PersonAPI second, PersonAPI captain, SkillEffectType type, String idInfix) {
        List<Effect> result = new ArrayList<Effect>();
        if (second == null || second.getStats() == null) return result;
        for (SkillLevelAPI sl : second.getStats().getSkillsCopy()) {
            SkillSpecAPI skill = sl.getSkill();
            // modded skills not on the whitelist may not work from the second seat (rsg_SkillWhitelist)
            if (!rsg_SkillWhitelist.isSupported(skill.getId())) continue;
            int index = 0;
            for (SkillEffectSpecAPI effect : skill.getEffectsAPI()) {
                // vanilla counts every effect of the skill when building the id, whatever its type
                String id = skill.getId() + idInfix + index;
                index++;
                if (effect.getType() != type) continue;
                String governing = getGoverningSkill(effect, skill);
                float level = second.getStats().getSkillLevel(governing);
                if (level < effect.getRequiredSkillLevel()) continue;
                if (captainHas(captain, governing, effect)) continue;
                result.add(new Effect(effect, id, level));
            }
        }
        return result;
    }

    private static boolean captainHas(PersonAPI captain, String governing, SkillEffectSpecAPI effect) {
        if (captain == null || captain.getStats() == null) return false;
        float level = captain.getStats().getSkillLevel(governing);
        return level > 0 && level >= effect.getRequiredSkillLevel();
    }

    private static String getGoverningSkill(SkillEffectSpecAPI effect, SkillSpecAPI skill) {
        return effect.getGoverningSkill() != null ? effect.getGoverningSkill() : skill.getId();
    }

    private static class Effect {
        final SkillEffectSpecAPI spec;
        final String id;
        final float level;

        Effect(SkillEffectSpecAPI spec, String id, float level) {
            this.spec = spec;
            this.id = id;
            this.level = level;
        }
    }
}
