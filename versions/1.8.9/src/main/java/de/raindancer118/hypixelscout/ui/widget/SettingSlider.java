package de.raindancer118.hypixelscout.ui.widget;

import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * A slider over one numeric setting, labelled with its own live value — the 1.8.9 shape of 26.2's
 * {@code ui.widget.SettingSlider} ({@code AbstractSliderButton}, which this platform has no
 * equivalent of). Built "GuiSlider-style" on {@link ThemedButton} rather than on {@code
 * net.minecraft.client.gui.GuiSlider} itself: that class exists here too, but several of its own
 * methods kept raw {@code func_...} names even under the {@code stable} MCP mapping set, which is
 * one more thing to get wrong for no benefit — a plain button with its own drag handling is exactly
 * as much code and entirely under this module's control.
 */
public final class SettingSlider extends ThemedButton {
	private final String labelKey;
	private final double min;
	private final double max;
	private final DoubleConsumer apply;
	private final DoubleFunction<String> format;
	/** 0..1 across the track. */
	private double position;
	private boolean dragging;

	public SettingSlider(int id, int x, int y, int width, int height, String labelKey, double min, double max,
			double initial, DoubleConsumer apply, DoubleFunction<String> format) {
		super(id, x, y, width, height, "");
		this.labelKey = labelKey;
		this.min = min;
		this.max = max;
		this.apply = apply;
		this.format = format;
		this.position = clamp((initial - min) / (max - min));
		updateMessage();
	}

	private static double clamp(double value) {
		return value < 0 ? 0 : value > 1 ? 1 : value;
	}

	private double actual() {
		return min + position * (max - min);
	}

	private void updateMessage() {
		displayString = StatCollector.translateToLocalFormatted(labelKey, format.apply(actual()));
	}

	@Override
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		if (!enabled || !visible || !isMouseOver(mouseX, mouseY)) {
			return false;
		}

		dragging = true;
		setFromMouse(mouseX);
		return true;
	}

	@Override
	protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
		if (dragging && enabled) {
			setFromMouse(mouseX);
		}
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY) {
		dragging = false;
	}

	private boolean isMouseOver(int mouseX, int mouseY) {
		return mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
	}

	private void setFromMouse(int mouseX) {
		position = clamp((mouseX - (xPosition + 4)) / (double) (width - 8));
		apply.accept(actual());
		updateMessage();
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}

		this.hovered = isMouseOver(mouseX, mouseY);
		ScoutTheme.panel(xPosition, yPosition, width, height, enabled ? (hovered ? 92 : 75) : 40);

		int fillWidth = (int) Math.round(position * (width - 4));
		if (fillWidth > 0) {
			ScoutTheme.rounded(xPosition + 2, yPosition + 2, fillWidth, height - 4,
					enabled ? ScoutTheme.accent(0x70) : ScoutTheme.HOVER);
		}

		drawLabel(mc.fontRendererObj);
	}

	@Override
	protected void drawLabel(FontRenderer font) {
		int colour = enabled ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT;
		ScoutTheme.textCentred(font, ScoutTheme.fit(font, displayString, width - 6), xPosition + width / 2,
				yPosition + (height - 8) / 2, colour);
	}
}
