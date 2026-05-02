package gg.afterburner.townyRaids.raid.notification;

import com.palmergames.bukkit.towny.object.Town;
import gg.afterburner.townyRaids.config.MessagesManager;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

import java.util.Objects;

public final class RaidBroadcaster {

    private final MessagesManager messages;

    public RaidBroadcaster(MessagesManager messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void declared(Town attacker, Town defender) {
        Bukkit.getServer().broadcast(messages.render("raid.declared-broadcast", resolver(attacker, defender)));
    }

    public void battleStarted(Town attacker, Town defender) {
        Bukkit.getServer().broadcast(messages.render("raid.battle-started-broadcast", resolver(attacker, defender)));
    }

    public void battleEnded(Town attacker, Town defender) {
        Bukkit.getServer().broadcast(messages.render("raid.battle-ended-broadcast", resolver(attacker, defender)));
    }

    public void cancelled(Town attacker, Town defender) {
        Bukkit.getServer().broadcast(messages.render("raid.cancelled-broadcast", resolver(attacker, defender)));
    }

    private TagResolver resolver(Town attacker, Town defender) {
        return TagResolver.resolver(
                MessagesManager.placeholder("attacker", attacker.getName()),
                MessagesManager.placeholder("defender", defender.getName())
        );
    }
}
