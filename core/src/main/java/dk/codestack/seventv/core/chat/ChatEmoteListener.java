package dk.codestack.seventv.core.chat;

import dk.codestack.seventv.core.i18n.Texts;
import dk.codestack.seventv.core.SevenTvAddon;
import dk.codestack.seventv.core.SevenTvConfiguration;
import dk.codestack.seventv.core.emote.Emote;
import dk.codestack.seventv.core.emote.EmoteRegistry;
import dk.codestack.seventv.core.render.EmoteTextureManager;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.TextComponent;
import net.labymod.api.client.component.TranslatableComponent;
import net.labymod.api.client.component.event.HoverEvent;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.Style;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.chat.ChatReceiveEvent;
import net.labymod.api.loader.MinecraftVersions;

/**
 * Replaces {@code :EmoteName:} tokens in incoming chat messages with icon components.
 *
 * <p>Design notes:
 * <ul>
 *   <li>The text that is SENT is never changed. Your own message travels to the server as plain
 *       {@code :PogChamp:}; every player with the addon renders it, everyone else just reads the
 *       text. Server chat filters therefore keep working.</li>
 *   <li>The message component tree is rebuilt, not edited in place, so styles, hover / click
 *       events and translatable arguments are preserved.</li>
 *   <li>A message without a single {@code :} is skipped with one {@code indexOf}.</li>
 * </ul>
 */
public final class ChatEmoteListener {

  /**
   * {@code :name:} where the name is 2-64 alphanumerics / _ / -. The look-arounds stop times like
   * {@code 12:30:45} or urls from being treated as emotes.
   */
  static final Pattern TOKEN_PATTERN = Pattern.compile(
      "(?<![A-Za-z0-9]):([A-Za-z0-9_\\-]{2,64}):(?![A-Za-z0-9])");

  /** Same key LabyMod's duplicate-message handler uses; tells it our edit is not a new message. */
  private static final String IGNORE_CHANGES_KEY = "ChatDuplicateMessages-IgnoreChanges";

  private final SevenTvAddon addon;

  public ChatEmoteListener(SevenTvAddon addon) {
    this.addon = addon;
  }

  @Subscribe(125)
  public void onChatReceive(ChatReceiveEvent event) {
    if (event.isCancelled()) {
      return;
    }

    SevenTvConfiguration configuration = this.addon.configuration();
    if (!configuration.enabled().get()) {
      return;
    }

    EmoteRegistry registry = this.addon.registry();
    if (registry.size() == 0) {
      return;
    }

    String plain = event.chatMessage().getPlainText();
    if (plain == null || plain.indexOf(':') < 0) {
      return;
    }

    Context context = new Context(
        registry,
        this.addon.textureManager(),
        configuration.caseInsensitive().get(),
        configuration.maxEmotesPerMessage().get()
    );

    Component message = event.message();
    if (MinecraftVersions.V1_12_2.orOlder()) {
      // Older versions treat "no color" as black in some places; keep vanilla behaviour.
      message = message.copy().colorIfAbsent(NamedTextColor.WHITE);
    }

    Component replaced = this.transform(message, context);
    if (context.replaced == 0) {
      return;
    }

    event.setMessage(replaced);
    event.chatMessage().metadata().set(IGNORE_CHANGES_KEY, true);
  }

  /** Recursively rebuilds the component with emote tokens swapped for icons. */
  Component transform(Component component, Context context) {
    if (component instanceof TextComponent text) {
      Component result = this.transformText(text, context);
      this.appendChildren(result, component, context);
      return result;
    }

    if (component instanceof TranslatableComponent translatable) {
      List<Component> arguments = translatable.getArguments();
      Component[] transformed = new Component[arguments.size()];
      for (int i = 0; i < transformed.length; i++) {
        transformed[i] = this.transform(arguments.get(i), context);
      }

      Component result = Component.translatable(translatable.getKey(), transformed)
          .style(translatable.style());
      this.appendChildren(result, component, context);
      return result;
    }

    // Keybind / score / selector / nbt components: nothing to replace, keep as-is.
    return component;
  }

  private void appendChildren(Component target, Component source, Context context) {
    List<Component> children = source.getChildren();
    for (int i = 0; i < children.size(); i++) {
      target.append(this.transform(children.get(i), context));
    }
  }

  private Component transformText(TextComponent component, Context context) {
    String text = component.getText();
    Style style = component.style();
    if (text == null || text.isEmpty() || text.indexOf(':') < 0 || context.remaining <= 0) {
      return Component.text(text == null ? "" : text).style(style);
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    Component result = null;
    int last = 0;
    while (matcher.find()) {
      if (context.remaining <= 0) {
        break;
      }

      Emote emote = context.registry.find(matcher.group(1), context.caseInsensitive);
      if (emote == null) {
        continue;
      }

      if (result == null) {
        result = Component.empty().style(style);
      }

      if (matcher.start() > last) {
        result.append(Component.text(text.substring(last, matcher.start())));
      }

      result.append(this.emoteComponent(emote, context));
      last = matcher.end();
      context.remaining--;
      context.replaced++;
    }

    if (result == null) {
      return Component.text(text).style(style);
    }

    if (last < text.length()) {
      result.append(Component.text(text.substring(last)));
    }

    return result;
  }

  private Component emoteComponent(Emote emote, Context context) {
    Component hover = Component.empty()
        .append(Component.text(emote.name(), NamedTextColor.AQUA))
        .append(Component.newline())
        .append(Texts.translatable("seventv.chat.hover.set", emote.setNameComponent())
            .color(NamedTextColor.GRAY));
    if (!emote.ownerName().isEmpty()) {
      hover.append(Component.newline())
          .append(Texts.translatable("seventv.chat.hover.by", Component.text(emote.ownerName()))
              .color(NamedTextColor.GRAY));
    }

    return Component.icon(context.textureManager.icon(emote))
        .hoverEvent(HoverEvent.showText(hover));
  }

  /** Per-message state so the emote cap is shared across the whole component tree. */
  static final class Context {

    final EmoteRegistry registry;
    final EmoteTextureManager textureManager;
    final boolean caseInsensitive;
    int remaining;
    int replaced;

    Context(EmoteRegistry registry, EmoteTextureManager textureManager, boolean caseInsensitive,
        int maxEmotes) {
      this.registry = registry;
      this.textureManager = textureManager;
      this.caseInsensitive = caseInsensitive;
      this.remaining = maxEmotes;
    }
  }
}
