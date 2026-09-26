package dk.codestack.emotify.core.render;

import dk.codestack.emotify.core.EmotifyConfiguration;
import dk.codestack.emotify.core.api.SevenTvApi;
import dk.codestack.emotify.core.emote.Emote;
import dk.codestack.emotify.core.image.DecodedImage;
import dk.codestack.emotify.core.image.WebPDecoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.labymod.api.Constants;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.util.ThreadSafe;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

public final class EmoteTextureManager {
  private static final Logging LOGGER = Logging.create(EmoteTextureManager.class);

  private static final ThemeTextureLocation PLACEHOLDER = ThemeTextureLocation.of(
      "emotify:placeholder", 16, 16);

  private static final ThemeTextureLocation LOGO = ThemeTextureLocation.of("emotify:logo", 64, 64);

  private static final int MAX_UPLOADS_PER_TICK = 24;
  private static final int MAX_ANIMATION_FRAMES = 64;

  private static final int MAX_CONCURRENT_DOWNLOADS = 8;

  private static final int MAX_BACKGROUND_DOWNLOADS = 3;
  private static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_BASE_DELAY_MILLIS = 750L;

  private final EmotifyConfiguration configuration;
  private final SevenTvApi api;
  private final Executor executor;
  private final Map<String, EmoteTexture> textures = new ConcurrentHashMap<>();
  private final @Nullable Path cacheDirectory;

  private final Deque<Runnable> pending = new ArrayDeque<>();

  private final Deque<Runnable> background = new ArrayDeque<>();
  private int inFlight;
  private int backgroundInFlight;

  private final ConcurrentLinkedQueue<Runnable> uploads = new ConcurrentLinkedQueue<>();
  private long lastStatsLog;
  private long slowestUploadNanos;

  public EmoteTextureManager(EmotifyConfiguration configuration, SevenTvApi api, Executor executor) {
    this.configuration = configuration;
    this.api = api;
    this.executor = executor;
    this.cacheDirectory = createCacheDirectory();
  }

  private static @Nullable Path createCacheDirectory() {
    try {
      Path directory = Constants.Files.FILE_CACHE.resolve("emotify");
      Files.createDirectories(directory);
      return directory;
    } catch (Exception exception) {
      LOGGER.warn("7TV emote disk cache disabled: " + exception.getMessage());
      return null;
    }
  }

  public Icon icon(Emote emote) {
    return this.texture(emote, false).icon();
  }

  public void prefetch(Emote emote) {
    this.texture(emote, true);
  }

  public EmoteTexture.@Nullable State state(Emote emote) {
    int scale = this.configuration.quality().get().scale();
    EmoteTexture texture = this.textures.get(emote.textureKey(scale));
    return texture == null ? null : texture.state();
  }

  public Icon logoIcon() {
    return Icon.texture(LOGO);
  }

  public Icon placeholderIcon() {
    return Icon.texture(PLACEHOLDER);
  }

  private EmoteTexture texture(Emote emote, boolean backgroundPriority) {
    int scale = this.configuration.quality().get().scale();
    String key = emote.textureKey(scale);

    EmoteTexture existing = this.textures.get(key);
    if (existing != null) {
      existing.touch();
      if (existing.shouldRetry(System.currentTimeMillis())) {
        existing.retrying();
        this.load(existing, emote.imageUrl(scale), scale, backgroundPriority);
      }

      return existing;
    }

    ResourceLocation location = ResourceLocation.create(
        "emotify",
        "emote/" + emote.id().toLowerCase(Locale.ROOT) + "_" + scale + "x"
    );
    EmoteTexture texture = new EmoteTexture(emote, location, PLACEHOLDER);
    EmoteTexture raced = this.textures.putIfAbsent(key, texture);
    if (raced != null) {
      raced.touch();
      return raced;
    }

    this.load(texture, emote.imageUrl(scale), scale, backgroundPriority);
    return texture;
  }

