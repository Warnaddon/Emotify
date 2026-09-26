package dk.codestack.emotify.core.emote;

import dk.codestack.emotify.core.i18n.Texts;
import java.util.Locale;
import java.util.Objects;
import net.labymod.api.client.component.Component;

public final class Emote {
  public static final int FLAG_CONTENT_SEXUAL = 1 << 16;

  public static final int FLAG_CONTENT_EPILEPSY = 1 << 17;

  public static final int FLAG_CONTENT_EDGY = 1 << 18;

  public static final int FLAG_CONTENT_TWITCH_DISALLOWED = 1 << 24;

  public static final int FLAG_PRIVATE = 1;

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

  public static final String TRANSLATED_SET_PREFIX = "emotify.set.";

  public static Component setNameComponent(String setName) {
    if (setName.startsWith(TRANSLATED_SET_PREFIX)) {
      return Texts.translatable(setName);
    }

    return Component.text(setName);
  }

  public Component setNameComponent() {
    return setNameComponent(this.setName);
  }

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

  public int width() {
    return this.width;
  }

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

  public String token() {
    return ":" + this.name + ":";
  }

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
