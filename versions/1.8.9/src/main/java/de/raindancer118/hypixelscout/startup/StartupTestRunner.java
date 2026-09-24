package de.raindancer118.hypixelscout.startup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Drives {@link StartupCheck}s one at a time, tick by tick, and turns the outcome into exactly two
 * things the {@code runClientStartupTest} Gradle task can check without parsing game internals: a
 * process exit code (0 pass, 1 fail) and a result file next to the run directory.
 *
 * <p>Only wired up (see the {@code hypixelscout.startupTest} system property check at the call
 * site) when actually running the smoke test — normal play never touches this class.
 */
public final class StartupTestRunner {

	private static final Logger LOGGER = LogManager.getLogger("HypixelScout/StartupTest");

	/**
	 * Belt-and-braces alongside the Gradle task's own {@code timeout}: Gradle's kill leaves no
	 * result file behind (the JVM is destroyed, not exited), which the task treats as a failure
	 * anyway but with no reason recorded. Failing this way first writes why. 5 minutes at 20
	 * ticks/s — generous for a cold dev-client boot, short enough to not stall CI badly.
	 */
	private static final int WATCHDOG_TICKS = 5 * 60 * 20;

	private final List<StartupCheck> steps;
	private final File resultFile;

	private int index;
	private int ticksElapsed;
	private boolean finished;

	public StartupTestRunner(File gameDir, StartupCheck... steps) {
		this.steps = Arrays.asList(steps);
		this.resultFile = new File(gameDir, "startupTest/result.txt");
	}

	/** Call once per client tick. No-op once the runner has finished (either way). */
	public void tick() {
		if (finished) {
			return;
		}

		ticksElapsed++;
		if (ticksElapsed > WATCHDOG_TICKS) {
			fail(steps.get(index).name(), new IllegalStateException(
					"watchdog: no step progress within " + WATCHDOG_TICKS + " ticks"));
			return;
		}

		StartupCheck current = steps.get(index);
		try {
			boolean done = current.tick();
			if (!done) {
				return;
			}

			LOGGER.info("startup test step passed: {}", current.name());
			index++;
			if (index >= steps.size()) {
				pass();
			}
		} catch (Exception e) {
			fail(current.name(), e);
		}
	}

	private void pass() {
		finished = true;
		writeResult("PASS: all " + steps.size() + " startup test step(s) completed");
		LOGGER.info("startup test PASSED — exiting 0");
		FMLCommonHandler.instance().exitJava(0, false);
	}

	private void fail(String stepName, Exception cause) {
		finished = true;
		LOGGER.error("startup test step failed: " + stepName, cause);
		writeResult("FAIL: step '" + stepName + "' threw " + cause);
		FMLCommonHandler.instance().exitJava(1, false);
	}

	private void writeResult(String content) {
		try {
			File file = resultFile;
			file.getParentFile().mkdirs();
			// First line: the id Gradle handed this launch, so a result left over from an earlier
			// run is never mistaken for this one's.
			String runId = System.getProperty("hypixelscout.startupTest.runId", "unknown");
			Files.write(file.toPath(), ("run " + runId + "\n" + content).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// The exit code alone still tells the Gradle task pass/fail; the missing file just
			// loses the reason, which is only ever read by a human debugging a red build.
			LOGGER.error("could not write startup test result file", e);
		}
	}

	/** The run directory the game was launched with — where the result file is written. */
	public static File gameDir() {
		return Minecraft.getMinecraft().mcDataDir;
	}
}
