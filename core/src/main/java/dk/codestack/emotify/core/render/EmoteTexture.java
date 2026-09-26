package dk.codestack.emotify.core.render;

import dk.codestack.emotify.core.emote.Emote;
import dk.codestack.emotify.core.image.DecodedImage;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.CompletableResourceLocation;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import net.labymod.laby3d.api.textures.SamplerDescription;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;
import org.jetbrains.annotations.Nullable;

public final class EmoteTexture {
  private static final SamplerDescription SAMPLER = SamplerDescription.builder()
      .setFilter(Filter.LINEAR)
      .build();

  public enum State {
    LOADING,
    READY,
    FAILED
  }

  private final Emote emote;
  private final ResourceLocation location;
  private final CompletableResourceLocation completable;
  private final Icon icon;

  private static final long RETRY_DELAY_MILLIS = 30_000L;

  private volatile State state = State.LOADING;
  private long uploadNanos;
  private volatile long failedAt;
  private DecodedImage image;
  private DynamicTexture texture;
  private int currentFrame = -1;
  private long lastRequested;

  EmoteTexture(Emote emote, ResourceLocation location, ThemeTextureLocation placeholder) {
    this.emote = emote;
    this.location = location;
    this.completable = new CompletableResourceLocation(placeholder);
    this.icon = Icon.completable(this.completable);
    this.icon.aspectRatio(emote.aspectRatio());
    this.lastRequested = System.currentTimeMillis();
  }

  public Emote emote() {
    return this.emote;
  }

  public Icon icon() {
    return this.icon;
  }

  public State state() {
    return this.state;
  }

  long uploadNanos() {
    return this.uploadNanos;
  }

  public boolean animated() {
    return this.image != null && this.image.animated();
  }

  long lastRequested() {
    return this.lastRequested;
  }

  void touch() {
    this.lastRequested = System.currentTimeMillis();
  }

  void upload(DecodedImage image) {
    if (this.state == State.FAILED || this.texture != null) {
      return;
    }

    long start = System.nanoTime();
    this.image = image;
    this.texture = new DynamicTexture(this.location, image.width(), image.height(), SAMPLER);
    this.writeFrame(0);
    this.texture.upload();

    this.texture.bindTo();
    this.currentFrame = 0;
    this.state = State.READY;
    this.uploadNanos = System.nanoTime() - start;

    this.completable.executeCompletableListeners(this.location);
  }

  void fail() {
    this.state = State.FAILED;
    this.failedAt = System.currentTimeMillis();
  }

  boolean shouldRetry(long now) {
    return this.state == State.FAILED && this.texture == null
        && now - this.failedAt >= RETRY_DELAY_MILLIS;
  }

  void retrying() {
    this.state = State.LOADING;
  }

  boolean advance(long now) {
    if (this.state != State.READY || this.image == null || !this.image.animated()) {
      return false;
    }

    int frame = this.image.frameAt(now);
    if (frame == this.currentFrame) {
      return false;
    }

    this.writeFrame(frame);
    this.texture.upload();
    this.currentFrame = frame;
    return true;
  }

  private void writeFrame(int frameIndex) {
    GameImage gameImage = this.texture.getImage();
    if (gameImage == null) {
      return;
    }

    int width = this.image.width();
    int height = this.image.height();
    int[] pixels = this.image.frame(frameIndex);
    for (int y = 0; y < height; y++) {
      int row = y * width;
      for (int x = 0; x < width; x++) {
        gameImage.setARGB(x, y, pixels[row + x]);
      }
    }
  }

  void release() {
    if (this.texture != null) {
      Laby.references().textureRepository().releaseTexture(this.location);
      this.texture.close();
      this.texture = null;
    }

    this.image = null;
    this.state = State.FAILED;
    this.failedAt = System.currentTimeMillis();
  }

  @Nullable
  DecodedImage image() {
    return this.image;
  }
}
