package gg.afterburner.townyRaids.command;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.raid.result.TributeResult;
import gg.afterburner.townyRaids.util.Permissions;
import gg.afterburner.townyRaids.util.TownyUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static gg.afterburner.townyRaids.config.MessagesManager.placeholder;

public final class TributeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("request", "accept", "deny");

    private final CommandContext context;

    public TributeCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(context.messages().render("generic.must-be-player"));
            return true;
        }
        if (!rateCheck(player)) return true;
        if (args.length == 0) {
            player.sendMessage(context.messages().render("tribute.usage"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Optional<Town> playerTown = TownyUtil.townOf(player);
        if (playerTown.isEmpty()) {
            player.sendMessage(context.messages().render("generic.must-have-town"));
            return true;
        }
        if (!TownyUtil.isMayorOrAssistant(player, playerTown.get())) {
            player.sendMessage(context.messages().render("generic.must-be-mayor-or-assistant"));
            return true;
        }
        switch (action) {
            case "request" -> handleRequest(player, playerTown.get(), args);
            case "accept" -> handleAccept(player, playerTown.get());
            case "deny" -> handleDeny(player, playerTown.get());
            default -> player.sendMessage(context.messages().render("tribute.usage"));
        }
        return true;
    }

    private void handleRequest(Player player, Town attackerTown, String[] args) {
        if (!player.hasPermission(Permissions.TRIBUTE_REQUEST)) {
            player.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(context.messages().render("tribute.usage"));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(context.messages().render("tribute.invalid-amount"));
            return;
        }
        TributeResult result = context.tributeManager().request(attackerTown, amount);
        handleRequestResult(player, result);
    }

    private void handleAccept(Player player, Town defenderTown) {
        if (!player.hasPermission(Permissions.TRIBUTE_ACCEPT)) {
            player.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        TributeResult result = context.tributeManager().accept(defenderTown);
        handleAnswerResult(player, result);
    }

    private void handleDeny(Player player, Town defenderTown) {
        if (!player.hasPermission(Permissions.TRIBUTE_ACCEPT)) {
            player.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        TributeResult result = context.tributeManager().deny(defenderTown);
        handleAnswerResult(player, result);
    }

    private void handleRequestResult(Player player, TributeResult result) {
        switch (result) {
            case TributeResult.Requested req -> {
                Optional<Town> defender = TownyUtil.townOf(player)
                        .flatMap(t -> context.raidManager().activeRaidOfTown(t.getUUID()))
                        .flatMap(r -> TownyUtil.townByUuid(r.defenderTownId()));
                player.sendMessage(context.messages().render("tribute.request-sent",
                        TagResolver.resolver(
                                placeholder("amount", context.tributeManager().formatAmount(req.amount())),
                                placeholder("defender", defender.map(Town::getName).orElse("")))));
            }
            case TributeResult.Disabled ignored -> player.sendMessage(context.messages().render("tribute.disabled"));
            case TributeResult.NotInPendingPhase ignored -> player.sendMessage(
                    context.messages().render("tribute.raid-not-pending"));
            case TributeResult.AmountOutOfBounds bounds -> sendAmountOutOfBounds(player, bounds);
            case TributeResult.AlreadyPending ignored -> player.sendMessage(
                    context.messages().render("tribute.already-pending"));
            case TributeResult.NotAllowed not -> player.sendMessage(
                    context.messages().render("tribute." + not.reason()));
            case TributeResult.NoActiveRaid ignored -> player.sendMessage(
                    context.messages().render("tribute.no-active-raid"));
            default -> player.sendMessage(context.messages().render("tribute.usage"));
        }
    }

    private void handleAnswerResult(Player player, TributeResult result) {
        switch (result) {
            case TributeResult.Accepted acc -> {
                String attackerName = TownyUtil.townByUuid(acc.attackerTownId())
                        .map(Town::getName).orElse("");
                player.sendMessage(context.messages().render("tribute.accepted",
                        TagResolver.resolver(
                                placeholder("amount", context.tributeManager().formatAmount(acc.amount())),
                                placeholder("attacker", attackerName))));
            }
            case TributeResult.Denied ignored -> player.sendMessage(context.messages().render("tribute.denied"));
            case TributeResult.Expired ignored -> player.sendMessage(context.messages().render("tribute.expired"));
            case TributeResult.NoPendingRequest ignored -> player.sendMessage(
                    context.messages().render("tribute.no-pending"));
            case TributeResult.InsufficientFunds ignored -> player.sendMessage(
                    context.messages().render("tribute.insufficient-funds"));
            case TributeResult.NotAllowed not -> player.sendMessage(
                    context.messages().render("tribute." + not.reason()));
            case TributeResult.NoActiveRaid ignored -> player.sendMessage(
                    context.messages().render("tribute.no-active-raid"));
            default -> player.sendMessage(context.messages().render("tribute.usage"));
        }
    }

    private void sendAmountOutOfBounds(Player player, TributeResult.AmountOutOfBounds bounds) {
        String key = bounds.tooLow() ? "tribute.amount-too-low" : "tribute.amount-too-high";
        double reference = bounds.tooLow() ? bounds.min() : bounds.max();
        player.sendMessage(context.messages().render(key,
                placeholder("amount", context.tributeManager().formatAmount(reference))));
    }

    private boolean rateCheck(Player player) {
        long seconds = context.configManager().settings().runtime().commandRateLimitSeconds();
        if (!context.rateLimiter().tryAcquire(player.getUniqueId(), Duration.ofSeconds(seconds))) {
            player.sendMessage(context.messages().render("generic.rate-limited"));
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
