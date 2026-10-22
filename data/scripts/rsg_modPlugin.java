package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.officers.rsg_SecondOfficerCleanup;
import org.apache.log4j.Logger;

public class rsg_modPlugin extends BaseModPlugin {

    public static final String MOD_ID = "rsg_riding_shotgun";
    private static final Logger log = Logger.getLogger(rsg_modPlugin.class);

    @Override
    public void onApplicationLoad() {
        log.info("Riding Shotgun loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().addTransientScript(new rsg_SecondOfficerCleanup());
    }
}
