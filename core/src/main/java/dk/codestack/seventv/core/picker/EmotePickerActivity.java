package dk.codestack.seventv.core.picker;

import dk.codestack.seventv.core.i18n.Texts;
import dk.codestack.seventv.core.SevenTvAddon;
import dk.codestack.seventv.core.config.InsertMode;
import dk.codestack.seventv.core.emote.Emote;
import dk.codestack.seventv.core.emote.EmoteRegistry;
import dk.codestack.seventv.core.render.EmoteTexture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.notification.Notification;
import net.labymod.api.notification.Notification.Type;

/**
 * The emote picker screen (default key: V).
 *
 * <p>A centered panel: search box, tabs (all / animated / static), a "recently used" section and
 * the emote grid. The grid shows one page at a time ("Show more" appends the next page) so the
 * screen is usable immediately even with 1000+ emotes; the next page is prefetched in the
 * background while the player looks at the current one. Search always covers every emote.
 *
 * <p>Typing in the search box only rebuilds the emote list, not the whole screen, so the text
 * field keeps its cursor.
 */
@AutoActivity
@Link("picker.lss")
public class EmotePickerActivity extends Activity {

  private static final int MAX_RESULTS = 2000;
  private static final int COLUMNS = 10;
  /** Emotes per page: 5 rows. Small enough to load in a second or two. */
  private static final int PAGE_SIZE = COLUMNS * 5;
  private static final int MAX_RECENT = 10;
  private static final String I18N = "seventv.picker.";

  private enum Tab {
    ALL,
    ANIMATED,
    STATIC;

    boolean matches(Emote emote) {
      switch (this) {
        case ANIMATED:
          return emote.animated();
        case STATIC:
          return !emote.animated();
        default:
          return true;
      }
    }
  }

  private final SevenTvAddon addon;
  private final EmotePickerOpener opener;

  private String filter = "";
  private Tab tab = Tab.ALL;
  private int shown = PAGE_SIZE;

  private TextFieldWidget search;
  private ComponentWidget count;
  private HorizontalListWidget tabRow;
  private VerticalListWidget<Widget> body;
  private ScrollWidget scroll;

  public EmotePickerActivity(SevenTvAddon addon, EmotePickerOpener opener) {
    this.addon = addon;
    this.opener = opener;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    EmoteRegistry registry = this.addon.registry();

    FlexibleContentWidget panel = new FlexibleContentWidget();
    panel.addId("picker-panel");

    VerticalListWidget<Widget> header = new VerticalListWidget<>();
    header.addId("picker-header");

    FlexibleContentWidget titleRow = new FlexibleContentWidget();
    titleRow.addId("picker-title-row");

    IconWidget logo = new IconWidget(this.addon.textureManager().logoIcon());
    logo.addId("picker-logo");
    titleRow.addContent(logo);

    ComponentWidget title = ComponentWidget.component(Texts.translatable(I18N + "title"));
    title.addId("picker-title");
    titleRow.addFlexibleContent(title);

    this.count = ComponentWidget.empty();
    this.count.addId("picker-count");
    titleRow.addContent(this.count);

    ButtonWidget close = ButtonWidget.component(Texts.translatable(I18N + "closeButton"), this::close);
    close.addId("picker-close");
    close.setHoverComponent(Texts.translatable(I18N + "close"));
    titleRow.addContent(close);
    header.addChild(titleRow);

    this.search = new TextFieldWidget();
    this.search.addId("picker-search");
    this.search.placeholder(Texts.translatable(I18N + "search"));
    this.search.setText(this.filter);
    this.search.setCursorAtEnd();
    this.search.updateListener(text -> {
      if (!text.equals(this.filter)) {
        this.filter = text;
        this.shown = PAGE_SIZE;
        this.refreshBody(true);
      }
    });
    this.search.submitHandler(text -> {
      // Enter picks the first match - fastest possible flow.
      List<Emote> matches = this.results(registry);
      if (!matches.isEmpty()) {
        this.pick(matches.get(0));
      }
    });
    header.addChild(this.search);

    this.tabRow = new HorizontalListWidget();
    this.tabRow.addId("picker-tabs");
    this.populateTabs();
    header.addChild(this.tabRow);

    DivWidget divider = new DivWidget();
    divider.addId("picker-divider");
    header.addChild(divider);
    panel.addContent(header);

    this.body = new VerticalListWidget<>();
    this.body.addId("picker-body");
    this.populateBody(registry);

    this.scroll = new ScrollWidget(this.body);
    this.scroll.addId("picker-scroll");
    panel.addFlexibleContent(this.scroll);

    ComponentWidget hint = ComponentWidget.component(Texts.translatable(I18N + "hint"));
    hint.addId("picker-hint");
    panel.addContent(hint);

    ComponentWidget credit = ComponentWidget.component(Texts.translatable(I18N + "credit"));
    credit.addId("picker-credit");
    panel.addContent(credit);

    this.document().addChild(panel);
    this.search.setFocused(true);
  }

