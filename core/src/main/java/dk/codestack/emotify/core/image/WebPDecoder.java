package dk.codestack.emotify.core.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class WebPDecoder {
  private static final int RIFF = 0x52494646;
  private static final int WEBP = 0x57454250;
  private static final int VP8X = 0x56503858;
  private static final int ANMF = 0x414E4D46;

  private static final int MIN_FRAME_DURATION = 20;
  private static final int DEFAULT_FRAME_DURATION = 100;

  private WebPDecoder() {
  }

  public static DecodedImage decode(byte[] bytes, int maxFrames) throws IOException {
    Container container = parseContainer(bytes);

    ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      reader.setInput(input, false, true);

      if (container.frames.isEmpty()) {
        BufferedImage image = reader.read(0);
        int[] pixels = toArgb(image);
        return new DecodedImage(
            image.getWidth(),
            image.getHeight(),
            new int[][]{pixels},
            new int[]{DEFAULT_FRAME_DURATION}
        );
      }

      int available = reader.getNumImages(true);
      int frameCount = Math.min(available, container.frames.size());
      if (frameCount <= 0) {
        throw new IOException("Animated WebP without decodable frames");
      }

      int canvasWidth = container.canvasWidth;
      int canvasHeight = container.canvasHeight;
      int[] canvas = new int[canvasWidth * canvasHeight];
      List<int[]> composited = new ArrayList<>(frameCount);
      List<Integer> durations = new ArrayList<>(frameCount);

      FrameInfo previous = null;
      for (int i = 0; i < frameCount; i++) {
        FrameInfo info = container.frames.get(i);
        if (previous != null && previous.disposeToBackground) {
          clearRect(canvas, canvasWidth, canvasHeight, previous);
        }

        BufferedImage frameImage = reader.read(i);
        drawFrame(canvas, canvasWidth, canvasHeight, info, frameImage);

        composited.add(canvas.clone());
        durations.add(info.duration);
        previous = info;
      }

      return thin(canvasWidth, canvasHeight, composited, durations, maxFrames);
    } finally {
      reader.dispose();
    }
  }

  private static DecodedImage thin(
      int width,
      int height,
      List<int[]> frames,
      List<Integer> durations,
      int maxFrames
  ) {
    int count = frames.size();
    if (maxFrames < 1) {
      maxFrames = 1;
    }

    if (count <= maxFrames) {
      int[][] frameArray = new int[count][];
      int[] durationArray = new int[count];
      for (int i = 0; i < count; i++) {
        frameArray[i] = frames.get(i);
        durationArray[i] = durations.get(i);
      }

      return new DecodedImage(width, height, frameArray, durationArray);
    }

    List<int[]> keptFrames = new ArrayList<>(maxFrames);
    List<Integer> keptDurations = new ArrayList<>(maxFrames);
    double step = (double) count / (double) maxFrames;
    int pending = 0;
    int nextKeep = 0;
    for (int i = 0; i < count; i++) {
      pending += durations.get(i);
      if (i >= nextKeep && keptFrames.size() < maxFrames) {
        keptFrames.add(frames.get(i));
        keptDurations.add(pending);
        pending = 0;
        nextKeep = (int) Math.round((keptFrames.size()) * step);
      }
    }

    if (pending > 0 && !keptDurations.isEmpty()) {
      int last = keptDurations.size() - 1;
      keptDurations.set(last, keptDurations.get(last) + pending);
    }

    int[][] frameArray = new int[keptFrames.size()][];
    int[] durationArray = new int[keptFrames.size()];
    for (int i = 0; i < keptFrames.size(); i++) {
      frameArray[i] = keptFrames.get(i);
      durationArray[i] = keptDurations.get(i);
    }

    return new DecodedImage(width, height, frameArray, durationArray);
  }

  private static void clearRect(int[] canvas, int canvasWidth, int canvasHeight, FrameInfo info) {
    int x0 = Math.max(0, info.x);
    int y0 = Math.max(0, info.y);
    int x1 = Math.min(canvasWidth, info.x + info.width);
    int y1 = Math.min(canvasHeight, info.y + info.height);
    for (int y = y0; y < y1; y++) {
      int row = y * canvasWidth;
      for (int x = x0; x < x1; x++) {
        canvas[row + x] = 0;
      }
    }
  }

  private static void drawFrame(
      int[] canvas,
      int canvasWidth,
      int canvasHeight,
      FrameInfo info,
      BufferedImage frame
  ) {
    int frameWidth = frame.getWidth();
    int frameHeight = frame.getHeight();
    int[] source = toArgb(frame);

    for (int fy = 0; fy < frameHeight; fy++) {
      int cy = info.y + fy;
      if (cy < 0 || cy >= canvasHeight) {
        continue;
      }

      for (int fx = 0; fx < frameWidth; fx++) {
        int cx = info.x + fx;
        if (cx < 0 || cx >= canvasWidth) {
          continue;
        }

        int src = source[fy * frameWidth + fx];
        int index = cy * canvasWidth + cx;
        if (info.blend) {
          canvas[index] = blend(src, canvas[index]);
        } else {
          canvas[index] = src;
        }
      }
    }
  }

  private static int blend(int src, int dst) {
    int srcA = (src >>> 24);
    if (srcA == 255) {
      return src;
    }

    if (srcA == 0) {
      return dst;
    }

    int dstA = (dst >>> 24);
    int outA = srcA + dstA * (255 - srcA) / 255;
    if (outA == 0) {
      return 0;
    }

    int r = channel(src >> 16, dst >> 16, srcA, dstA, outA);
    int g = channel(src >> 8, dst >> 8, srcA, dstA, outA);
    int b = channel(src, dst, srcA, dstA, outA);
    return (outA << 24) | (r << 16) | (g << 8) | b;
  }

  private static int channel(int src, int dst, int srcA, int dstA, int outA) {
    int s = src & 0xFF;
    int d = dst & 0xFF;
    return (s * srcA + d * dstA * (255 - srcA) / 255) / outA;
  }

  private static int[] toArgb(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    return image.getRGB(0, 0, width, height, null, 0, width);
  }

  static Container parseContainer(byte[] bytes) throws IOException {
    if (bytes.length < 12 || readFourCC(bytes, 0) != RIFF || readFourCC(bytes, 8) != WEBP) {
      throw new IOException("Not a WebP file");
    }

    Container container = new Container();
    int offset = 12;
    while (offset + 8 <= bytes.length) {
      int fourCC = readFourCC(bytes, offset);
      long chunkSize = readUInt32LE(bytes, offset + 4);
      int payload = offset + 8;
      if (chunkSize < 0 || payload + chunkSize > bytes.length) {
        break;
      }

      if (fourCC == VP8X && chunkSize >= 10) {
        container.canvasWidth = 1 + readUInt24LE(bytes, payload + 4);
        container.canvasHeight = 1 + readUInt24LE(bytes, payload + 7);
      } else if (fourCC == ANMF && chunkSize >= 16) {
        FrameInfo info = new FrameInfo();
        info.x = 2 * readUInt24LE(bytes, payload);
        info.y = 2 * readUInt24LE(bytes, payload + 3);
        info.width = 1 + readUInt24LE(bytes, payload + 6);
        info.height = 1 + readUInt24LE(bytes, payload + 9);
        int duration = readUInt24LE(bytes, payload + 12);
        info.duration = duration < MIN_FRAME_DURATION ? DEFAULT_FRAME_DURATION : duration;
        int flags = bytes[payload + 15] & 0xFF;
        info.disposeToBackground = (flags & 0x01) != 0;
        info.blend = (flags & 0x02) == 0;
        container.frames.add(info);
      }

      offset = (int) (payload + chunkSize + (chunkSize & 1L));
    }

    if (!container.frames.isEmpty() && (container.canvasWidth <= 0 || container.canvasHeight <= 0)) {
      for (FrameInfo info : container.frames) {
        container.canvasWidth = Math.max(container.canvasWidth, info.x + info.width);
        container.canvasHeight = Math.max(container.canvasHeight, info.y + info.height);
      }
    }

    return container;
  }

  private static int readFourCC(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 24)
        | ((bytes[offset + 1] & 0xFF) << 16)
        | ((bytes[offset + 2] & 0xFF) << 8)
        | (bytes[offset + 3] & 0xFF);
  }

  private static long readUInt32LE(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFFL)
        | ((bytes[offset + 1] & 0xFFL) << 8)
        | ((bytes[offset + 2] & 0xFFL) << 16)
        | ((bytes[offset + 3] & 0xFFL) << 24);
  }

  private static int readUInt24LE(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF)
        | ((bytes[offset + 1] & 0xFF) << 8)
        | ((bytes[offset + 2] & 0xFF) << 16);
  }

  static final class Container {
    int canvasWidth;
    int canvasHeight;
    final List<FrameInfo> frames = new ArrayList<>();
  }

  static final class FrameInfo {
    int x;
    int y;
    int width;
    int height;
    int duration;
    boolean blend;
    boolean disposeToBackground;
  }
}
