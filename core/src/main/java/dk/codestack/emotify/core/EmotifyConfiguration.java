package dk.codestack.emotify.core;

import dk.codestack.emotify.core.config.LanguageOption;
import dk.codestack.emotify.core.config.EmoteQuality;
import dk.codestack.emotify.core.config.FilterConfiguration;
import dk.codestack.emotify.core.config.InsertMode;
import dk.codestack.emotify.core.config.PopularEmoteCount;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget.TextFieldSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.MethodOrder;

@ConfigName("settings")
public class EmotifyConfiguration extends AddonConfig {
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @DropdownSetting
  private final ConfigProperty<LanguageOption> language =
      ConfigProperty.createEnum(LanguageOption.AUTO);

  @SettingSection("sources")
  @SwitchSetting
  private final ConfigProperty<Boolean> useGlobalSet = new ConfigProperty<>(true);

  @DropdownSetting
  private final ConfigProperty<PopularEmoteCount> popularEmotes =
      ConfigProperty.createEnum(PopularEmoteCount.TOP500);

  @TextFieldSetting(maxLength = 2048)
  private final ConfigProperty<String> extraEmoteSets = new ConfigProperty<>("");

  @SettingSection("rendering")
  @DropdownSetting
  private final ConfigProperty<EmoteQuality> quality = ConfigProperty.createEnum(EmoteQuality.X2);

  @SwitchSetting
  private final ConfigProperty<Boolean> animated = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> caseInsensitive = new ConfigProperty<>(true);

  @SliderSetting(min = 1, max = 30)
  private final ConfigProperty<Integer> maxEmotesPerMessage = new ConfigProperty<>(10);

  @SliderSetting(min = 32, max = 1024, steps = 32)
  private final ConfigProperty<Integer> maxCachedEmotes = new ConfigProperty<>(256);

  @SettingSection("picker")
  @KeyBindSetting
  private final ConfigProperty<Key> pickerKey = new ConfigProperty<>(Key.V);

  @DropdownSetting
  private final ConfigProperty<InsertMode> insertMode = ConfigProperty.createEnum(InsertMode.SEND);

  @SwitchSetting
  private final ConfigProperty<Boolean> tabCompletion = new ConfigProperty<>(true);

  @SettingSection("filter")
  private final FilterConfiguration filter = new FilterConfiguration();

  private final ConfigProperty<String> recentEmotes = new ConfigProperty<>("");

  @MethodOrder(after = "tabCompletion")
  @ButtonSetting
  public void reloadEmotes(Setting setting) {
    EmotifyAddon addon = EmotifyAddon.instance();
    if (addon != null) {
      addon.reload(true);
    }
  }

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<LanguageOption> language() {
    return this.language;
  }

  public ConfigProperty<Boolean> useGlobalSet() {
    return this.useGlobalSet;
  }

  public ConfigProperty<PopularEmoteCount> popularEmotes() {
    return this.popularEmotes;
  }

  public ConfigProperty<String> extraEmoteSets() {
    return this.extraEmoteSets;
  }

  public ConfigProperty<EmoteQuality> quality() {
    return this.quality;
  }

  public ConfigProperty<Boolean> animated() {
    return this.animated;
  }

  public ConfigProperty<Boolean> caseInsensitive() {
    return this.caseInsensitive;
  }

  public ConfigProperty<Integer> maxEmotesPerMessage() {
    return this.maxEmotesPerMessage;
  }

  public ConfigProperty<Integer> maxCachedEmotes() {
    return this.maxCachedEmotes;
  }

  public ConfigProperty<Key> pickerKey() {
    return this.pickerKey;
  }

  public ConfigProperty<InsertMode> insertMode() {
    return this.insertMode;
  }

  public ConfigProperty<Boolean> tabCompletion() {
    return this.tabCompletion;
  }

  public FilterConfiguration filter() {
    return this.filter;
  }

  public ConfigProperty<String> recentEmotes() {
    return this.recentEmotes;
  }
}
