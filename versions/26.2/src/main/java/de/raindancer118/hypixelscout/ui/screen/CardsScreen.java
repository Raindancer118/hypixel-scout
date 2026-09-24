package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.CardField;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * What every player card shows, and how big it is: the look tooltip, the proximity popup and the
 * chat hover each with their own fields — switched on and put in order, so many to a line — star,
 * rank, face and size; then the parts of the profile and the peek overlay's size. One scrolling list,
 * which keeps its place when a field moves.
 */
public final class CardsScreen extends Screen {
	private static final int WIDE = 150;
	private static final int SMALL = 20;

	private final HypixelScout mod;
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private ScrollableLayout body;
	private double scroll;

	public CardsScreen(HypixelScout mod, Screen parent) {
		super(Component.translatable("message.hypixelscout.cards.title"));
		this.mod = mod;
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addToHeader(new StringWidget(title, font), LayoutSettings::alignHorizontallyCenter);

		ScoutSettings.Cards cards = mod.settings().cards;
		GridLayout grid = new GridLayout().columnSpacing(10).rowSpacing(4);
		GridLayout.RowHelper rows = grid.createRowHelper(2);

		card(rows, "tooltip", cards.tooltip, CardLines.Layout.TOOLTIP, true);
		card(rows, "popup", cards.popup, CardLines.Layout.POPUP, true);
		card(rows, "hover", cards.hover, CardLines.Layout.TOOLTIP, false);

		section(rows, "message.hypixelscout.cards.profile");
		ScoutSettings.Profile profile = cards.profile;
		rows.addChild(new SettingSlider(0, 0, WIDE, "message.hypixelscout.cards.peek_size", ScoutSettings.MIN_SCALE,
				ScoutSettings.MAX_SCALE, cards.peekScale, value -> cards.peekScale = Math.round(value * 20) / 20.0,
				value -> String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0)));
		rows.addChild(toggle("message.hypixelscout.cards.part.threat", profile.threat, value -> profile.threat = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.facts", profile.facts, value -> profile.facts = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.last_login", profile.lastLogin, value -> profile.lastLogin = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.combat", profile.combat, value -> profile.combat = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.games", profile.games, value -> profile.games = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.beds", profile.beds, value -> profile.beds = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.pace", profile.pace, value -> profile.pace = value));
		rows.addChild(toggle("message.hypixelscout.cards.part.socials", profile.socials, value -> profile.socials = value));

		body = new ScrollableLayout(minecraft, grid, layout.getContentHeight());
		layout.addToContents(body);
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		scrollArea().ifPresent(area -> area.setScrollAmount(scroll));
	}

	/** One card's section: size, fields a line, star, rank, face, a reset — then every field. */
	private void card(GridLayout.RowHelper rows, String key, ScoutSettings.Card card, CardLines.Layout defaults,
			boolean sizeable) {
		section(rows, "message.hypixelscout.cards." + key);
		if (sizeable) {
			rows.addChild(new SettingSlider(0, 0, WIDE, "message.hypixelscout.cards.size", ScoutSettings.MIN_SCALE,
					ScoutSettings.MAX_SCALE, card.scale, value -> card.scale = Math.round(value * 20) / 20.0,
					value -> String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0)));
		}
		rows.addChild(new SettingSlider(0, 0, WIDE, "message.hypixelscout.cards.per_line", 1, 6, card.perLine,
				value -> card.perLine = (int) Math.round(value), value -> String.valueOf(Math.round(value))));
		rows.addChild(toggle("message.hypixelscout.cards.stars", card.stars, value -> card.stars = value));
		rows.addChild(toggle("message.hypixelscout.cards.rank", card.rank, value -> card.rank = value));
		if (sizeable) {
			rows.addChild(toggle("message.hypixelscout.cards.head", card.head, value -> card.head = value));
		}
		rows.addChild(Button.builder(Component.translatable("message.hypixelscout.cards.reset"), button -> {
			ScoutSettings.Card fresh = ScoutSettings.Card.of(defaults);
			card.fields = fresh.fields;
			card.perLine = fresh.perLine;
			card.stars = true;
			card.rank = true;
			card.head = true;
			card.scale = 1.0;
			refresh();
		}).width(WIDE).build());
		if (!sizeable) {
			rows.addChild(new StringWidget(Component.empty(), font));
		}

		// The chosen fields in their order first, then the rest to switch on.
		List<CardField> order = new ArrayList<>(card.fields);
		for (CardField field : CardField.values()) {
			if (!order.contains(field)) {
				order.add(field);
			}
		}
		for (CardField field : order) {
			boolean on = card.fields.contains(field);
			String name = "message.hypixelscout.card.field." + field.name().toLowerCase(Locale.ROOT);
			rows.addChild(CycleButton.onOffBuilder(on)
					.withTooltip(value -> Tooltip.create(Component.translatable(name + ".tooltip")))
					.create(0, 0, WIDE, 20, Component.translatable(name), (button, value) -> {
						card.fields.remove(field);
						if (value) {
							card.fields.add(field);
						}
						refresh();
					}));
			LinearLayout move = LinearLayout.horizontal().spacing(4);
			Button up = Button.builder(Component.literal("↑"), button -> {
				move(card.fields, field, -1);
				refresh();
			}).width(SMALL).tooltip(Tooltip.create(Component.translatable("message.hypixelscout.cards.up"))).build();
			Button down = Button.builder(Component.literal("↓"), button -> {
				move(card.fields, field, 1);
				refresh();
			}).width(SMALL).tooltip(Tooltip.create(Component.translatable("message.hypixelscout.cards.down"))).build();
			int index = card.fields.indexOf(field);
			up.active = on && index > 0;
			down.active = on && index < card.fields.size() - 1;
			move.addChild(up);
			move.addChild(down);
			rows.addChild(move, rows.newCellSettings().alignHorizontallyLeft());
		}
	}

	private static void move(List<CardField> fields, CardField field, int by) {
		int index = fields.indexOf(field);
		int target = index + by;
		if (index >= 0 && target >= 0 && target < fields.size()) {
			Collections.swap(fields, index, target);
		}
	}

	/** Rebuilds the list in place, keeping where it was scrolled to. */
	private void refresh() {
		scroll = scrollArea().map(AbstractScrollArea::scrollAmount).orElse(0.0);
		rebuildWidgets();
	}

	private java.util.Optional<AbstractScrollArea> scrollArea() {
		return children().stream().filter(AbstractScrollArea.class::isInstance).map(AbstractScrollArea.class::cast).findFirst();
	}

	private void section(GridLayout.RowHelper rows, String key) {
		rows.addChild(new StringWidget(Component.translatable(key).withStyle(ChatFormatting.GOLD), font),
				2, rows.newCellSettings().alignHorizontallyCenter().paddingTop(6));
	}

	private CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> set) {
		return CycleButton.onOffBuilder(initial).create(0, 0, WIDE, 20, Component.translatable(key),
				(button, value) -> set.accept(value));
	}

	@Override
	protected void repositionElements() {
		body.arrangeElements();
		body.setMaxHeight(layout.getContentHeight());
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		mod.saveSettings();
		minecraft.gui.setScreen(parent);
	}
}
