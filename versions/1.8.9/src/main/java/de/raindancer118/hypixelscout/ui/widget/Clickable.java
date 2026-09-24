package de.raindancer118.hypixelscout.ui.widget;

/**
 * A widget that reacts to being pressed.
 *
 * <p>Vanilla 1.8.9's {@code GuiButton} carries no click callback of its own — a screen dispatches
 * every press to its own {@code actionPerformed(GuiButton)} and has to work out which button fired
 * from its id. Every custom widget in this package implements this instead, so a screen's whole
 * {@code actionPerformed} is one line: {@code if (button instanceof Clickable) ((Clickable)
 * button).onClick();} — the closest this old API gets to 26.2's {@code Button.builder(...,
 * onPress)}.
 */
public interface Clickable {
	void onClick();
}