  private void load(EmoteTexture texture, String url, int scale, boolean backgroundPriority) {
    Runnable task = () -> this.download(texture, url, scale, 1, backgroundPriority);
    synchronized (this.pending) {
      if (backgroundPriority) {
        this.background.addLast(task);
      } else {
        this.pending.push(task);
      }
    }

    this.pump();
  }

  private void pump() {
    List<Runnable> start = new ArrayList<>();
    synchronized (this.pending) {
      while (this.inFlight < MAX_CONCURRENT_DOWNLOADS && !this.pending.isEmpty()) {
        this.inFlight++;
        start.add(this.pending.pop());
      }

      while (this.inFlight < MAX_CONCURRENT_DOWNLOADS
          && this.backgroundInFlight < MAX_BACKGROUND_DOWNLOADS
          && !this.background.isEmpty()) {
        this.inFlight++;
        this.backgroundInFlight++;
        start.add(this.background.pollFirst());
      }
    }

    for (Runnable task : start) {
      task.run();
    }
  }

  private void finished(boolean backgroundPriority) {
    synchronized (this.pending) {
      this.inFlight--;
      if (backgroundPriority) {
        this.backgroundInFlight--;
      }
    }

    this.pump();
  }

  private void download(
      EmoteTexture texture,
      String url,
      int scale,
      int attempt,
      boolean backgroundPriority
  ) {
    boolean animate = this.configuration.animated().get();
    Path cacheFile = this.cacheFile(texture.emote(), scale);

    this.bytes(url, cacheFile)
        .thenApplyAsync(bytes -> {
          try {
            return WebPDecoder.decode(bytes, animate ? MAX_ANIMATION_FRAMES : 1);
          } catch (Exception exception) {
            this.deleteQuietly(cacheFile);
            throw new IllegalStateException("Failed to decode " + url, exception);
          }
        }, this.executor)
        .whenComplete((image, error) -> {
          this.finished(backgroundPriority);
          if (error == null) {
            this.uploads.add(() -> this.uploadSafely(texture, image));
            return;
          }

          Throwable cause = error instanceof CompletionException && error.getCause() != null
              ? error.getCause() : error;
          if (attempt < MAX_ATTEMPTS && !(cause instanceof IllegalStateException)) {
            long delay = RETRY_BASE_DELAY_MILLIS * attempt;
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, this.executor)
                .execute(() -> {
                  synchronized (this.pending) {
                    this.pending.push(() -> this.download(texture, url, scale, attempt + 1, false));
                  }

                  this.pump();
                });
            return;
          }

          LOGGER.warn("Could not load 7TV emote " + texture.emote().name() + " (attempt "
              + attempt + "): " + cause.getMessage());
          texture.fail();
        });
  }

  private CompletableFuture<byte[]> bytes(String url, @Nullable Path cacheFile) {
    if (cacheFile != null) {
      CompletableFuture<byte[]> cached = CompletableFuture.supplyAsync(() -> {
        try {
          return Files.exists(cacheFile) ? Files.readAllBytes(cacheFile) : null;
        } catch (IOException exception) {
          return null;
        }
      }, this.executor);

      return cached.thenCompose(bytes -> {
        if (bytes != null && bytes.length > 0) {
          return CompletableFuture.completedFuture(bytes);
        }

        return this.api.downloadImage(url).thenApply(downloaded -> {
          this.writeQuietly(cacheFile, downloaded);
          return downloaded;
        });
      });
    }

    return this.api.downloadImage(url);
  }

  private @Nullable Path cacheFile(Emote emote, int scale) {
    if (this.cacheDirectory == null) {
      return null;
    }

    String name = emote.id().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    if (name.isEmpty()) {
      return null;
    }

    return this.cacheDirectory.resolve(name + "_" + scale + "x.webp");
  }

  private void writeQuietly(Path file, byte[] bytes) {
    try {
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(temp, bytes);
      Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception exception) {
      LOGGER.debug("Could not cache 7TV emote: " + exception.getMessage());
    }
  }

  private void deleteQuietly(@Nullable Path file) {
    if (file == null) {
      return;
    }

    try {
      Files.deleteIfExists(file);
    } catch (Exception ignored) {
    }
  }

  private void uploadSafely(EmoteTexture texture, DecodedImage image) {
    try {
      texture.upload(image);
    } catch (Exception exception) {
      LOGGER.error("Failed to upload 7TV emote texture " + texture.emote().name(), exception);
      texture.fail();
    }
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.PRE) {
      return;
    }

    this.drainUploads();
    if (!this.textures.isEmpty()) {
      this.tick();
    }

    this.logStats();
  }

  private void drainUploads() {
    int done = 0;
    Runnable upload;
    while (done < MAX_UPLOADS_PER_TICK && (upload = this.uploads.poll()) != null) {
      upload.run();
      done++;
    }
  }

  private void logStats() {
    long now = System.currentTimeMillis();
    if (now - this.lastStatsLog < 10_000L) {
      return;
    }

    int pendingCount;
    int backgroundCount;
    int running;
    synchronized (this.pending) {
      pendingCount = this.pending.size();
      backgroundCount = this.background.size();
      running = this.inFlight;
    }

    int ready = 0;
    int loading = 0;
    int failed = 0;
    for (EmoteTexture texture : this.textures.values()) {
      switch (texture.state()) {
        case READY:
          ready++;
          this.slowestUploadNanos = Math.max(this.slowestUploadNanos, texture.uploadNanos());
          break;
        case LOADING:
          loading++;
          break;
        default:
          failed++;
      }
    }

    if (loading == 0 && pendingCount == 0 && backgroundCount == 0 && this.uploads.isEmpty()) {
      return;
    }

    this.lastStatsLog = now;
    LOGGER.info("7TV textures: ready=" + ready + " loading=" + loading + " failed=" + failed
        + " | downloads running=" + running + " queued=" + pendingCount + " prefetch="
        + backgroundCount + " | uploads waiting=" + this.uploads.size()
        + " slowest upload=" + (this.slowestUploadNanos / 1_000_000L) + "ms");
  }

  void tick() {
    if (!this.configuration.animated().get()) {
      return;
    }

    long now = System.currentTimeMillis();
    int uploads = 0;
    for (EmoteTexture texture : this.textures.values()) {
      if (uploads >= MAX_UPLOADS_PER_TICK) {
        break;
      }

      try {
        if (texture.advance(now)) {
          uploads++;
        }
      } catch (Exception exception) {
        LOGGER.error("Animation upload failed for " + texture.emote().name(), exception);
        texture.release();
      }
    }

    this.evictIfNeeded();
  }

  private void evictIfNeeded() {
    int limit = this.configuration.maxCachedEmotes().get();
    if (this.textures.size() <= limit) {
      return;
    }

    List<EmoteTexture> sorted = new ArrayList<>(this.textures.values());
    sorted.sort((a, b) -> Long.compare(a.lastRequested(), b.lastRequested()));
    int toRemove = this.textures.size() - limit;
    for (int i = 0; i < toRemove && i < sorted.size(); i++) {
      EmoteTexture texture = sorted.get(i);
      this.remove(texture);
    }
  }

  private void remove(EmoteTexture texture) {
    Iterator<Map.Entry<String, EmoteTexture>> iterator = this.textures.entrySet().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue() == texture) {
        iterator.remove();
        break;
      }
    }

    texture.release();
  }

  public void clear() {
    this.uploads.clear();
    Collection<EmoteTexture> all = new ArrayList<>(this.textures.values());
    this.textures.clear();
    ThreadSafe.executeOnRenderThread(() -> {
      for (EmoteTexture texture : all) {
        texture.release();
      }
    });
  }

  public int loadedCount() {
    return this.textures.size();
  }
}
