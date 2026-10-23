package data.scripts.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The Gemini Core. Not an AI core: a special item the Dual Command Interface hullmod requires. Vanilla's
 * HullModItemManager takes it when the hullmod is installed and gives it back when the hullmod is removed.
 * The plugin params name the hullmod it is for.
 */
public class rsg_GeminiCoreItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        float opad = 10f;
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, false);

        String hullmodName = Global.getSettings().getHullModSpec(spec.getParams()).getDisplayName();
        tooltip.addPara("Required to install the %s hullmod. The core is used while the hullmod is installed, and returned when it is removed.",
                opad, Misc.getHighlightColor(), hullmodName);
        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }
}
