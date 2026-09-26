package dk.codestack.emotify.core.config;

import org.jetbrains.annotations.Nullable;

public enum LanguageOption {
  AUTO(null),
  DANISH("da_dk"),
  ENGLISH("en_us"),
  GERMAN("de_de");

  private final @Nullable String code;

  LanguageOption(@Nullable String code) {
    this.code = code;
  }

  public @Nullable String code() {
    return this.code;
  }
}
