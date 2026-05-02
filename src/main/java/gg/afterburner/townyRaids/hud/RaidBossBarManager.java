package gg.afterburner.townyRaids.hud;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.ConfigManager;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.config.RaidSettings;
import gg.afterburner.townyRaids.raid.model.Raid;
import gg.afterburner.townyRaids.raid.model.RaidPhase;
import gg.afterburner.townyRaids.util.DurationFormatter;
import gg.afterburner.townyRaids.util.TownyUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidBossBarManager implements AutoCloseable {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessagesManager messages;
    private final Map<UUID, RaidBars> bars = new ConcurrentHashMap<>();
    private volatile BukkitTask updateTask;

    public RaidBossBarManager(Plugin plugin, ConfigManager configManager, MessagesManager messages) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.messages = Objects.requireNonNull(messages);
    }

    public synchronized void start() {
        if (updateTask != null) return;
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public synchronized void close() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        bars.values().forEach(this::hide);
        bars.clear();
    }

    public void attach(Raid raid, Town attacker, Town defender) {
        RaidSettings settings = configManager.settings();
        RaidSettings.BossBarSettings settingsForPhase = raid.phase() == RaidPhase.BATTLE
                ? settings.battleBar()
                : settings.preparationBar();
        BossBar attackerBar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(),
                1f, settingsForPhase.color(), settingsForPhase.overlay());
        BossBar defenderBar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(),
                1f, settingsForPhase.color(), settingsForPhase.overlay());
        RaidBars bundle = new RaidBars(raid.id(), raid.attackerTownId(), raid.defenderTownId(),
                attackerBar, defenderBar, raid.createdAt(), raid.phaseEndsAt(), raid.phase());
        bars.put(raid.id(), bundle);
        showToTown(attacker, attackerBar);
        showToTown(defender, defenderBar);
        updateBundle(bundle, raid);
    }

    public void updatePhase(Raid raid) {
        RaidBars bundle = bars.get(raid.id());
        if (bundle == null) return;
        RaidSettings settings = configManager.settings();
        RaidSettings.BossBarSettings settingsForPhase = raid.phase() == RaidPhase.BATTLE
                ? settings.battleBar()
                : settings.preparationBar();
        bundle.attackerBar.color(settingsForPhase.color());
        bundle.attackerBar.overlay(settingsForPhase.overlay());
        bundle.defenderBar.color(settingsForPhase.color());
        bundle.defenderBar.overlay(settingsForPhase.overlay());
        bundle.phase = raid.phase();
        bundle.phaseStart = Instant.now();
        bundle.phaseEnd = raid.phaseEndsAt();
        updateBundle(bundle, raid);
    }

    public void detach(UUID raidId) {
        RaidBars removed = bars.remove(raidId);
        if (removed == null) return;
        hide(removed);
    }

    public void onPlayerJoin(Player player) {
        bars.values().forEach(bundle -> {
            TownyUtil.townByUuid(bundle.attackerTownId).ifPresent(town -> {
                if (town.hasResident(player.getUniqueId())) {
                    player.showBossBar(bundle.attackerBar);
                }
            });
            TownyUtil.townByUuid(bundle.defenderTownId).ifPresent(town -> {
                if (town.hasResident(player.getUniqueId())) {
                    player.showBossBar(bundle.defenderBar);
                }
            });
        });
    }

    public void onPlayerQuit(Player player) {
        bars.values().forEach(bundle -> {
            player.hideBossBar(bundle.attackerBar);
            player.hideBossBar(bundle.defenderBar);
        });
    }

    private void tick() {
        Instant now = Instant.now();
        bars.values().forEach(bundle -> {
            Duration total = Duration.between(bundle.phaseStart, bundle.phaseEnd);
            Duration remaining = Duration.between(now, bundle.phaseEnd);
            float progress = total.isZero() ? 0f
                    : (float) Math.max(0, Math.min(1.0d, remaining.toMillis() / (double) total.toMillis()));
            bundle.attackerBar.progress(progress);
            bundle.defenderBar.progress(progress);

            String attackerName = TownyUtil.townByUuid(bundle.attackerTownId).map(Town::getName).orElse("?");
            String defenderName = TownyUtil.townByUuid(bundle.defenderTownId).map(Town::getName).orElse("?");
            TagResolver resolver = TagResolver.resolver(
                    MessagesManager.placeholder("attacker", attackerName),
                    MessagesManager.placeholder("defender", defenderName),
                    MessagesManager.placeholder("time", DurationFormatter.format(remaining))
            );
            String key = bundle.phase == RaidPhase.BATTLE ? "raid.battle-bossbar" : "raid.preparation-bossbar";
            bundle.attackerBar.name(messages.render(key, resolver));
            bundle.defenderBar.name(messages.render(key, resolver));
        });
    }

    private void updateBundle(RaidBars bundle, Raid raid) {
        bundle.phase = raid.phase();
        bundle.phaseEnd = raid.phaseEndsAt();
        tick();
    }

    private void showToTown(Town town, BossBar bar) {
        TownyUtil.onlineResidents(town).forEach(player -> player.showBossBar(bar));
    }

    private void hide(RaidBars bundle) {
        TownyUtil.townByUuid(bundle.attackerTownId).ifPresent(town ->
                TownyUtil.onlineResidents(town).forEach(p -> p.hideBossBar(bundle.attackerBar)));
        TownyUtil.townByUuid(bundle.defenderTownId).ifPresent(town ->
                TownyUtil.onlineResidents(town).forEach(p -> p.hideBossBar(bundle.defenderBar)));
    }

    private static final class RaidBars {
        final UUID raidId;
        final UUID attackerTownId;
        final UUID defenderTownId;
        final BossBar attackerBar;
        final BossBar defenderBar;
        Instant phaseStart;
        Instant phaseEnd;
        RaidPhase phase;

        RaidBars(UUID raidId, UUID attackerTownId, UUID defenderTownId,
                 BossBar attackerBar, BossBar defenderBar,
                 Instant phaseStart, Instant phaseEnd, RaidPhase phase) {
            this.raidId = raidId;
            this.attackerTownId = attackerTownId;
            this.defenderTownId = defenderTownId;
            this.attackerBar = attackerBar;
            this.defenderBar = defenderBar;
            this.phaseStart = phaseStart;
            this.phaseEnd = phaseEnd;
            this.phase = phase;
        }
    }
}
