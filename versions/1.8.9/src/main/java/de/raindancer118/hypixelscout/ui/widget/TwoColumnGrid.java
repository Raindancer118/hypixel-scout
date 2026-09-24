package de.raindancer118.hypixelscout.ui.widget;

/**
 * A simple two-column layout cursor. 1.8.9 has no {@code GridLayout}/{@code RowHelper} (26.2's own,
 * used by its settings/cards/cheat-checks screens); this hands out the next widget's top-left
 * corner instead, one column at a time, with a full-width row for anything that should span both —
 * a text field, a section title, a lone button.
 */
public final class TwoColumnGrid {
	private final int left;
	private final int columnWidth;
	private final int gap;
	private final int rowHeight;
	private int column;
	private int y;

	public TwoColumnGrid(int left, int top, int columnWidth, int gap, int rowHeight) {
		this.left = left;
		this.columnWidth = columnWidth;
		this.gap = gap;
		this.rowHeight = rowHeight;
		this.y = top;
	}

	public int columnWidth() {
		return columnWidth;
	}

	/** The next one-column widget's x. */
	public int x() {
		return left + column * (columnWidth + gap);
	}

	public int y() {
		return y;
	}

	/** Advances past a normal, one-column-wide widget — to the second column, or the next row. */
	public void advance() {
		if (column == 0) {
			column = 1;
		} else {
			column = 0;
			y += rowHeight;
		}
	}

	public int wideX() {
		return left;
	}

	public int wideWidth() {
		return columnWidth * 2 + gap;
	}

	/** Starts a fresh row, abandoning a lone first column if one was left half-filled. */
	public void newRow() {
		if (column != 0) {
			column = 0;
			y += rowHeight;
		}
	}

	/** After a full-width widget: always its own row. */
	public void advanceRow() {
		column = 0;
		y += rowHeight;
	}

	/** Extra vertical space, e.g. before a section title. */
	public void gap(int amount) {
		newRow();
		y += amount;
	}

	public int bottom() {
		newRow();
		return y;
	}
}
