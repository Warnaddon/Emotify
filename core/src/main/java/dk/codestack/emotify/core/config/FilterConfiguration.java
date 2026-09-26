package dk.codestack.emotify.core.config;

import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget.TextFieldSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.property.ConfigProperty;

public class FilterConfiguration extends Config {
  @SwitchSetting
  private final ConfigProperty<Boolean> blockEdgy = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> blockEpilepsy = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> blockTwitchDisallowed = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> blockUnlisted = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> blockZeroWidth = new ConfigProperty<>(false);

  @SwitchSetting
  private final ConfigProperty<Boolean> useBuiltInBlocklist = new ConfigProperty<>(true);

  @TextFieldSetting
  private final ConfigProperty<String> customBlocklist = new ConfigProperty<>("");

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
