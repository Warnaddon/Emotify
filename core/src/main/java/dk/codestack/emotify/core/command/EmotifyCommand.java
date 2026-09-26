package dk.codestack.emotify.core.command;

import dk.codestack.emotify.core.i18n.Texts;
import dk.codestack.emotify.core.EmotifyAddon;
import dk.codestack.emotify.core.emote.Emote;
import dk.codestack.emotify.core.emote.EmoteRegistry;
import dk.codestack.emotify.core.filter.ContentFilter.FilterReason;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.labymod.api.client.chat.command.Command;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.event.ClickEvent;
import net.labymod.api.client.component.format.NamedTextColor;

public final class EmotifyCommand extends Command {
  private static final int SEARCH_LIMIT = 15;
  private static final String I18N = "emotify.command.";

  private final EmotifyAddon addon;

  public EmotifyCommand(EmotifyAddon addon) {
    super("7tv", "emotify");
    this.addon = addon;
  }

  @Override
  public boolean execute(String prefix, String[] arguments) {
    String sub = arguments.length == 0 ? "info" : arguments[0].toLowerCase();
    switch (sub) {
      case "reload":
        this.addon.reload(true);
        return true;
      case "picker":
        if (!this.addon.configuration().enabled().get()) {
          this.displayMessage(this.prefixed(Texts.translatable(I18N + "disabled")));
          return true;
        }

        this.addon.pickerOpener().open();
        return true;
      case "search":
        this.search(arguments);
        return true;
      case "info":
      default:
        this.info();
        return true;
    }
  }

  private void info() {
    EmoteRegistry registry = this.addon.registry();
    this.displayMessage(this.prefixed(Texts.translatable(I18N + "info.loaded",
        Component.text(String.valueOf(registry.size()), NamedTextColor.AQUA),
        Component.text(String.valueOf(registry.setNames().size()), NamedTextColor.AQUA))));

    for (String setName : registry.setNames()) {
      this.displayMessage(Component.text("  - ", NamedTextColor.GRAY)
          .append(Emote.setNameComponent(setName).color(NamedTextColor.GRAY)));
    }

    Map<FilterReason, Integer> blocked = registry.blockedCounts();
    this.displayMessage(this.prefixed(Texts.translatable(I18N + "info.blocked",
        Component.text(String.valueOf(registry.blockedTotal()), NamedTextColor.RED))));
    for (Map.Entry<FilterReason, Integer> entry : blocked.entrySet()) {
      String reasonKey = I18N + "info.reason." + entry.getKey().name().toLowerCase(Locale.ROOT);
      this.displayMessage(Component.text("  - ", NamedTextColor.GRAY)
          .append(Texts.translatable(reasonKey).color(NamedTextColor.GRAY))
          .append(Component.text(": " + entry.getValue(), NamedTextColor.GRAY)));
    }

    this.displayMessage(this.prefixed(Texts.translatable(I18N + "info.textures",
        Component.text(String.valueOf(this.addon.textureManager().loadedCount()),
            NamedTextColor.AQUA))));
    this.displayMessage(this.prefixed(Texts.translatable(I18N + "info.usage")));
  }

  private void search(String[] arguments) {
    if (arguments.length < 2) {
      this.displayMessage(this.prefixed(Texts.translatable(I18N + "search.usage")));
      return;
    }

    StringBuilder query = new StringBuilder();
    for (int i = 1; i < arguments.length; i++) {
      if (i > 1) {
        query.append(' ');
      }

      query.append(arguments[i]);
    }

    List<Emote> results = this.addon.registry().search(query.toString(), SEARCH_LIMIT);
    if (results.isEmpty()) {
      this.displayMessage(this.prefixed(Texts.translatable(I18N + "search.none")));
      return;
    }

    Component line = this.prefixed(Texts.translatable(I18N + "search.results",
        Component.text(String.valueOf(results.size()), NamedTextColor.AQUA)));
    this.displayMessage(line);

    Component list = Component.empty();
    for (int i = 0; i < results.size(); i++) {
      Emote emote = results.get(i);
      if (i > 0) {
        list.append(Component.text("  "));
      }

      list.append(Component.text(emote.token(), NamedTextColor.AQUA)
          .clickEvent(ClickEvent.suggestCommand(emote.token() + " ")));
    }

    this.displayMessage(list);
  }

  private Component prefixed(Component message) {
    return Component.empty()
        .append(Component.text("[Emotify] ", NamedTextColor.DARK_PURPLE))
        .append(message.colorIfAbsent(NamedTextColor.WHITE));
  }
}
