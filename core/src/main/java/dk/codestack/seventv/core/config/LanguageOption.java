package dk.codestack.seventv.core.config;

import org.jetbrains.annotations.Nullable;

/**
 * Language of the addon's own texts (picker, chat hover, commands, notifications). {@link #AUTO}
 * follows the game language. The settings screen itself is rendered by LabyMod and always follows
 * the game language.
 */
public enum LanguageOption {
  AUTO(null),
  DANISH("da_dk"),
  ENGLISH("en_us"),
  GERMAN("de_de");

  private final @Nullable String code;

  LanguageOption(@Nullable String code) {
    this.code = code;
  }

  /** Minecraft style language code, or {@code null} for the game language. */
  public @Nullable String code() {
    return this.code;
  }
}
