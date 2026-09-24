package de.raindancer118.hypixelscout.ui.widget;

import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/**
 * Base for every button this module draws: a small {@link ScoutTheme} panel with a hover
 * highlight and centred text, instead of vanilla's own grey button texture — so a screen built out
 * of these looks like the rest of the mod (and like 26.2's own cut-corner cards) rather than like a
 * stock options menu.
 *
 * <p>{@link #drawButton} is vanilla's own per-frame hook, called once per entry in {@code
 * GuiScreen.buttonList} from {@code drawScreen}; {@code hovered} is inherited from {@link
 * GuiButton} and kept in sync here since nothing else in this old API does it centrally.
 */
public abstract class ThemedButton extends GuiButton {
	protected ThemedButton(int id, int x, int y, int width, int height, String text) {
		super(id, x, y, width, height, text);
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}

		this.hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
				&& mouseY < yPosition + height;

		ScoutTheme.panel(xPosition, yPosition, width, height, enabled ? (hovered ? 92 : 75) : 40);
		if (hovered && enabled) {
			ScoutTheme.rounded(xPosition, yPosition, width, height, ScoutTheme.HOVER);
		}

		drawLabel(mc.fontRendererObj);
	}

	/** Most buttons just show their own centred label; a few (the slider) draw more first. */
	protected void drawLabel(FontRenderer font) {
		int colour = !enabled ? ScoutTheme.TEXT_FAINT : hovered ? ScoutTheme.accent() : ScoutTheme.TEXT;
		ScoutTheme.textCentred(font, ScoutTheme.fit(font, displayString, width - 6), xPosition + width / 2,
				yPosition + (height - 8) / 2, colour);
	}
}
