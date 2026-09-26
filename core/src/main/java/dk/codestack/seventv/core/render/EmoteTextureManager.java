package dk.codestack.seventv.core.render;

import dk.codestack.seventv.core.SevenTvConfiguration;
import dk.codestack.seventv.core.api.SevenTvApi;
import dk.codestack.seventv.core.emote.Emote;
import dk.codestack.seventv.core.image.DecodedImage;
import dk.codestack.seventv.core.image.WebPDecoder;
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

/**
 * Owns every emote texture. Downloads + decodes on the addon executor, uploads on the render
 * thread, animates on the game tick and evicts the least recently used textures when the cache
 * limit is reached.
 *
 * <p>Downloads go through a small queue: at most {@link #MAX_CONCURRENT_DOWNLOADS} run at once,
 * the newest request first (that is what the player is looking at). Emotes the picker expects to
 * need soon are {@link #prefetch(Emote) prefetched} in the background with lower priority. Raw
 * WEBP bytes are cached on disk so a restart doesn't hit the CDN again.
 */
public final class EmoteTextureManager {

  private static final Logging LOGGER = Logging.create(EmoteTextureManager.class);

  /** Placeholder shown until an emote is loaded: assets/seventv/themes/vanilla/textures/placeholder.png. */
  private static final ThemeTextureLocation PLACEHOLDER = ThemeTextureLocation.of(
      "seventv:placeholder", 16, 16);
  /** The Emotify logo: assets/seventv/themes/vanilla/textures/logo.png. */
  private static final ThemeTextureLocation LOGO = ThemeTextureLocation.of("seventv:logo", 64, 64);

  /** Upper bound for texture uploads per tick so a chat full of animated emotes can't stutter. */
  private static final int MAX_UPLOADS_PER_TICK = 24;
  private static final int MAX_ANIMATION_FRAMES = 64;

  /** Downloads running at the same time. */
  private static final int MAX_CONCURRENT_DOWNLOADS = 8;
  /** Of those, how many may be low priority prefetches while urgent requests are waiting. */
  private static final int MAX_BACKGROUND_DOWNLOADS = 3;
  private static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_BASE_DELAY_MILLIS = 750L;

  private final SevenTvConfiguration configuration;
  private final SevenTvApi api;
  private final Executor executor;
  private final Map<String, EmoteTexture> textures = new ConcurrentHashMap<>();
  private final @Nullable Path cacheDirectory;

  /** Urgent downloads (something on screen needs them), newest first. */
  private final Deque<Runnable> pending = new ArrayDeque<>();
  /** Prefetches (probably needed soon), oldest first. */
  private final Deque<Runnable> background = new ArrayDeque<>();
  private int inFlight;
  private int backgroundInFlight;

  /** Decoded images waiting for the render thread; drained in {@link #onTick}. */
  private final ConcurrentLinkedQueue<Runnable> uploads = new ConcurrentLinkedQueue<>();
  private long lastStatsLog;
  private long slowestUploadNanos;

  public EmoteTextureManager(SevenTvConfiguration configuration, SevenTvApi api, Executor executor) {
    this.configuration = configuration;
    this.api = api;
    this.executor = executor;
    this.cacheDirectory = createCacheDirectory();
  }

  private static @Nullable Path createCacheDirectory() {
    try {
      Path directory = Constants.Files.FILE_CACHE.resolve("seventv");
      Files.createDirectories(directory);
      return directory;
    } catch (Exception exception) {
      LOGGER.warn("7TV emote disk cache disabled: " + exception.getMessage());
      return null;
    }
  }

  /**
   * Returns an icon for the emote, starting the download if needed. Cheap and safe to call from
   * the chat event: it never blocks.
   */
  public Icon icon(Emote emote) {
    return this.texture(emote, false).icon();
  }

  /** Starts loading the emote with low priority, e.g. for the next page of the picker. */
  public void prefetch(Emote emote) {
    this.texture(emote, true);
  }

  /**
   * Current state of the emote's texture, or {@code null} if it is not loaded (never requested
   * or evicted). Lets the picker re-request evicted emotes.
   */
  public EmoteTexture.@Nullable State state(Emote emote) {
    int scale = this.configuration.quality().get().scale();
    EmoteTexture texture = this.textures.get(emote.textureKey(scale));
    return texture == null ? null : texture.state();
  }

  /** The addon logo, shown in the picker header. */
  public Icon logoIcon() {
    return Icon.texture(LOGO);
  }

  /** Neutral square shown in the picker for emotes that have not been requested yet. */
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
        "seventv",
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

  /** Starts queued downloads until the concurrency limits are reached. */
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
            // Network error: try again a bit later.
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

  /** Raw WEBP bytes: from the disk cache when present, otherwise downloaded and cached. */
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
      // A stale cache file is harmless; it is overwritten on the next download.
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

  /** Uploads finished decodes and drives animations. Runs on the render thread. */
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

  /** Creates GPU textures for decoded emotes, a limited number per tick. */
  private void drainUploads() {
    int done = 0;
    Runnable upload;
    while (done < MAX_UPLOADS_PER_TICK && (upload = this.uploads.poll()) != null) {
      upload.run();
      done++;
    }
  }

  /** One line every 10 s while something is going on, so slow loading can be diagnosed. */
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

  /** Frees everything. Called when emotes are reloaded or the addon is disabled. */
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