  private void populateTabs() {
    boolean initialized = this.scroll != null;
    for (Tab candidate : Tab.values()) {
      ComponentWidget widget = ComponentWidget.component(
          Texts.translatable(I18N + "tab." + candidate.name().toLowerCase(Locale.ROOT)));
      widget.addId(candidate == this.tab ? "picker-tab-active" : "picker-tab");
      widget.setPressable(() -> this.selectTab(candidate));
      if (initialized) {
        this.tabRow.addEntryInitialized(widget);
      } else {
        this.tabRow.addEntry(widget);
      }
    }
  }

  private void selectTab(Tab selected) {
    if (selected == this.tab) {
      return;
    }

    this.tab = selected;
    this.shown = PAGE_SIZE;
    this.tabRow.removeChildIf(widget -> true);
    this.populateTabs();
    this.refreshBody(true);
  }

  /** Rebuilds only the emote list. The search box (and its cursor) is left untouched. */
  private void refreshBody(boolean scrollToTop) {
    if (this.body == null) {
      return;
    }

    this.body.removeChildIf(widget -> true);
    this.populateBody(this.addon.registry());
    if (scrollToTop && this.scroll != null) {
      this.scroll.scrollToTop();
    }
  }

  /** Every emote matching the search text and the active tab, best matches first. */
  private List<Emote> results(EmoteRegistry registry) {
    List<Emote> matches = registry.search(this.filter, MAX_RESULTS);
    if (this.tab == Tab.ALL) {
      return matches;
    }

    List<Emote> filtered = new ArrayList<>();
    for (Emote emote : matches) {
      if (this.tab.matches(emote)) {
        filtered.add(emote);
      }
    }

    return filtered;
  }

  private void populateBody(EmoteRegistry registry) {
    boolean initialized = this.scroll != null;
    List<Widget> widgets = new ArrayList<>();

    if (this.filter.isEmpty()) {
      List<Emote> recent = this.recentEmotes(registry);
      if (!recent.isEmpty()) {
        widgets.add(this.sectionTitle(I18N + "recent"));
        widgets.add(this.grid(recent));
        widgets.add(this.sectionTitle(I18N + "all"));
      }
    }

    List<Emote> results = this.results(registry);
    this.updateCount(registry, results);

    if (results.isEmpty()) {
      ComponentWidget empty;
      if (registry.size() == 0) {
        empty = ComponentWidget.component(Texts.translatable(I18N + "noEmotesLoaded"));
      } else if (this.filter.trim().isEmpty()) {
        empty = ComponentWidget.component(Texts.translatable(I18N + "noEmotesInTab"));
      } else {
        empty = ComponentWidget.component(
            Texts.translatable(I18N + "noResults", Component.text(this.filter.trim())));
      }

      empty.addId("picker-empty");
      widgets.add(empty);
    } else {
      int visible = Math.min(this.shown, results.size());
      widgets.add(this.grid(results.subList(0, visible)));

      int remaining = results.size() - visible;
      if (remaining > 0) {
        int next = Math.min(PAGE_SIZE, remaining);
        ButtonWidget more = ButtonWidget.component(
            Texts.translatable(I18N + "showMore",
                Component.text(String.valueOf(next)),
                Component.text(String.valueOf(remaining))),
            () -> {
              this.shown += PAGE_SIZE;
              this.refreshBody(false);
            });
        more.addId("picker-more");
        widgets.add(more);

        // Warm up the next page while the player looks at this one.
        for (Emote emote : results.subList(visible, visible + next)) {
          this.addon.textureManager().prefetch(emote);
        }
      }
    }

    for (Widget widget : widgets) {
      if (initialized) {
        this.body.addChildInitialized(widget);
      } else {
        this.body.addChild(widget);
      }
    }
  }

  private void updateCount(EmoteRegistry registry, List<Emote> results) {
    Component text;
    if (this.filter.trim().isEmpty() && this.tab == Tab.ALL) {
      text = Texts.translatable(I18N + "count", Component.text(String.valueOf(registry.size())));
    } else {
      text = Texts.translatable(I18N + "results",
          Component.text(String.valueOf(results.size())),
          Component.text(String.valueOf(registry.size())));
    }

    this.count.setComponent(text.color(NamedTextColor.GRAY));
  }

