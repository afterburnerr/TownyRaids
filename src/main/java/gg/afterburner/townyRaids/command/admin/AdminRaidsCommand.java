package gg.afterburner.townyRaids.command.admin;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.command.CommandContext;
import gg.afterburner.townyRaids.config.ConfigLoadException;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidPhase;
import gg.afterburner.townyRaids.raid.result.RaidDeclarationResult;
import gg.afterburner.townyRaids.util.Permissions;
import gg.afterburner.townyRaids.util.TownyUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static gg.afterburner.townyRaids.config.MessagesManager.placeholder;

public final class AdminRaidsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("reload", "start", "end", "phase");
    private static final List<String> PHASES = List.of("pending", "battle");

    private final CommandContext context;
    private final Runnable reloadCallback;

    public AdminRaidsCommand(CommandContext context, Runnable reloadCallback) {
        this.context = Objects.requireNonNull(context);
        this.reloadCallback = Objects.requireNonNull(reloadCallback);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ROOT)) {
            sender.sendMessage(context.messages().render("generic.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> handleReload(sender);
            case "start" -> handleStart(sender, args);
            case "end" -> handleEnd(sender, args);
            case "phase" -> handlePhase(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            sender.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        try {
            reloadCallback.run();
            sender.sendMessage(context.messages().render("generic.reloaded"));
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() instanceof ConfigLoadException ? ex.getCause() : ex;
            sender.sendMessage(context.messages().render("generic.reload-failed",
                    placeholder("reason", cause.getMessage() == null ? cause.toString() : cause.getMessage())));
        }
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_FORCE_START)) {
            sender.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Optional<Town> attacker = TownyUtil.townByName(args[1]);
        Optional<Town> defender = TownyUtil.townByName(args[2]);
        if (attacker.isEmpty()) {
            sender.sendMessage(context.messages().render("generic.unknown-town",
                    placeholder("town", args[1])));
            return;
        }
        if (defender.isEmpty()) {
            sender.sendMessage(context.messages().render("generic.unknown-town",
                    placeholder("town", args[2])));
            return;
        }
        RaidDeclarationResult result = context.raidManager().declare(attacker.get(), defender.get());
        TagResolver resolver = TagResolver.resolver(
                placeholder("attacker", attacker.get().getName()),
                placeholder("defender", defender.get().getName())
        );
        if (result instanceof RaidDeclarationResult.Success) {
            sender.sendMessage(context.messages().render("admin.forced-declare", resolver));
            return;
        }
        sender.sendMessage(context.messages().render("generic.reload-failed",
                placeholder("reason", result.getClass().getSimpleName())));
    }

    private void handleEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_FORCE_END)) {
            sender.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }
        Optional<Town> town = TownyUtil.townByName(args[1]);
        if (town.isEmpty()) {
            sender.sendMessage(context.messages().render("generic.unknown-town",
                    placeholder("town", args[1])));
            return;
        }
        Optional<Raid> raid = context.raidManager().activeRaidOfTown(town.get().getUUID());
        if (raid.isEmpty()) {
            sender.sendMessage(context.messages().render("admin.no-raid-for-town",
                    placeholder("town", town.get().getName())));
            return;
        }
        context.raidManager().forceEnd(raid.get());
        sender.sendMessage(context.messages().render("admin.forced-end",
                placeholder("town", town.get().getName())));
    }

    private void handlePhase(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_FORCE_PHASE)) {
            sender.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }
        Optional<Town> town = TownyUtil.townByName(args[1]);
        if (town.isEmpty()) {
            sender.sendMessage(context.messages().render("generic.unknown-town",
                    placeholder("town", args[1])));
            return;
        }
        Optional<Raid> raid = context.raidManager().activeRaidOfTown(town.get().getUUID());
        if (raid.isEmpty()) {
            sender.sendMessage(context.messages().render("admin.no-raid-for-town",
                    placeholder("town", town.get().getName())));
            return;
        }
        Optional<RaidPhase> phase = RaidPhase.fromInput(args[2]);
        if (phase.isEmpty() || phase.get() == RaidPhase.ENDED) {
            sender.sendMessage(context.messages().render("admin.invalid-phase"));
            return;
        }
        context.raidManager().forcePhase(raid.get(), phase.get());
        sender.sendMessage(context.messages().render("admin.forced-phase",
                placeholder("phase", phase.get().name().toLowerCase(Locale.ROOT))));
    }

    private void sendUsage(CommandSender sender) {
        context.messages().renderLines("admin.usage").forEach(sender::sendMessage);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("start")
                || args[0].equalsIgnoreCase("end")
                || args[0].equalsIgnoreCase("phase"))) {
            if (args.length == 2 || args.length == 3 && args[0].equalsIgnoreCase("start")) {
                String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
                return com.palmergames.bukkit.towny.TownyAPI.getInstance().getTowns().stream()
                        .map(Town::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("phase")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return PHASES.stream().filter(s -> s.startsWith(prefix)).toList();
            }
        }
        return Collections.emptyList();
    }
}
