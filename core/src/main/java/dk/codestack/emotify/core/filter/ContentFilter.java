package dk.codestack.emotify.core.filter;

import dk.codestack.emotify.core.config.FilterConfiguration;
import dk.codestack.emotify.core.emote.Emote;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

public final class ContentFilter {
  private static final Logging LOGGER = Logging.create(ContentFilter.class);
  private static final String BUILTIN_BLOCKLIST = "/assets/emotify/blocklist.txt";

  private final FilterConfiguration configuration;
  private final Set<String> builtInWords = new HashSet<>();

  public ContentFilter(FilterConfiguration configuration) {
    this.configuration = configuration;
    this.loadBuiltInList();
  }

  public @Nullable FilterReason check(Emote emote) {
    if (emote.hasFlag(Emote.FLAG_CONTENT_SEXUAL)) {
      return FilterReason.SEXUAL;
    }

    if (this.configuration.blockUnlisted().get() && !emote.listed()) {
      return FilterReason.UNLISTED;
    }

    if (this.configuration.blockEdgy().get() && emote.hasFlag(Emote.FLAG_CONTENT_EDGY)) {
      return FilterReason.EDGY;
    }

    if (this.configuration.blockEpilepsy().get() && emote.hasFlag(Emote.FLAG_CONTENT_EPILEPSY)) {
      return FilterReason.EPILEPSY;
    }

    if (this.configuration.blockTwitchDisallowed().get()
        && emote.hasFlag(Emote.FLAG_CONTENT_TWITCH_DISALLOWED)) {
      return FilterReason.TWITCH_DISALLOWED;
    }

    if (this.configuration.blockZeroWidth().get() && emote.hasFlag(Emote.FLAG_ZERO_WIDTH)) {
      return FilterReason.ZERO_WIDTH;
    }

    if (emote.hasFlag(Emote.FLAG_PRIVATE)) {
      return FilterReason.PRIVATE;
    }

    if (this.isAllowlisted(emote.lowerName())) {
      return null;
    }

    if (this.configuration.useBuiltInBlocklist().get()
        && containsAny(emote.name(), emote.lowerName(), this.builtInWords)) {
      return FilterReason.WORD_LIST;
    }

    List<String> custom = splitWords(this.configuration.customBlocklist().get());
    if (!custom.isEmpty() && containsAny(emote.name(), emote.lowerName(), custom)) {
      return FilterReason.WORD_LIST;
    }

    return null;
  }

  private boolean isAllowlisted(String lowerName) {
    List<String> allow = splitWords(this.configuration.customAllowlist().get());
    for (String entry : allow) {
      if (entry.equals(lowerName)) {
        return true;
      }
    }

    return false;
  }

  private static boolean containsAny(String name, String lowerName, Iterable<String> needles) {
    for (String needle : needles) {
      if (!needle.isEmpty() && matchesWord(name, lowerName, needle)) {
        return true;
      }
    }

    return false;
  }

  static boolean matchesWord(String name, String lowerName, String word) {
    int index = lowerName.indexOf(word);
    while (index >= 0) {
      if (word.length() >= 5 || isWordStart(name, index)) {
        return true;
      }

      index = lowerName.indexOf(word, index + 1);
    }

    return false;
  }

  private static boolean isWordStart(String name, int index) {
    if (index == 0) {
      return true;
    }

    char current = name.charAt(index);
    char previous = name.charAt(index - 1);
    return Character.isUpperCase(current) || !Character.isLetter(previous);
  }

  static List<String> splitWords(@Nullable String input) {
    List<String> words = new ArrayList<>();
    if (input == null || input.isBlank()) {
      return words;
    }

    String[] parts = input.split("[,;\\s]+");
    for (String part : parts) {
      String word = part.trim().toLowerCase(Locale.ROOT);
      if (word.length() >= 2) {
        words.add(word);
      }
    }

    return words;
  }

  private void loadBuiltInList() {
    try (InputStream stream = ContentFilter.class.getResourceAsStream(BUILTIN_BLOCKLIST)) {
      if (stream == null) {
        LOGGER.warn("Built-in blocklist not found at " + BUILTIN_BLOCKLIST);
        return;
      }

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(stream, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        String word = line.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty() || word.startsWith("#") || word.length() < 2) {
          continue;
        }

        this.builtInWords.add(word);
      }
    } catch (IOException exception) {
      LOGGER.error("Failed to read built-in blocklist", exception);
    }
  }

  public int builtInWordCount() {
    return this.builtInWords.size();
  }

  public enum FilterReason {
    SEXUAL,
    UNLISTED,
    EDGY,
    EPILEPSY,
    TWITCH_DISALLOWED,
    ZERO_WIDTH,
    PRIVATE,
    WORD_LIST
  }
}
