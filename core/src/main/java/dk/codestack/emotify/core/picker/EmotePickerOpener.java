package dk.codestack.emotify.core.picker;

import dk.codestack.emotify.core.EmotifyAddon;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

public final class EmotePickerOpener {
  private static final long SUPPRESS_NANOS = 250_000_000L;

  private final EmotifyAddon addon;
  private long suppressedUntil;

  public EmotePickerOpener(EmotifyAddon addon) {
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

  void suppressOpen() {
    this.suppressedUntil = System.nanoTime() + SUPPRESS_NANOS;
  }
}
