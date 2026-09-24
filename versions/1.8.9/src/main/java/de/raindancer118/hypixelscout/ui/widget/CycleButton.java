package de.raindancer118.hypixelscout.ui.widget;

import net.minecraft.util.StatCollector;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One value out of a fixed list, advanced by clicking — the 1.8.9 shape of 26.2's {@code
 * CycleButton}. Shows as {@code Caption: Value}, exactly like vanilla's own on/off buttons.
 */
public final class CycleButton<T> extends ThemedButton implements Clickable {
	private final String captionKey;
	private final Object[] captionArgs;
	private final T[] values;
	private final Function<T, String> label;
	private final Consumer<T> onChange;
	private int index;

	public CycleButton(int id, int x, int y, int width, int height, String captionKey, T[] values, T initial,
			Function<T, String> label, Consumer<T> onChange) {
		this(id, x, y, width, height, captionKey, NO_ARGS, values, initial, label, onChange);
	}

	/** With arguments for the caption, for a slot number or similar — {@code "Slot %s"} and so on. */
	public CycleButton(int id, int x, int y, int width, int height, String captionKey, Object[] captionArgs,
			T[] values, T initial, Function<T, String> label, Consumer<T> onChange) {
		super(id, x, y, width, height, "");
		this.captionKey = captionKey;
		this.captionArgs = captionArgs;
		this.values = values;
		this.label = label;
		this.onChange = onChange;
		this.index = indexOf(values, initial);
		refreshText();
	}

	private static final Object[] NO_ARGS = new Object[0];

	/** An on/off switch, worded the same way vanilla's own toggle buttons are. */
	public static CycleButton<Boolean> onOff(int id, int x, int y, int width, int height, String captionKey,
			boolean initial, Consumer<Boolean> onChange) {
		return new CycleButton<Boolean>(id, x, y, width, height, captionKey, new Boolean[] {Boolean.FALSE, Boolean.TRUE},
				initial, new Function<Boolean, String>() {
					@Override
					public String apply(Boolean value) {
						return StatCollector.translateToLocal(value ? "options.on" : "options.off");
					}
				}, onChange);
	}

	private static <T> int indexOf(T[] values, T initial) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == null ? initial == null : values[i].equals(initial)) {
				return i;
			}
		}
		return 0;
	}

	public T value() {
		return values[index];
	}

	@Override
	public void onClick() {
		index = (index + 1) % values.length;
		refreshText();
		onChange.accept(values[index]);
	}

	private void refreshText() {
		String caption = captionArgs.length == 0 ? StatCollector.translateToLocal(captionKey)
				: StatCollector.translateToLocalFormatted(captionKey, captionArgs);
		displayString = caption + ": " + label.apply(values[index]);
	}
}
