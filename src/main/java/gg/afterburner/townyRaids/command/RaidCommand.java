package gg.afterburner.townyRaids.command;

import com.palmergames.bukkit.towny.confirmations.Confirmation;
import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.raid.result.RaidCancellationResult;
import gg.afterburner.townyRaids.raid.result.RaidDeclarationResult;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static gg.afterburner.townyRaids.config.MessagesManager.placeholder;

public final class RaidCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_CHOICES = List.of("cancel");

    private final CommandContext context;

    public RaidCommand(CommandContext context) {
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
            sendHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("cancel")) {
            handleCancel(player);
            return true;
        }
        handleDeclare(player, args[0]);
        return true;
    }

    private void handleDeclare(Player player, String defenderName) {
        if (!player.hasPermission(Permissions.RAID_DECLARE)) {
            player.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        Optional<Town> playerTown = TownyUtil.townOf(player);
        if (playerTown.isEmpty()) {
            player.sendMessage(context.messages().render("generic.must-have-town"));
            return;
        }
        if (!TownyUtil.isMayorOrAssistant(player, playerTown.get())) {
            player.sendMessage(context.messages().render("generic.must-be-mayor-or-assistant"));
            return;
        }
        Optional<Town> defender = TownyUtil.townByName(defenderName);
        if (defender.isEmpty()) {
            player.sendMessage(context.messages().render("generic.unknown-town",
                    placeholder("town", defenderName)));
            return;
        }
        Town attacker = playerTown.get();
        if (attacker.getUUID().equals(defender.get().getUUID())) {
            player.sendMessage(context.messages().render("generic.same-town"));
            return;
        }

        RaidSettings settings = context.configManager().settings();
        UUID playerId = player.getUniqueId();
        Confirmation.runOnAccept(() -> performDeclaration(playerId, attacker.getUUID(), defender.get().getUUID()))
                .setTitle(plainText("raid.confirmation-title", defender.get()))
                .setDuration((int) settings.timings().confirmationSeconds())
                .sendTo(player);
        player.sendMessage(context.messages().render("raid.confirmation-sent",
                placeholder("seconds", Long.toString(settings.timings().confirmationSeconds()))));
    }

    private void performDeclaration(UUID playerId, UUID attackerTownId, UUID defenderTownId) {
        Optional<Town> attacker = TownyUtil.townByUuid(attackerTownId);
        Optional<Town> defender = TownyUtil.townByUuid(defenderTownId);
        if (attacker.isEmpty() || defender.isEmpty()) return;
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        RaidDeclarationResult result = context.raidManager().declare(attacker.get(), defender.get());
        if (player == null) return;
        handleDeclarationResult(player, attacker.get(), defender.get(), result);
    }

    private void handleDeclarationResult(Player player, Town attacker, Town defender, RaidDeclarationResult result) {
        TagResolver resolver = TagResolver.resolver(
                placeholder("attacker", attacker.getName()),
                placeholder("defender", defender.getName())
        );
        switch (result) {
            case RaidDeclarationResult.Success ignored -> { }
            case RaidDeclarationResult.AttackerOnCooldown cd -> player.sendMessage(
                    context.messages().render("raid.attacker-on-cooldown",
                            TagResolver.resolver(resolver,
                                    placeholder("time", formatSeconds(cd.remainingSeconds())))));
            case RaidDeclarationResult.DefenderProtected pd -> player.sendMessage(
                    context.messages().render("raid.defender-protected",
                            TagResolver.resolver(resolver,
                                    placeholder("time", formatSeconds(pd.remainingSeconds())))));
            case RaidDeclarationResult.DefenderTooSmall ignored -> player.sendMessage(
                    context.messages().render("raid.defender-too-small", resolver));
            case RaidDeclarationResult.AttackerTooSmall ignored -> player.sendMessage(
                    context.messages().render("raid.attacker-too-small", resolver));
            case RaidDeclarationResult.SameTown ignored -> player.sendMessage(
                    context.messages().render("generic.same-town"));
            case RaidDeclarationResult.SameNation ignored -> player.sendMessage(
                    context.messages().render("raid.same-nation", resolver));
            case RaidDeclarationResult.RaidAlreadyActive ignored -> player.sendMessage(
                    context.messages().render("raid.already-active"));
            case RaidDeclarationResult.Failure fail -> player.sendMessage(
                    context.messages().render("generic.reload-failed",
                            placeholder("reason", fail.reason())));
        }
    }

    private void handleCancel(Player player) {
        if (!player.hasPermission(Permissions.RAID_CANCEL)) {
            player.sendMessage(context.messages().render("generic.no-permission"));
            return;
        }
        Optional<Town> playerTown = TownyUtil.townOf(player);
        if (playerTown.isEmpty()) {
            player.sendMessage(context.messages().render("generic.must-have-town"));
            return;
        }
        if (!TownyUtil.isMayorOrAssistant(player, playerTown.get())) {
            player.sendMessage(context.messages().render("generic.must-be-mayor-or-assistant"));
            return;
        }
        RaidCancellationResult result = context.raidManager().cancelByAttacker(playerTown.get().getUUID());
        switch (result) {
            case RaidCancellationResult.Success ignored -> { }
            case RaidCancellationResult.NoActiveRaid ignored -> player.sendMessage(
                    context.messages().render("raid.no-active-raid"));
            case RaidCancellationResult.NotCancellableNow ignored -> player.sendMessage(
                    context.messages().render("raid.not-cancellable-now"));
            case RaidCancellationResult.NotAttacker ignored -> player.sendMessage(
                    context.messages().render("raid.not-cancellable-now"));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(context.messages().render("raid.help.header"));
        player.sendMessage(context.messages().render("raid.help.declare"));
        player.sendMessage(context.messages().render("raid.help.cancel"));
    }

    private boolean rateCheck(Player player) {
        long seconds = context.configManager().settings().runtime().commandRateLimitSeconds();
        if (!context.rateLimiter().tryAcquire(player.getUniqueId(), Duration.ofSeconds(seconds))) {
            player.sendMessage(context.messages().render("generic.rate-limited"));
            return false;
        }
        return true;
    }

    private String plainText(String messageKey, Town defender) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(context.messages().render(messageKey, placeholder("defender", defender.getName())));
    }

    private String formatSeconds(long seconds) {
        return gg.afterburner.townyRaids.util.DurationFormatter.formatSeconds(seconds);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(ROOT_CHOICES);
            com.palmergames.bukkit.towny.TownyAPI api = com.palmergames.bukkit.towny.TownyAPI.getInstance();
            api.getTowns().stream().map(Town::getName).forEach(suggestions::add);
            String prefix = args[0].toLowerCase();
            return suggestions.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
