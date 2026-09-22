package de.raindancer118.hypixelscout.ui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * A slider over one numeric setting, labelled with its own live value.
 *
 * <p>{@link AbstractSliderButton} works in {@code 0..1}; this maps that onto a real range so the
 * callers can think in degrees, percent and minutes.
 */
public final class SettingSlider extends AbstractSliderButton {
	private final String labelKey;
	private final double min;
	private final double max;
	private final DoubleConsumer apply;
	private final DoubleFunction<String> format;

	public SettingSlider(int x, int y, int width, String labelKey, double min, double max,
			double initial, DoubleConsumer apply, DoubleFunction<String> format) {
		super(x, y, width, 20, Component.empty(), Math.clamp((initial - min) / (max - min), 0, 1));
		this.labelKey = labelKey;
		this.min = min;
		this.max = max;
		this.apply = apply;
		this.format = format;
		updateMessage();
	}

	private double actual() {
		return min + value * (max - min);
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.translatable(labelKey, format.apply(actual())));
	}

	@Override
	protected void applyValue() {
		apply.accept(actual());
	}
}
