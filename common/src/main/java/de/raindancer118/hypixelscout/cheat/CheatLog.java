package de.raindancer118.hypixelscout.cheat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.raindancer118.hypixelscout.core.Clock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Every cheat sighting, flag and verdict of the player's, one JSON object a line, into a file a day
 * ({@code cheats-2026-09-24.jsonl}) — what the thresholds get tuned by after real games.
 *
 * <p>Local only, and only while switched on. Reliefs are too many to write one by one; they are
 * counted and written with the round's summary. Writing happens on the given executor, never on the
 * caller's thread; a day's file stops growing at its size limit rather than filling the disk, and a
 * failed write is kept in {@link #problem()} instead of thrown.
 */
public final class CheatLog {
	public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;

	private static final Gson GSON = new Gson();

	private final Path dir;
	private final Clock clock;
	private final Executor executor;
	private final ZoneId zone;
	private final long maxBytes;
	private final Map<Check, int[]> counts = new EnumMap<>(Check.class);
	private int round;
	private volatile String problem;

	/**
	 * @param zone which day a line belongs to, for the file name
	 */
	public CheatLog(Path dir, Clock clock, Executor executor, ZoneId zone, long maxBytes) {
		this.dir = dir;
		this.clock = clock;
		this.executor = executor;
		this.zone = zone;
		this.maxBytes = maxBytes;
	}

	/** Where the files go. */
	public Path dir() {
		return dir;
	}

	/** The last write that failed, or {@code null}. */
	public String problem() {
		return problem;
	}

	public synchronized void roundStarted(String mode, String map) {
		round++;
		counts.clear();
		JsonObject line = line("round");
		line.addProperty("mode", mode);
		line.addProperty("map", map);
		write(line);
	}

	/** A sighting is written as it is; a relief only counted for the summary. */
	public synchronized void record(Violation violation, double confidence, int sensitivity) {
		counts.computeIfAbsent(violation.check(), check -> new int[2])[violation.relief() ? 1 : 0]++;
		if (violation.relief()) {
			return;
		}
		JsonObject line = line("sighting");
		line.addProperty("player", violation.player());
		line.addProperty("check", violation.check().name());
		line.addProperty("detail", violation.detail());
		line.addProperty("tick", violation.tick());
		line.addProperty("confidence", round(confidence));
		line.addProperty("sensitivity", sensitivity);
		write(line);
	}

	/** A flag just raised, with the player's confidence at that moment. */
	public synchronized void flagged(Suspicion.Flag flag, double confidence) {
		JsonObject line = line("flag");
		line.addProperty("player", flag.player());
		line.addProperty("check", flag.check().name());
		line.addProperty("count", flag.count());
		line.addProperty("detail", flag.detail());
		line.addProperty("confidence", round(confidence));
		write(line);
	}

	/**
	 * The player's own judgement: {@code cheating} false for a flag that was wrong, true for one that
	 * was right; {@code check} {@code null} for all of the player's flags.
	 */
	public synchronized void verdict(String player, Check check, boolean cheating, List<Suspicion.Flag> flags,
			double confidence) {
		JsonObject line = line("verdict");
		line.addProperty("player", player);
		if (check != null) {
			line.addProperty("check", check.name());
		}
		line.addProperty("cheating", cheating);
		line.addProperty("confidence", round(confidence));
		JsonArray flagged = new JsonArray();
		for (Suspicion.Flag flag : flags) {
			JsonObject entry = new JsonObject();
			entry.addProperty("check", flag.check().name());
			entry.addProperty("count", flag.count());
			entry.addProperty("detail", flag.detail());
			flagged.add(entry);
		}
		line.add("flags", flagged);
		write(line);
	}

	/** How often each check saw something, and how often it saw the legit thing, this round. */
	public synchronized void roundEnded() {
		if (counts.isEmpty()) {
			return;
		}
		JsonObject line = line("summary");
		JsonObject checks = new JsonObject();
		counts.forEach((check, count) -> {
			JsonObject entry = new JsonObject();
			entry.addProperty("sightings", count[0]);
			entry.addProperty("reliefs", count[1]);
			checks.add(check.name(), entry);
		});
		line.add("checks", checks);
		write(line);
		counts.clear();
	}

	private JsonObject line(String event) {
		JsonObject line = new JsonObject();
		line.addProperty("event", event);
		line.addProperty("time", Instant.ofEpochMilli(clock.millis()).toString());
		line.addProperty("round", round);
		return line;
	}

	private void write(JsonObject line) {
		String text = GSON.toJson(line) + "\n";
		Path file = dir.resolve("cheats-" + Instant.ofEpochMilli(clock.millis()).atZone(zone).toLocalDate() + ".jsonl");
		executor.execute(() -> append(file, text));
	}

	private void append(Path file, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		try {
			Files.createDirectories(dir);
			if (Files.exists(file) && Files.size(file) + bytes.length > maxBytes) {
				problem = String.format(Locale.ROOT, "%s is full (%d bytes)", file.getFileName(), maxBytes);
				return;
			}
			Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException e) {
			problem = "could not write " + file + ": " + e.getMessage();
		}
	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}
}
