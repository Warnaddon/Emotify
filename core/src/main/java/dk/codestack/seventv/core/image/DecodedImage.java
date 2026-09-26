package dk.codestack.seventv.core.image;

/**
 * A fully decoded (and, for animations, fully composited) emote image. Every frame has the size of
 * the canvas so uploading it to a texture is a plain memcpy.
 */
public final class DecodedImage {

  private final int width;
  private final int height;
  private final int[][] frames;
  private final int[] durations;
  private final int totalDuration;

  public DecodedImage(int width, int height, int[][] frames, int[] durations) {
    this.width = width;
    this.height = height;
    this.frames = frames;
    this.durations = durations;

    int total = 0;
    for (int duration : durations) {
      total += duration;
    }

    this.totalDuration = Math.max(1, total);
  }

  public int width() {
    return this.width;
  }

  public int height() {
    return this.height;
  }

  public int frameCount() {
    return this.frames.length;
  }

  public boolean animated() {
    return this.frames.length > 1;
  }

  /** ARGB pixels of the given frame, row-major, {@code width * height} entries. */
  public int[] frame(int index) {
    return this.frames[index];
  }

  /** Duration of the given frame in milliseconds. */
  public int duration(int index) {
    return this.durations[index];
  }

  /** Length of one animation loop in milliseconds. */
  public int totalDuration() {
    return this.totalDuration;
  }

  /** Maps a point in time (ms) to the frame that should be visible. */
  public int frameAt(long timeMillis) {
    if (this.frames.length <= 1) {
      return 0;
    }

    long position = Math.floorMod(timeMillis, (long) this.totalDuration);
    for (int i = 0; i < this.durations.length; i++) {
      position -= this.durations[i];
      if (position < 0) {
        return i;
      }
    }

    return this.durations.length - 1;
  }
}
