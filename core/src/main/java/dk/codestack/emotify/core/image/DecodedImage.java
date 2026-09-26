package dk.codestack.emotify.core.image;

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

  public int[] frame(int index) {
    return this.frames[index];
  }

  public int duration(int index) {
    return this.durations[index];
  }

  public int totalDuration() {
    return this.totalDuration;
  }

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
