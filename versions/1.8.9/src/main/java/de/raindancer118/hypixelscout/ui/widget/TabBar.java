package de.raindancer118.hypixelscout.ui.widget;

import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

import java.util.function.IntConsumer;

/**
 * A row of equal-width tabs across the top of a screen, drawn through {@link ScoutTheme} — 1.8.9
 * has no {@code MenuTabBar}/{@code TabManager} (26.2's own tabbed screens use those), so this is
 * what {@code ScoutScreen} and {@code SettingsScreen} lay their pages out on instead: a header
 * strip, the selected tab lit in the accent colour with a rule under it, Ctrl+Tab (and
 * Ctrl+Shift+Tab) cycling the same way vanilla's own tabbed menus do.
 *
 * <p>Not a widget itself — a screen owns one, feeds it mouse clicks and key presses from its own
 * {@code mouseClicked}/{@code keyTyped}, and asks it where its content area starts ({@link #bottom()}).
 */
public final class TabBar {
	public static final int HEIGHT = 24;

	private final String[] labels;
	private final IntConsumer onSelect;
	private int selected;
	private int left;
	private int top;
	private int width;

	public TabBar(String[] labels, int selected, IntConsumer onSelect) {
		this.labels = labels;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	public void layout(int left, int top, int width) {
		this.left = left;
		this.top = top;
		this.width = width;
	}

	public int bottom() {
		return top + HEIGHT;
	}

	public int selected() {
		return selected;
	}

	private int tabWidth() {
		return width / labels.length;
	}

	public void draw(FontRenderer font, int mouseX, int mouseY) {
		ScoutTheme.panel(left, top, width, HEIGHT, 90);

		int tabWidth = tabWidth();
		for (int i = 0; i < labels.length; i++) {
			int x = left + i * tabWidth;
			int w = i == labels.length - 1 ? width - i * tabWidth : tabWidth;
			boolean active = i == selected;
			boolean hover = !active && mouseX >= x && mouseX < x + w && mouseY >= top && mouseY < top + HEIGHT;

			if (active) {
				ScoutTheme.rounded(x + 1, top + 1, w - 2, HEIGHT - 2, ScoutTheme.accent(0x30));
			} else if (hover) {
				ScoutTheme.rounded(x + 1, top + 1, w - 2, HEIGHT - 2, ScoutTheme.HOVER);
			}

			int colour = active ? ScoutTheme.accent() : hover ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT;
			ScoutTheme.textCentred(font, ScoutTheme.fit(font, labels[i], w - 8), x + w / 2, top + (HEIGHT - 8) / 2,
					colour);

			if (active) {
				de.raindancer118.hypixelscout.ui.ScoutTheme.divider(x + 2, top + HEIGHT - 2, w - 4);
			}
		}
	}

	/** @return whether the click landed on a tab (selected or not — either way nothing else should see it) */
	public boolean mouseClicked(int mouseX, int mouseY) {
		if (mouseY < top || mouseY >= top + HEIGHT || mouseX < left || mouseX >= left + width) {
			return false;
		}

		int index = Math.min(labels.length - 1, (mouseX - left) / tabWidth());
		select(index);
		return true;
	}

	/** Ctrl+Tab / Ctrl+Shift+Tab, as vanilla's own tabbed menus use. Returns whether it handled the key. */
	public boolean keyTyped(int keyCode) {
		if (keyCode != Keyboard.KEY_TAB || !(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
			return false;
		}

		boolean backwards = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
		int next = (selected + (backwards ? -1 : 1) + labels.length) % labels.length;
		select(next);
		return true;
	}

	private void select(int index) {
		if (index != selected) {
			selected = index;
			onSelect.accept(index);
		}
	}
}
