package gg.afterburner.townyRaids.hud;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.MessagesManager;
import gg.afterburner.townyRaids.util.TownyUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;

public final class RaidTitleManager {

    private static final Duration FADE_IN = Duration.ofMillis(500);
    private static final Duration STAY = Duration.ofSeconds(3);
    private static final Duration FADE_OUT = Duration.ofMillis(500);

    private final MessagesManager messages;

    public RaidTitleManager(MessagesManager messages) {
        this.messages = Objects.requireNonNull(messages);
    }

    public void sendDeclaration(Town attacker, Town defender) {
        TagResolver resolver = TagResolver.resolver(
                MessagesManager.placeholder("attacker", attacker.getName()),
                MessagesManager.placeholder("defender", defender.getName())
        );
        Title attackerTitle = buildTitle(
                messages.render("raid.preparation-title-attacker.title", resolver),
                messages.render("raid.preparation-title-attacker.subtitle", resolver));
        Title defenderTitle = buildTitle(
                messages.render("raid.preparation-title-defender.title", resolver),
                messages.render("raid.preparation-title-defender.subtitle", resolver));
        TownyUtil.onlineResidents(attacker).forEach(p -> p.showTitle(attackerTitle));
        TownyUtil.onlineResidents(defender).forEach(p -> p.showTitle(defenderTitle));
    }

    public void sendBattleEnded(Town attacker, Town defender) {
        TagResolver resolver = TagResolver.resolver(
                MessagesManager.placeholder("attacker", attacker.getName()),
                MessagesManager.placeholder("defender", defender.getName())
        );
        Title title = buildTitle(
                messages.render("raid.battle-ended-title.title", resolver),
                messages.render("raid.battle-ended-title.subtitle", resolver));
        TownyUtil.onlineResidents(attacker).forEach(p -> p.showTitle(title));
        TownyUtil.onlineResidents(defender).forEach(p -> p.showTitle(title));
    }

    public void sendCancelled(Town attacker, Town defender) {
        TagResolver resolver = TagResolver.resolver(
                MessagesManager.placeholder("attacker", attacker.getName()),
                MessagesManager.placeholder("defender", defender.getName())
        );
        Title title = buildTitle(messages.render("raid.cancelled-title", resolver), Component.empty());
        TownyUtil.onlineResidents(attacker).forEach(p -> p.showTitle(title));
        TownyUtil.onlineResidents(defender).forEach(p -> p.showTitle(title));
    }

    private Title buildTitle(Component title, Component subtitle) {
        return Title.title(title, subtitle, Title.Times.times(FADE_IN, STAY, FADE_OUT));
    }

    public void sendTo(Player player, Component title, Component subtitle) {
        player.showTitle(buildTitle(title, subtitle));
    }
}
