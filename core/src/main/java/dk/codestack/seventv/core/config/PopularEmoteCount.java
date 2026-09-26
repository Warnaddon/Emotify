package dk.codestack.seventv.core.config;

/**
 * How many of 7TV's most popular emotes (site wide, sorted by usage) are loaded in addition to the
 * global set. The global set alone is only ~45 emotes; this is what makes the picker feel full.
 */
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
