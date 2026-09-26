package dk.codestack.seventv.core.config;

/** Which CDN variant is downloaded. 2x looks crisp on GUI scale 2+, 1x saves bandwidth/VRAM. */
public enum EmoteQuality {
  X1(1),
  X2(2);

  private final int scale;

  EmoteQuality(int scale) {
    this.scale = scale;
  }

  public int scale() {
    return this.scale;
  }
}
