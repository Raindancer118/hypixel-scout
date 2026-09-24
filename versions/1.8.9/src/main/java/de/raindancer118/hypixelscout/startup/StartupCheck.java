package de.raindancer118.hypixelscout.startup;

/**
 * One step of the client-startup smoke test ({@code runClientStartupTest}). Forge 1.8.9 has no
 * client-gametest API, so {@link StartupTestRunner} drives a small ordered list of these, one tick
 * at a time, instead.
 *
 * <p>Keep steps here narrow and independent (this one waits for the main menu and screenshots it)
 * so later work can append more — a fake login, a HUD screenshot, whatever the next port step
 * needs proven — without touching the runner or the earlier steps.
 */
public interface StartupCheck {

	/** Human-readable name for logging and the result file. */
	String name();

	/**
	 * Called once per client tick while this is the active step.
	 *
	 * @return {@code true} once this step has finished and the runner should move to the next
	 *     one (or, if this was the last step, report success and exit).
	 * @throws Exception on any failure; the runner treats this as a fatal test failure — exit
	 *     code 1, no need to catch anything defensively inside a step.
	 */
	boolean tick() throws Exception;
}
