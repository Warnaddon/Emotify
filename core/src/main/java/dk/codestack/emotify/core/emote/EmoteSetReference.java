package dk.codestack.emotify.core.emote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public final class EmoteSetReference {
  private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{24,26}$");
  private static final Pattern SET_URL_PATTERN = Pattern.compile(
      "7tv\\.app/emote-sets/([A-Za-z0-9]{24,26})", Pattern.CASE_INSENSITIVE);
  private static final Pattern USER_URL_PATTERN = Pattern.compile(
      "7tv\\.app/users/([A-Za-z0-9]{24,26})", Pattern.CASE_INSENSITIVE);
  private static final Pattern TWITCH_PATTERN = Pattern.compile(
      "^twitch:([0-9]{1,20})$", Pattern.CASE_INSENSITIVE);

  public enum Type {
    EMOTE_SET,
    USER,
    TWITCH_USER
  }

  private final Type type;
  private final String value;
  private final String raw;

  private EmoteSetReference(Type type, String value, String raw) {
    this.type = type;
    this.value = value;
    this.raw = raw;
  }

  public Type type() {
    return this.type;
  }

  public String value() {
    return this.value;
  }

  public String raw() {
    return this.raw;
  }

  public static @Nullable EmoteSetReference parse(String input) {
    if (input == null) {
      return null;
    }

    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return null;
    }

    Matcher twitch = TWITCH_PATTERN.matcher(trimmed);
    if (twitch.find()) {
      return new EmoteSetReference(Type.TWITCH_USER, twitch.group(1), trimmed);
    }

    Matcher setUrl = SET_URL_PATTERN.matcher(trimmed);
    if (setUrl.find()) {
      return new EmoteSetReference(Type.EMOTE_SET, setUrl.group(1), trimmed);
    }

    Matcher userUrl = USER_URL_PATTERN.matcher(trimmed);
    if (userUrl.find()) {
      return new EmoteSetReference(Type.USER, userUrl.group(1), trimmed);
    }

    if (ID_PATTERN.matcher(trimmed).matches()) {
      return new EmoteSetReference(Type.EMOTE_SET, trimmed, trimmed);
    }

    return null;
  }

  public static List<EmoteSetReference> parseAll(String input) {
    List<EmoteSetReference> references = new ArrayList<>();
    if (input == null || input.isBlank()) {
      return references;
    }

    String[] parts = input.split("[,;\\s]+");
    for (String part : parts) {
      EmoteSetReference reference = parse(part);
      if (reference != null) {
        references.add(reference);
      }
    }

    return references;
  }

  @Override
  public String toString() {
    return this.type.name().toLowerCase(Locale.ROOT) + ":" + this.value;
  }
}
