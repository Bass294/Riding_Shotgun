package data.scripts.console;

import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.officers.rsg_SecondOfficerSkills;
import data.scripts.officers.rsg_SecondOfficers;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.List;

/** Test command for second officers until the refit UI exists. Ship and officer numbers come from "list". */
public class rsg_SecondOfficerCommand implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        FleetDataAPI fleetData = rsg_SecondOfficers.getPlayerFleetData();
        if (fleetData == null) {
            Console.showMessage("No player fleet.");
            return CommandResult.ERROR;
        }
        rsg_SecondOfficers.prune();

        String[] parts = args.trim().isEmpty() ? new String[]{"list"} : args.trim().split("\\s+");
        List<FleetMemberAPI> members = fleetData.getMembersListCopy();
        List<OfficerDataAPI> officers = fleetData.getOfficersCopy();

        switch (parts[0].toLowerCase()) {
            case "list":
                showList(members, officers);
                return CommandResult.SUCCESS;
            case "set": {
                if (parts.length != 3) return CommandResult.BAD_SYNTAX;
                FleetMemberAPI member = pick(members, parts[1]);
                OfficerDataAPI officer = pick(officers, parts[2]);
                if (member == null || officer == null) {
                    Console.showMessage("Unknown ship or officer number. Use \"rsg_SecondOfficer list\".");
                    return CommandResult.ERROR;
                }
                String error = rsg_SecondOfficers.set(member, officer.getPerson());
                if (error != null) {
                    Console.showMessage(error);
                    return CommandResult.ERROR;
                }
                if (member.getCaptain() == officer.getPerson()) {
                    Console.showMessage("The " + member.getShipName() + " had no captain, so " + officer.getPerson().getNameString() + " is now its captain.");
                } else {
                    Console.showMessage(officer.getPerson().getNameString() + " is now the co-pilot of the " + member.getShipName() + ".");
                }
                return CommandResult.SUCCESS;
            }
            case "skills": {
                if (parts.length != 2) return CommandResult.BAD_SYNTAX;
                FleetMemberAPI member = pick(members, parts[1]);
                if (member == null) {
                    Console.showMessage("Unknown ship number. Use \"rsg_SecondOfficer list\".");
                    return CommandResult.ERROR;
                }
                showSkills(member);
                return CommandResult.SUCCESS;
            }
            case "clear": {
                if (parts.length != 2) return CommandResult.BAD_SYNTAX;
                FleetMemberAPI member = pick(members, parts[1]);
                if (member == null) {
                    Console.showMessage("Unknown ship number. Use \"rsg_SecondOfficer list\".");
                    return CommandResult.ERROR;
                }
                rsg_SecondOfficers.clear(member);
                Console.showMessage("Cleared the co-pilot of the " + member.getShipName() + ".");
                return CommandResult.SUCCESS;
            }
            default:
                return CommandResult.BAD_SYNTAX;
        }
    }

    private static void showList(List<FleetMemberAPI> members, List<OfficerDataAPI> officers) {
        StringBuilder sb = new StringBuilder("Ships with the Dual Command Interface:\n");
        boolean any = false;
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI member = members.get(i);
            if (member.isFighterWing() || !member.getVariant().hasHullMod(rsg_SecondOfficers.HULLMOD_ID)) continue;
            any = true;
            PersonAPI second = rsg_SecondOfficers.get(member);
            sb.append("  [").append(i).append("] ").append(member.getShipName())
                    .append(" (").append(member.getHullSpec().getHullName()).append(")")
                    .append(" - captain: ").append(describe(member.getCaptain()))
                    .append(", second: ").append(describe(second)).append("\n");
        }
        if (!any) sb.append("  none\n");

        sb.append("Officers:\n");
        for (int i = 0; i < officers.size(); i++) {
            PersonAPI person = officers.get(i).getPerson();
            sb.append("  [").append(i).append("] ").append(person.getNameString())
                    .append(" (level ").append(person.getStats().getLevel()).append(")")
                    .append(" - ").append(getStatus(members, person)).append("\n");
        }
        Console.showMessage(sb.toString());
    }

    private static void showSkills(FleetMemberAPI member) {
        PersonAPI second = rsg_SecondOfficers.get(member);
        if (second == null) {
            Console.showMessage("The " + member.getShipName() + " has no co-pilot.");
            return;
        }
        PersonAPI captain = member.getCaptain();
        StringBuilder sb = new StringBuilder("Co-pilot " + describe(second) + " of the " + member.getShipName()
                + ", captain " + describe(captain) + ":\n");
        for (SkillLevelAPI sl : second.getStats().getSkillsCopy()) {
            SkillSpecAPI skill = sl.getSkill();
            if (!skill.isCombatOfficerSkill() || sl.getLevel() <= 0) continue;
            String part = rsg_SecondOfficerSkills.getAppliedPart(second, captain, skill);
            sb.append("  ").append(skill.getName()).append(" (").append(skill.getId()).append(")")
                    .append(sl.getLevel() >= 2 ? " elite" : " base")
                    .append(" - applies: ").append(part != null ? part : "nothing, the captain has it").append("\n");
        }
        Console.showMessage(sb.toString());
    }

    private static String getStatus(List<FleetMemberAPI> members, PersonAPI person) {
        for (FleetMemberAPI member : members) {
            if (member.getCaptain() == person) return "captain of the " + member.getShipName();
            if (rsg_SecondOfficers.get(member) == person) return "co-pilot of the " + member.getShipName();
        }
        return "unassigned";
    }

    private static String describe(PersonAPI person) {
        if (person == null || person.isDefault()) return "none";
        return person.getNameString() + " (level " + person.getStats().getLevel() + ")";
    }

    private static <T> T pick(List<T> list, String index) {
        try {
            int i = Integer.parseInt(index);
            return i >= 0 && i < list.size() ? list.get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
