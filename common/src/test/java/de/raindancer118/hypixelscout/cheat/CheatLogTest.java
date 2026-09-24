package de.raindancer118.hypixelscout.cheat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheatLogTest {
	@TempDir
	Path dir;

	/** 2026-09-24 10:00:00 UTC. */
	private long now = Instant.parse("2026-09-24T10:00:00Z").toEpochMilli();

	private CheatLog log(long maxBytes) {
		return new CheatLog(dir, () -> now, Runnable::run, ZoneOffset.UTC, maxBytes);
	}

	private List<JsonObject> lines() throws IOException {
		return Files.readAllLines(dir.resolve("cheats-2026-09-24.jsonl"), StandardCharsets.UTF_8).stream()
				.map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
	}

	@Test
	void aRoundIsItsStartItsSightingsItsFlagsAndASummary() throws IOException {
		CheatLog log = log(CheatLog.DEFAULT_MAX_BYTES);
		log.roundStarted("BEDWARS_FOUR_FOUR", "Lighthouse");
		log.record(new Violation("Sundial", Check.REACH, "4.1 blocks", 120), 0.3, 100);
		log.record(Violation.relief("Sundial", Check.REACH, 130), 0.3, 100);
		log.record(Violation.relief("Sundial", Check.REACH, 140), 0.3, 100);
		log.flagged(new Suspicion.Flag("Sundial", Check.REACH, 4, "4.1 blocks", 0.76), 0.76);
		log.roundEnded();

		List<JsonObject> lines = lines();
		assertThat(lines).extracting(line -> line.get("event").getAsString())
				.containsExactly("round", "sighting", "flag", "summary");

		JsonObject round = lines.get(0);
		assertThat(round.get("mode").getAsString()).isEqualTo("BEDWARS_FOUR_FOUR");
		assertThat(round.get("map").getAsString()).isEqualTo("Lighthouse");
		assertThat(round.get("time").getAsString()).isEqualTo("2026-09-24T10:00:00Z");

		JsonObject sighting = lines.get(1);
		assertThat(sighting.get("player").getAsString()).isEqualTo("Sundial");
		assertThat(sighting.get("check").getAsString()).isEqualTo("REACH");
		assertThat(sighting.get("detail").getAsString()).isEqualTo("4.1 blocks");
		assertThat(sighting.get("tick").getAsLong()).isEqualTo(120);
		assertThat(sighting.get("confidence").getAsDouble()).isEqualTo(0.3);
		assertThat(sighting.get("sensitivity").getAsInt()).isEqualTo(100);
		assertThat(sighting.get("round").getAsInt()).isEqualTo(round.get("round").getAsInt());

		JsonObject flag = lines.get(2);
		assertThat(flag.get("count").getAsInt()).isEqualTo(4);
		assertThat(flag.get("confidence").getAsDouble()).isEqualTo(0.76);

		JsonObject summary = lines.get(3);
		JsonObject reach = summary.getAsJsonObject("checks").getAsJsonObject("REACH");
		assertThat(reach.get("sightings").getAsInt()).isEqualTo(1);
		assertThat(reach.get("reliefs").getAsInt()).isEqualTo(2);
	}

	@Test
	void aVerdictSaysWhetherTheFlagWasRightAndWhatWasFlaggedThen() throws IOException {
		CheatLog log = log(CheatLog.DEFAULT_MAX_BYTES);
		log.verdict("Sundial", null, false, List.of(new Suspicion.Flag("Sundial", Check.REACH, 4, "4.1 blocks", 0.76)), 0.76);
		log.verdict("Brick", Check.FLY, true, List.of(), 0.4);

		List<JsonObject> lines = lines();
		assertThat(lines.get(0).get("event").getAsString()).isEqualTo("verdict");
		assertThat(lines.get(0).get("cheating").getAsBoolean()).isFalse();
		assertThat(lines.get(0).has("check")).isFalse();
		assertThat(lines.get(0).getAsJsonArray("flags").get(0).getAsJsonObject().get("check").getAsString()).isEqualTo("REACH");
		assertThat(lines.get(1).get("check").getAsString()).isEqualTo("FLY");
		assertThat(lines.get(1).get("cheating").getAsBoolean()).isTrue();
	}

	@Test
	void aNewDayIsANewFile() throws IOException {
		CheatLog log = log(CheatLog.DEFAULT_MAX_BYTES);
		log.roundStarted("BEDWARS_EIGHT_ONE", "Aquarium");
		now += 24L * 3600 * 1000;
		log.roundStarted("BEDWARS_EIGHT_ONE", "Aquarium");

		assertThat(dir.resolve("cheats-2026-09-24.jsonl")).exists();
		assertThat(dir.resolve("cheats-2026-09-25.jsonl")).exists();
	}

	@Test
	void aFullDayStopsWritingInsteadOfFillingTheDisk() throws IOException {
		CheatLog log = log(600);
		for (int i = 0; i < 50; i++) {
			log.record(new Violation("Sundial", Check.SPEED, "15.2 blocks/s", i), 0.3, 100);
		}
		assertThat(Files.size(dir.resolve("cheats-2026-09-24.jsonl"))).isLessThanOrEqualTo(600);
	}

	@Test
	void anUnwritableDirectoryIsSwallowedNotThrown() throws IOException {
		Path file = dir.resolve("not-a-directory");
		Files.writeString(file, "x");
		CheatLog log = new CheatLog(file, () -> now, Runnable::run, ZoneOffset.UTC, CheatLog.DEFAULT_MAX_BYTES);
		log.roundStarted("BEDWARS_EIGHT_ONE", "Aquarium");
		assertThat(log.problem()).isNotNull();
	}
}
