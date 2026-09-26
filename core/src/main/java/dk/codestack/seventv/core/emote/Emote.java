package dk.codestack.seventv.core.emote;

import dk.codestack.seventv.core.i18n.Texts;
import java.util.Locale;
import java.util.Objects;
import net.labymod.api.client.component.Component;

/**
 * Immutable description of one 7TV emote as it appears inside an emote set.
 *
 * <p>Only the data the addon actually needs is kept here; the full 7TV JSON is thrown away after
 * parsing so the registry stays small even with several thousand emotes loaded.
 */
public final class Emote {

  /** 7TV emote flag: sexually suggestive content. */
  public static final int FLAG_CONTENT_SEXUAL = 1 << 16;
  /** 7TV emote flag: rapid flashing (epilepsy warning). */
  public static final int FLAG_CONTENT_EPILEPSY = 1 << 17;
  /** 7TV emote flag: edgy / distasteful, may be offensive. */
  public static final int FLAG_CONTENT_EDGY = 1 << 18;
  /** 7TV emote flag: not allowed on Twitch. */
  public static final int FLAG_CONTENT_TWITCH_DISALLOWED = 1 << 24;
  /** 7TV emote flag: private emote (only usable by its owner). */
  public static final int FLAG_PRIVATE = 1;
  /** 7TV emote flag: recommended to be rendered as zero-width overlay. */
  public static final int FLAG_ZERO_WIDTH = 1 << 8;

  private final String id;
  private final String name;
  private final String lowerName;
  private final String ownerName;
  private final String setId;
  private final String setName;
  private final boolean animated;
  private final boolean listed;
  private final int flags;
  private final String hostUrl;
  private final int width;
  private final int height;
  private final int frameCount;

  /** Texture cache keys per quality scale, built once. */
  private final String textureKey1x;
  private final String textureKey2x;

  public Emote(
      String id,
      String name,
      String ownerName,
      String setId,
      String setName,
      boolean animated,
      boolean listed,
      int flags,
      String hostUrl,
      int width,
      int height,
      int frameCount
  ) {
    this.id = id;
    this.name = name;
    this.lowerName = name.toLowerCase(Locale.ROOT);
    this.ownerName = ownerName;
    this.setId = setId;
    this.setName = setName;
    this.animated = animated;
    this.listed = listed;
    this.flags = flags;
    this.hostUrl = hostUrl;
    this.width = width;
    this.height = height;
    this.frameCount = frameCount;
    this.textureKey1x = id + ":1";
    this.textureKey2x = id + ":2";
  }

  public String id() {
    return this.id;
  }

  /** The name used in chat, e.g. {@code PogChamp} for {@code :PogChamp:}. Aliases are respected. */
  public String name() {
    return this.name;
  }

  public String lowerName() {
    return this.lowerName;
  }

  public String ownerName() {
    return this.ownerName;
  }

  public String setId() {
    return this.setId;
  }

  public String setName() {
    return this.setName;
  }

  /** Prefix of set names that are translation keys rather than names from the 7TV API. */
  public static final String TRANSLATED_SET_PREFIX = "seventv.set.";

  /** The set name as shown to the user: translated for the built-in sets, verbatim otherwise. */
  public static Component setNameComponent(String setName) {
    if (setName.startsWith(TRANSLATED_SET_PREFIX)) {
      return Texts.translatable(setName);
    }

    return Component.text(setName);
  }

  public Component setNameComponent() {
    return setNameComponent(this.setName);
  }

  /** Key under which the texture for this emote at the given scale is cached. */
  public String textureKey(int scale) {
    return scale == 1 ? this.textureKey1x : this.textureKey2x;
  }

  public boolean animated() {
    return this.animated;
  }

  public boolean listed() {
    return this.listed;
  }

  public int flags() {
    return this.flags;
  }

  public boolean hasFlag(int flag) {
    return (this.flags & flag) != 0;
  }

  /** Width of the 1x variant in pixels. */
  public int width() {
    return this.width;
  }

  /** Height of the 1x variant in pixels. */
  public int height() {
    return this.height;
  }

  public int frameCount() {
    return this.frameCount;
  }

  public float aspectRatio() {
    if (this.height <= 0 || this.width <= 0) {
      return 1.0F;
    }

    return (float) this.width / (float) this.height;
  }

  /** The chat token for this emote, e.g. {@code :PogChamp:}. */
  public String token() {
    return ":" + this.name + ":";
  }

  /**
   * Builds the CDN url for the given scale (1-4). 7TV serves WEBP and AVIF only; the addon decodes
   * WEBP itself.
   */
  public String imageUrl(int scale) {
    String base = this.hostUrl;
    if (base.startsWith("//")) {
      base = "https:" + base;
    }

    return base + "/" + scale + "x.webp";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Emote emote)) {
      return false;
    }

    return this.id.equals(emote.id) && this.name.equals(emote.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id, this.name);
  }

  @Override
  public String toString() {
    return "Emote{" + this.name + " (" + this.id + ") from " + this.setName + "}";
  }
}
