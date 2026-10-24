package data.scripts.officers;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Which skills a co-pilot may apply.
 * - Skills on the blacklist (data/config/rsgConfig/rsg_skill_blacklist.csv) never apply: they are confirmed not to
 *   work from the second seat. The blacklist overrides everything below, the whitelist and vanilla included.
 * - Vanilla skills always apply. Their ids come from the game's own skill_data.csv, so a mod that rebalances a
 *   vanilla skill keeps it supported.
 * - Skills added by mods apply only once they are listed in data/config/rsgConfig/rsg_skill_whitelist.csv, since
 *   many of them assume the skill's owner is the ship's captain. The LunaLib setting "Use skill whitelist" turns
 *   this off, so that every modded skill not on the blacklist applies.
 * Both lists are merged across mods, so another mod can mark its own skills with the same files.
 * Unsupported skills are not applied, don't count towards the deployment cost increase, and are listed as
 * unsupported in the co-pilot tooltip.
 */
public class rsg_SkillWhitelist {

    public static final String WHITELIST_PATH = "data/config/rsgConfig/rsg_skill_whitelist.csv";
    public static final String BLACKLIST_PATH = "data/config/rsgConfig/rsg_skill_blacklist.csv";
    private static final String VANILLA_SKILLS_PATH = "data/characters/skills/skill_data.csv";
    private static final String MOD_ID = "rsg_riding_shotgun";
    // LunaLib setting, data/config/LunaSettings.csv
    private static final String SETTING_USE_WHITELIST = "rsg_useSkillWhitelist";

    private static final Logger log = Global.getLogger(rsg_SkillWhitelist.class);

    private static Set<String> vanilla;
    private static Set<String> whitelisted;
    private static Set<String> blacklisted;

    public static boolean isSupported(String skillId) {
        if (skillId == null) return false;
        load();
        if (blacklisted.contains(skillId)) return false;
        if (vanilla.contains(skillId)) return true;
        return !isWhitelistEnabled() || whitelisted.contains(skillId);
    }

    /** The "Use skill whitelist" setting; on unless the player turned it off. */
    public static boolean isWhitelistEnabled() {
        if (!Global.getSettings().getModManager().isModEnabled("lunalib")) return true;
        Boolean value = LunaSettings.getBoolean(MOD_ID, SETTING_USE_WHITELIST);
        return value == null || value;
    }

    private static void load() {
        if (vanilla != null) return;
        vanilla = readIds(VANILLA_SKILLS_PATH, false);
        whitelisted = readIds(WHITELIST_PATH, true);
        blacklisted = readIds(BLACKLIST_PATH, true);
        log.info("Riding Shotgun: " + vanilla.size() + " vanilla skills, " + whitelisted.size()
                + " whitelisted and " + blacklisted.size() + " blacklisted skills for co-pilots");
    }

    /** The id column of a CSV: the game's own copy only, or merged across every enabled mod. */
    private static Set<String> readIds(String path, boolean merged) {
        Set<String> ids = new HashSet<String>();
        try {
            JSONArray rows = merged
                    ? Global.getSettings().getMergedSpreadsheetDataForMod("id", path, MOD_ID)
                    : Global.getSettings().loadCSV(path, false);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("id", "").trim();
                if (!id.isEmpty() && !id.startsWith("#")) ids.add(id);
            }
        } catch (Exception e) {
            // a broken list file only loses that list's entries; the vanilla list ships with the game
            log.error("Riding Shotgun: couldn't read " + path, e);
        }
        return ids;
    }
}
