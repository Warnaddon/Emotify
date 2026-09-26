package dk.codestack.seventv.core.picker;

import dk.codestack.seventv.core.SevenTvAddon;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

/**
 * Opens the emote picker when the configured key (default V) is pressed in-game.
 *
 * <p>The key only fires while no screen (chat, inventory, menu, ...) is open, so typing a "v"
 * in chat never triggers it. The binding itself can only be changed in the addon settings.
 */
public final class EmotePickerOpener {

  private static final long SUPPRESS_NANOS = 250_000_000L;

  private final SevenTvAddon addon;
  private long suppressedUntil;

  public EmotePickerOpener(SevenTvAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    Key key = this.openKey();
    if (event.state() != State.PRESS || key == Key.NONE || event.key() != key) {
      return;
    }

    if (!this.addon.configuration().enabled().get()) {
      return;
    }

    Minecraft minecraft = Laby.labyAPI().minecraft();
    if (System.nanoTime() < this.suppressedUntil
        || !minecraft.isIngame()
        || minecraft.minecraftWindow().isScreenOpened()) {
      return;
    }

    this.open();
  }

  public void open() {
    Laby.labyAPI().minecraft().minecraftWindow().displayScreen(new EmotePickerActivity(this.addon, this));
  }

  Key openKey() {
    return this.addon.configuration().pickerKey().get();
  }

  /** The key that closed the picker must not immediately reopen it. */
  void suppressOpen() {
    this.suppressedUntil = System.nanoTime() + SUPPRESS_NANOS;
  }
}
