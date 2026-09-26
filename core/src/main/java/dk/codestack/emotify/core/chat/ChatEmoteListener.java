package dk.codestack.emotify.core.chat;

import dk.codestack.emotify.core.i18n.Texts;
import dk.codestack.emotify.core.EmotifyAddon;
import dk.codestack.emotify.core.EmotifyConfiguration;
import dk.codestack.emotify.core.emote.Emote;
import dk.codestack.emotify.core.emote.EmoteRegistry;
import dk.codestack.emotify.core.render.EmoteTextureManager;
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

public final class ChatEmoteListener {
  static final Pattern TOKEN_PATTERN = Pattern.compile(
      "(?<![A-Za-z0-9]):([A-Za-z0-9_\\-]{2,64}):(?![A-Za-z0-9])");

  private static final String IGNORE_CHANGES_KEY = "ChatDuplicateMessages-IgnoreChanges";

  private final EmotifyAddon addon;

  public ChatEmoteListener(EmotifyAddon addon) {
    this.addon = addon;
  }

  @Subscribe(125)
  public void onChatReceive(ChatReceiveEvent event) {
    if (event.isCancelled()) {
      return;
    }

    EmotifyConfiguration configuration = this.addon.configuration();
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
      message = message.copy().colorIfAbsent(NamedTextColor.WHITE);
    }

    Component replaced = this.transform(message, context);
    if (context.replaced == 0) {
      return;
    }

    event.setMessage(replaced);
    event.chatMessage().metadata().set(IGNORE_CHANGES_KEY, true);
  }

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
        .append(Texts.translatable("emotify.chat.hover.set", emote.setNameComponent())
            .color(NamedTextColor.GRAY));
    if (!emote.ownerName().isEmpty()) {
      hover.append(Component.newline())
          .append(Texts.translatable("emotify.chat.hover.by", Component.text(emote.ownerName()))
              .color(NamedTextColor.GRAY));
    }

    return Component.icon(context.textureManager.icon(emote))
        .hoverEvent(HoverEvent.showText(hover));
  }

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
