package dk.codestack.seventv.core.emote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Something the user typed into the "extra emote sets" field, resolved into what the 7TV API needs.
 *
 * <p>Accepted inputs (comma separated in the settings):
 * <ul>
 *   <li>an emote set id ({@code 62cdd34e72a832540de95857} or ULID style {@code 01FRG0ZGSR00084PQ73P1BYDX8})</li>
 *   <li>an emote set url ({@code https://7tv.app/emote-sets/<id>})</li>
 *   <li>a 7TV user url ({@code https://7tv.app/users/<id>}) - resolves to that user's active set</li>
 *   <li>{@code twitch:<twitch user id>} - resolves the 7TV user connected to that Twitch account</li>
 * </ul>
 */
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

  /** The id to send to 7TV (emote set id, 7TV user id or Twitch user id, depending on type). */
  public String value() {
    return this.value;
  }

  public String raw() {
    return this.raw;
  }

  /** Parses a single entry. Returns {@code null} if the entry can't be understood. */
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

  /** Parses the comma/space/newline separated settings string. Unparseable entries are skipped. */
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
