# Riding Shotgun

A meme vibeslop tech demo for a stupid idea that totally will not explode everything TM

Work in progress. Mostly feature complete as a tech demo.

Adds the **Dual Command Interface** hullmod, discoverable from a proc-gen station with a bar event hint. The hullmod allows a second officer to be assigned as a co-pilot, and apply their non-duplicate combat skills to the captain. Usable on auto-ships, and skills apply to modules.

## (for nerd/modders) How co-pilot skills work (according to claude)
The co-pilot's skills are never copied to the captain. Instead, the Dual Command Interface hullmod runs the
co-pilot's skill effects on the ship itself, at the same points where the game applies the captain's skills: the
ship's stats, the ship once it is built for combat, and the fighters it launches. Effects the captain already gets
are skipped, so nothing applies twice. Each skill the co-pilot adds raises the ship's deployment cost by 8%.

Vanilla skills always work this way. Many modded skills do too, but some are written on the assumption that their
owner is the ship's captain, so modded skills only apply when they are on the skill whitelist. Skills that don't
apply are listed as "Unsupported" in the co-pilot's tooltip.

- `data/config/rsgConfig/rsg_skill_whitelist.csv`: modded skills checked to work from the co-pilot seat. Skills
  that only partly work are included, with the limitation described in the `notes` column.
- `data/config/rsgConfig/rsg_skill_blacklist.csv`: skills confirmed not to work. A skill is blacklisted when its
  effect does nothing unless its owner is the ship's captain: it checks whether the captain has the skill or is a
  specific character, or relies on a hullmod or script that removes itself or only counts captains. Skills whose
  effects only ever apply to the player are blacklisted too. The blacklist overrides the whitelist.

Both lists are merged across mods, so other mods can add their own skills by shipping a CSV (`id`, `notes`) at the
same path. The LunaLib setting **Use skill whitelist** (on by default) can be turned off to let every modded skill
apply; blacklisted skills stay disabled either way.

## (for nerd/modders) Patches (according to claude)
The game has no API for a second officer seat, so the mod uses PatchLib patches (all in `data/scripts/patches`)
where vanilla code has no hook. They only add to vanilla behaviour: none of them skips a vanilla method, and the
redirects always run the original call. Obfuscated game classes are matched by the API interfaces they implement and
their readable method names, never by obfuscated names, so the patches can survive game updates.

**Assigning co-pilots**
- `rsg_RefitOfficerPatch`: adds the co-pilot slot next to the captain's portrait on the refit screen, and moves the
  in-space refit CR readout aside to make room. Also applies the choice made in the officer picker when it closes.
- `rsg_FleetMemberPanelPatch`: applies a choice made in the picker when it was opened from the fleet screen.
- `rsg_CaptainPickerPatch`: the co-pilot seat reuses vanilla's officer picker. This retitles it ("Select co-pilot
  for the ...") and highlights the current co-pilot.
- `rsg_OfficerRowPatch`: shows co-pilots as "co-pilot of the ..." instead of "unassigned" in the picker, and
  refuses picks that can't be co-pilots (yourself, or an AI core on a crewed ship).

**Showing co-pilots**
- `rsg_PortraitPatch`: draws the co-pilot's portrait on vanilla officer portraits. It's a small corner portrait on
  the fleet screen and in tooltips, or an empty seat if none is assigned. In combat it sits beside the captain's.
  It also swaps in the co-pilot's tooltip when the mouse is over it.
- `rsg_PortraitButtonPatch`: vanilla portrait buttons don't pass clicks to what they draw, so this catches clicks on
  the co-pilot portrait. Left-click opens the picker, right-click removes the co-pilot.
- `rsg_CombatReticlePatch`: vanilla leaves the captain portrait off your own ship's target reticle, so this shows it
  there too. It also records which ship each reticle portrait belongs to, so the right co-pilot is shown when you
  captain two ships at once (Neural Link).

**Battles and the campaign**
- `rsg_OfficerXPPatch`: gives each co-pilot the same battle XP as their captain. The flagship's co-pilot gets the
  XP an officer deployed as long would have, since vanilla doesn't track officer XP for the player's ship.
- `rsg_CoPilotCoreBattlePatch`: an AI core co-pilot on a ship lost in battle is treated like a captain core. It
  stays aboard if the ship is recovered, goes into the loot if the ship was offered for recovery but not taken, and
  is lost otherwise.
- `rsg_AutomatedPointsPatch`: keeps the co-pilot's deployment cost increase out of the Automated Ships point count,
  so an AI core co-pilot adds no automated ship cost.

## Requirements
- PatchLib
- LunaLib
