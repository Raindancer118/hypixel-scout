package de.raindancer118.hypixelscout.core;

/**
 * The current time in milliseconds.
 *
 * <p>Java 8's {@code LongSupplier} would do, but this compiles to a name that says why it exists,
 * and the tests hand over a counter they control.
 */
public interface Clock {
	long millis();

	Clock SYSTEM = new Clock() {
		@Override
		public long millis() {
			return System.currentTimeMillis();
		}
	};
}
