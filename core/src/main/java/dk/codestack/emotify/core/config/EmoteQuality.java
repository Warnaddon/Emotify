package dk.codestack.emotify.core.config;

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
