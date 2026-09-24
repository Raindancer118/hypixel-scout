package de.raindancer118.hypixelscout.ui.widget;

/** A plain push button: a label and a {@link Runnable}, the 1.8.9 shape of 26.2's {@code Button.builder}. */
public final class ActionButton extends ThemedButton implements Clickable {
	private final Runnable action;

	public ActionButton(int id, int x, int y, int width, int height, String text, Runnable action) {
		super(id, x, y, width, height, text);
		this.action = action;
	}

	@Override
	public void onClick() {
		action.run();
	}
}