  private ComponentWidget sectionTitle(String key) {
    ComponentWidget widget = ComponentWidget.component(Texts.translatable(key));
    widget.addId("picker-section");
    return widget;
  }

  /** Emotes laid out in rows of {@link #COLUMNS}. */
  private VerticalListWidget<Widget> grid(List<Emote> emotes) {
    VerticalListWidget<Widget> grid = new VerticalListWidget<>();
    grid.addId("picker-grid");

    HorizontalListWidget row = null;
    for (int i = 0; i < emotes.size(); i++) {
      if (i % COLUMNS == 0) {
        row = new HorizontalListWidget();
        row.addId("picker-row");
        grid.addChild(row);
      }

      row.addEntry(this.createEmoteWidget(emotes.get(i)));
    }

    return grid;
  }

  private IconWidget createEmoteWidget(Emote emote) {
    IconWidget widget = new LazyEmoteWidget(emote);
    widget.addId("picker-emote");
    widget.setHoverComponent(Component.empty()
        .append(Component.text(emote.name(), NamedTextColor.AQUA))
        .append(Component.newline())
        .append(emote.setNameComponent().color(NamedTextColor.GRAY)));
    widget.setPressable(() -> this.pick(emote));
    return widget;
  }

  private void close() {
    this.opener.suppressOpen();
    this.closeScreen();
  }

  private void pick(Emote emote) {
    this.rememberRecent(emote);
    this.close();

    String token = emote.token();
    InsertMode mode = this.addon.configuration().insertMode().get();
    switch (mode) {
      case CLIPBOARD:
        Laby.labyAPI().minecraft().setClipboard(token);
        Laby.labyAPI().notificationController().push(Notification.builder()
            .title(Texts.translatable("seventv.settings.name"))
            .text(Texts.translatable(I18N + "copied", Component.text(token)))
            .icon(this.addon.textureManager().icon(emote))
            .type(Type.SYSTEM)
            .build());
        return;
      case SEND:
        // Sends the emote as its own chat message right away.
        Laby.references().chatExecutor().chat(token);
        return;
      default:
        // Opens the chat screen with the token pre-typed (works for any text, not only commands).
        Laby.references().chatExecutor().suggestCommand(token + " ");
    }
  }

  private List<Emote> recentEmotes(EmoteRegistry registry) {
    List<Emote> result = new ArrayList<>();
    String stored = this.addon.configuration().recentEmotes().get();
    if (stored == null || stored.isEmpty()) {
      return result;
    }

    String[] names = stored.split(",");
    for (String name : names) {
      Emote emote = registry.find(name.trim(), false);
      if (emote != null && !result.contains(emote) && this.tab.matches(emote)) {
        result.add(emote);
      }

      if (result.size() >= MAX_RECENT) {
        break;
      }
    }

    return result;
  }

  private void rememberRecent(Emote emote) {
    List<String> names = new ArrayList<>();
    names.add(emote.name());
    String stored = this.addon.configuration().recentEmotes().get();
    if (stored != null && !stored.isEmpty()) {
      for (String name : stored.split(",")) {
        String trimmed = name.trim();
        if (!trimmed.isEmpty() && !names.contains(trimmed) && names.size() < MAX_RECENT) {
          names.add(trimmed);
        }
      }
    }

    this.addon.configuration().recentEmotes().set(String.join(",", names));
  }

  /**
   * Shows the placeholder until it is actually rendered (= scrolled into view) and only then asks
   * the texture manager for the real emote, so off-screen rows don't download anything.
   */
  private final class LazyEmoteWidget extends IconWidget {

    private final Emote emote;
    private boolean requested;
    private boolean ready;

    LazyEmoteWidget(Emote emote) {
      super(EmotePickerActivity.this.addon.textureManager().placeholderIcon());
      this.emote = emote;
    }

    @Override
    public void renderWidget(ScreenContext context) {
      EmoteTexture.State state = EmotePickerActivity.this.addon.textureManager().state(this.emote);
      // First render, or the texture was evicted from the cache meanwhile: (re)request it.
      if (!this.requested || state == null) {
        this.requested = true;
        this.ready = false;
        Icon icon = EmotePickerActivity.this.addon.textureManager().icon(this.emote);
        this.icon().set(icon);
      } else if (!this.ready && state == EmoteTexture.State.READY) {
        // Texture just arrived: recompute the icon bounds (aspect ratio) like a hover would.
        this.ready = true;
        this.handleAttributes();
      }

      super.renderWidget(context);
    }
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    // The picker key closes the picker again - unless the user is typing in the search box.
    if (key == this.opener.openKey() && (this.search == null || !this.search.isFocused())) {
      this.close();
      return true;
    }

    return super.keyPressed(key, type);
  }
}
