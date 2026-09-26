package dk.codestack.seventv.core.config;

import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget.TextFieldSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.property.ConfigProperty;

/**
 * Content filter settings. Note that there is intentionally NO switch for sexual content: emotes
 * flagged by 7TV as sexually suggestive are always blocked, no matter what.
 */
public class FilterConfiguration extends Config {

  /** Block emotes 7TV flagged as edgy / distasteful. */
  @SwitchSetting
  private final ConfigProperty<Boolean> blockEdgy = new ConfigProperty<>(true);

  /** Block emotes 7TV flagged as rapidly flashing (epilepsy). */
  @SwitchSetting
  private final ConfigProperty<Boolean> blockEpilepsy = new ConfigProperty<>(true);

  /** Block emotes that are disallowed on Twitch (usually copyright or ToS problems). */
  @SwitchSetting
  private final ConfigProperty<Boolean> blockTwitchDisallowed = new ConfigProperty<>(true);

  /**
   * Block emotes that are not "listed" on 7TV. Unlisted emotes never went through 7TV moderation,
   * so this is the most important switch after the sexual-content block.
   */
  @SwitchSetting
  private final ConfigProperty<Boolean> blockUnlisted = new ConfigProperty<>(true);

  /** Block emotes marked as zero-width (overlay) emotes; they look odd rendered inline. */
  @SwitchSetting
  private final ConfigProperty<Boolean> blockZeroWidth = new ConfigProperty<>(false);

  /** Match emote names against the bundled word list (assets/seventv/blocklist.txt). */
  @SwitchSetting
  private final ConfigProperty<Boolean> useBuiltInBlocklist = new ConfigProperty<>(true);

  /** Extra words (comma separated). An emote is blocked if its name contains any of them. */
  @TextFieldSetting
  private final ConfigProperty<String> customBlocklist = new ConfigProperty<>("");

  /**
   * Exact emote names (comma separated) that are always allowed even if the word list matches.
   * Flag based blocks (sexual, unlisted, ...) are NOT bypassed by this.
   */
  @TextFieldSetting
  private final ConfigProperty<String> customAllowlist = new ConfigProperty<>("");

  public ConfigProperty<Boolean> blockEdgy() {
    return this.blockEdgy;
  }

  public ConfigProperty<Boolean> blockEpilepsy() {
    return this.blockEpilepsy;
  }

  public ConfigProperty<Boolean> blockTwitchDisallowed() {
    return this.blockTwitchDisallowed;
  }

  public ConfigProperty<Boolean> blockUnlisted() {
    return this.blockUnlisted;
  }

  public ConfigProperty<Boolean> blockZeroWidth() {
    return this.blockZeroWidth;
  }

  public ConfigProperty<Boolean> useBuiltInBlocklist() {
    return this.useBuiltInBlocklist;
  }

  public ConfigProperty<String> customBlocklist() {
    return this.customBlocklist;
  }

  public ConfigProperty<String> customAllowlist() {
    return this.customAllowlist;
  }
}
