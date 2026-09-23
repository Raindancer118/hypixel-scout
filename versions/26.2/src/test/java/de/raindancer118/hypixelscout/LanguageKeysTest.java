package de.raindancer118.hypixelscout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer118.hypixelscout.config.Accent;
import de.raindancer118.hypixelscout.config.TableAnchor;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.SortMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every translation key the code asks for exists, in English and in German.
 *
 * <p>A missing key does not fail anywhere else: the game just prints the raw key on a button. This
 * reads the source for literal keys and adds the ones built from enum names by hand.
 */
class LanguageKeysTest {
	private static final Path SOURCES = Path.of("src/main/java");
	private static final Path LANG = Path.of("src/main/resources/assets/hypixelscout/lang");
	private static final Pattern LITERAL = Pattern.compile("\"((?:message|key)\\.hypixelscout\\.[a-z0-9_.]+)\"");

	private static Set<String> keys(String file) throws IOException {
		JsonObject json = JsonParser.parseString(Files.readString(LANG.resolve(file))).getAsJsonObject();
		return new HashSet<>(json.keySet());
	}

	private static Set<String> used() throws IOException {
		Set<String> used = new HashSet<>();

		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				Matcher matcher = LITERAL.matcher(Files.readString(file));
				while (matcher.find()) {
					String key = matcher.group(1);
					// A literal ending in a dot is a prefix an enum name is appended to; those are below.
					if (!key.endsWith(".")) {
						used.add(key);
					}
				}
			}
		}

		// Keybinds are registered as add("name", ...) and prefixed there.
		Matcher binding = Pattern.compile("\\badd\\(\"([a-z0-9_]+)\",").matcher(
				Files.readString(SOURCES.resolve("de/raindancer118/hypixelscout/ScoutKeys.java")));
		while (binding.find()) {
			used.add("key.hypixelscout." + binding.group(1));
		}

		for (HudMode mode : HudMode.values()) {
			used.add("message.hypixelscout.mode." + name(mode));
			used.add("message.hypixelscout.mode." + name(mode) + ".tooltip");
		}
		for (SortMode sort : SortMode.values()) {
			used.add("message.hypixelscout.sort." + name(sort));
		}
		for (Accent accent : Accent.values()) {
			used.add("message.hypixelscout.accent." + name(accent));
		}
		for (TableAnchor anchor : TableAnchor.values()) {
			used.add("message.hypixelscout.anchor." + name(anchor));
		}
		for (KeyCheck.Outcome outcome : KeyCheck.Outcome.values()) {
			used.add("message.hypixelscout.key.result." + name(outcome));
		}
		for (String page : new String[] {"game", "teams", "lookup", "queue"}) {
			used.add("message.hypixelscout.page." + page);
		}
		for (String tab : new String[] {"general", "table", "overlays", "alerts", "keys"}) {
			used.add("message.hypixelscout.settings.tab." + tab);
		}
		for (de.raindancer118.hypixelscout.core.ThreatScale.Basis basis
				: de.raindancer118.hypixelscout.core.ThreatScale.Basis.values()) {
			used.add("message.hypixelscout.threat_basis." + name(basis));
			used.add("message.hypixelscout.threat_basis." + name(basis) + ".tooltip");
			used.add("message.hypixelscout.threat_basis.short." + name(basis));
		}
		used.add("message.hypixelscout.game.look_up_now.tooltip");
		for (de.raindancer118.hypixelscout.game.PartyReport.Channel channel
				: de.raindancer118.hypixelscout.game.PartyReport.Channel.values()) {
			used.add("message.hypixelscout.teams." + name(channel));
			used.add("message.hypixelscout.teams." + name(channel) + ".tooltip");
			used.add("message.hypixelscout.teams.list." + name(channel));
			used.add("message.hypixelscout.teams.list." + name(channel) + ".tooltip");
		}

		for (String toggle : new String[] {"lobby", "hypixel_only", "group", "hide_own", "wlr", "winstreak", "beds", "beds_per_game", "kills_per_game", "age",
				"tooltip", "walls", "tab", "nametag_stars", "nametag_fkdr", "chat_hover", "nick_alert", "streak_alert", "proximity"}) {
			used.add("message.hypixelscout.settings." + toggle);
			used.add("message.hypixelscout.settings." + toggle + ".tooltip");
		}
		for (String feature : new String[] {"tab", "nametags", "tooltip", "chat_hover", "nick_alert", "proximity"}) {
			used.add("message.hypixelscout.toggle." + feature);
		}
		for (int slot = 1; slot <= 9; slot++) {
			used.add("key.hypixelscout.queue_" + slot);
		}

		return used;
	}

	private static String name(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	@Test
	void everyKeyTheCodeUsesIsInEnglish() throws IOException {
		assertThat(keys("en_us.json")).containsAll(used());
	}

	@Test
	void germanHasExactlyTheEnglishKeys() throws IOException {
		assertThat(keys("de_de.json")).isEqualTo(keys("en_us.json"));
	}
}
