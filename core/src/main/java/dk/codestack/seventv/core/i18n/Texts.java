package dk.codestack.seventv.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.codestack.seventv.core.config.LanguageOption;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.labymod.api.client.component.Component;
import net.labymod.api.util.logging.Logging;

/**
 * Translations for the addon's own texts, with an optional language override.
 *
 * <p>With {@link LanguageOption#AUTO} every call returns a normal translatable component that
 * LabyMod resolves in the game language. With a fixed language the matching i18n file is read
 * from the jar and the text is resolved here instead.
 */
public final class Texts {

  private static final Logging LOGGER = Logging.create(Texts.class);
  private static final String PLACEHOLDER = "%s";

  private static volatile Map<String, String> override = Collections.emptyMap();

  private Texts() {
  }

  /** Switches the language. Safe to call from any thread. */
  public static void configure(LanguageOption option) {
    String code = option.code();
    if (code == null) {
      override = Collections.emptyMap();
      return;
    }

    Map<String, String> loaded = new HashMap<>();
    String path = "/assets/seventv/i18n/" + code + ".json";
    try (InputStream stream = Texts.class.getResourceAsStream(path)) {
      if (stream == null) {
        LOGGER.warn("No translation file for " + code);
        override = Collections.emptyMap();
        return;
      }

      JsonElement root = JsonParser.parseReader(
          new InputStreamReader(stream, StandardCharsets.UTF_8));
      flatten("", root, loaded);
    } catch (Exception exception) {
      LOGGER.warn("Could not read translation file " + path + ": " + exception.getMessage());
    }

    override = loaded;
  }

  private static void flatten(String prefix, JsonElement element, Map<String, String> target) {
    if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
        flatten(key, entry.getValue(), target);
      }
    } else if (element.isJsonPrimitive()) {
      target.put(prefix, element.getAsString());
    }
  }

  /** A translated text. {@code %s} placeholders are replaced by the arguments in order. */
  public static Component translatable(String key, Component... arguments) {
    String text = override.get(key);
    if (text == null) {
      return Component.translatable(key, arguments);
    }

    Component result = Component.empty();
    int argument = 0;
    int from = 0;
    int index;
    while ((index = text.indexOf(PLACEHOLDER, from)) >= 0) {
      if (index > from) {
        result.append(Component.text(text.substring(from, index)));
      }

      if (argument < arguments.length) {
        result.append(arguments[argument++]);
      }

      from = index + PLACEHOLDER.length();
    }

    if (from < text.length()) {
      result.append(Component.text(text.substring(from)));
    }

    return result;
  }
}
