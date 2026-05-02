package gg.afterburner.townyRaids.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class MessagesManager {

    public static final List<String> BUNDLED_LANGUAGES = List.of("en", "ru", "es", "tr");
    public static final String FALLBACK_LANGUAGE = "en";

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final AtomicReference<LoadedMessages> currentRef = new AtomicReference<>();

    public MessagesManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void reload(String requestedLanguage) throws ConfigLoadException {
        ensureBundledLanguages();
        String language = normalizeLanguage(requestedLanguage);
        FileConfiguration active = loadLanguage(language);
        FileConfiguration fallback = language.equals(FALLBACK_LANGUAGE) ? active : loadBundledLanguage(FALLBACK_LANGUAGE);
        String rawPrefix = firstNonNull(active.getString("prefix"), fallback.getString("prefix"), "");
        Component prefix = miniMessage.deserialize(rawPrefix);
        currentRef.set(new LoadedMessages(language, active, fallback, prefix, rawPrefix));
    }

    public Component render(String key, TagResolver... placeholders) {
        String template = lookup(key);
        TagResolver prefixResolver = TagResolver.resolver("prefix", Tag.selfClosingInserting(currentOrThrow().prefix()));
        TagResolver combined = TagResolver.resolver(prefixResolver, TagResolver.resolver(placeholders));
        return miniMessage.deserialize(template, combined);
    }

    public List<Component> renderLines(String key, TagResolver... placeholders) {
        String template = lookup(key);
        TagResolver prefixResolver = TagResolver.resolver("prefix", Tag.selfClosingInserting(currentOrThrow().prefix()));
        TagResolver combined = TagResolver.resolver(prefixResolver, TagResolver.resolver(placeholders));
        return Arrays.stream(template.split("\n"))
                .map(line -> miniMessage.deserialize(line, combined))
                .toList();
    }

    public String activeLanguage() {
        return currentOrThrow().language();
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    private LoadedMessages currentOrThrow() {
        LoadedMessages current = currentRef.get();
        if (current == null) {
            throw new IllegalStateException("MessagesManager has not been loaded yet");
        }
        return current;
    }

    private String lookup(String key) {
        LoadedMessages current = currentOrThrow();
        Object value = current.active().get(key);
        if (value instanceof String s) {
            return s;
        }
        value = current.fallback().get(key);
        if (value instanceof String s) {
            return s;
        }
        return key;
    }

    private String normalizeLanguage(String requested) {
        if (requested == null || requested.isBlank()) {
            return FALLBACK_LANGUAGE;
        }
        return requested.trim().toLowerCase(Locale.ROOT);
    }

    private FileConfiguration loadLanguage(String language) throws ConfigLoadException {
        File langDir = new File(plugin.getDataFolder(), "lang");
        File target = new File(langDir, language + ".yml");
        if (target.exists()) {
            return YamlConfiguration.loadConfiguration(target);
        }
        FileConfiguration bundled = loadBundledLanguage(language);
        if (bundled != null) {
            return bundled;
        }
        plugin.getLogger().warning("Language file '" + language + "' not found, falling back to '" + FALLBACK_LANGUAGE + "'.");
        return loadBundledLanguage(FALLBACK_LANGUAGE);
    }

    private FileConfiguration loadBundledLanguage(String language) throws ConfigLoadException {
        String resourcePath = "lang/" + language + ".yml";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ConfigLoadException("Failed to read bundled language " + language, ex);
        }
    }

    private void ensureBundledLanguages() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().warning("Unable to create language directory: " + langDir.getAbsolutePath());
            return;
        }
        for (String lang : BUNDLED_LANGUAGES) {
            String resourcePath = "lang/" + lang + ".yml";
            File target = new File(langDir, lang + ".yml");
            if (target.exists()) {
                continue;
            }
            try (InputStream stream = plugin.getResource(resourcePath)) {
                if (stream == null) {
                    plugin.getLogger().warning("Missing bundled language resource: " + resourcePath);
                    continue;
                }
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to write bundled language " + lang, ex);
            }
        }
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return "";
    }

    public static TagResolver placeholder(String key, String value) {
        return net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed(key, value == null ? "" : value);
    }

    public static TagResolver placeholder(String key, Component value) {
        return net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(key,
                value == null ? Component.empty() : value);
    }

    public static TagResolver placeholders(Map<String, String> values) {
        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((k, v) -> builder.resolver(placeholder(k, v)));
        return builder.build();
    }

    private record LoadedMessages(String language,
                                  FileConfiguration active,
                                  FileConfiguration fallback,
                                  Component prefix,
                                  String rawPrefix) {}
}
