package dk.codestack.emotify.core.config;

public enum PopularEmoteCount {
  OFF(0),
  TOP100(100),
  TOP250(250),
  TOP500(500),
  TOP1000(1000);

  private final int count;

  PopularEmoteCount(int count) {
    this.count = count;
  }

  public int count() {
    return this.count;
  }
}
