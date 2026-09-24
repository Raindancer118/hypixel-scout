package de.raindancer118.hypixelscout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every translation key the code asks for exists, in English and in German.
 *
 * <p>Mirrors {@code versions/26.2}'s {@code LanguageKeysTest}, adapted to the legacy {@code
 * .lang} format (one {@code key=value} line, no JSON) instead of {@code en_us.json}/{@code
 * de_de.json}. Unlike the 26.2 version this never hardcodes an expected key list built from enum
 * names: the feature port for this module has not landed yet (no {@code ScoutKeys}, {@code
 * HudMode}, ... classes exist here so far), so the only thing this can safely do right now is a
 * generic source scan for translation-key-shaped string literals. As soon as code lands that
 * calls {@code I18n.format("hypixelscout.xxx", ...)} or {@code
 * StatCollector.translateToLocal(...)}, this test starts actually checking it — no changes needed
 * here.
 *
 * <p>A missing key does not fail anywhere else: the game just prints the raw key on a button.
 */
class LanguageKeysTest {
	private static final Path SOURCES = Paths.get("src/main/java");
	private static final Path LANG = Paths.get("src/main/resources/assets/hypixelscout/lang");

	// Every real key in this mod is "message.hypixelscout.xxx" or "key.hypixelscout.xxx" (the
	// same shape versions/26.2 uses) - mirrors that module's LITERAL pattern exactly.
	private static final Pattern LITERAL = Pattern.compile("\"((?:message|key)\\.hypixelscout\\.[a-z0-9_.]+)\"");

	private static Set<String> keys(String file) throws IOException {
		Set<String> keys = new LinkedHashSet<>();
		for (String line : Files.readAllLines(LANG.resolve(file), StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			keys.add(line.substring(0, eq));
		}
		return keys;
	}

	/**
	 * Every translation-key-shaped string literal found anywhere under {@code src/main/java}.
	 * Deliberately generic (no enum-name-derived keys, no hardcoded list): this module's feature
	 * code has not been ported yet, so there is nothing to derive those from. A literal ending in
	 * a dot is a prefix something else gets appended to at runtime and is skipped, same as
	 * versions/26.2 does.
	 */
	private static Set<String> used() throws IOException {
		Set<String> used = new LinkedHashSet<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java"))
					.collect(Collectors.toList());
			for (Path file : javaFiles) {
				String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				Matcher matcher = LITERAL.matcher(source);
				while (matcher.find()) {
					String key = matcher.group(1);
					if (!key.endsWith(".")) {
						used.add(key);
					}
				}
			}
		}
		return used;
	}

	@Test
	void everyKeyTheCodeUsesIsInEnglish() throws IOException {
		Set<String> english = keys("en_US.lang");
		Set<String> used = used();
		assertThat(english)
				.as("keys referenced from src/main/java but missing from en_US.lang: %s",
						used.stream().filter(key -> !english.contains(key)).collect(Collectors.toList()))
				.containsAll(used);
	}

	@Test
	void germanHasExactlyTheEnglishKeys() throws IOException {
		Set<String> english = keys("en_US.lang");
		Set<String> german = keys("de_DE.lang");
		assertThat(german)
				.as("en_US.lang keys missing from de_DE.lang: %s, extra in de_DE.lang: %s",
						english.stream().filter(key -> !german.contains(key)).collect(Collectors.toList()),
						german.stream().filter(key -> !english.contains(key)).collect(Collectors.toList()))
				.isEqualTo(english);
	}
}
