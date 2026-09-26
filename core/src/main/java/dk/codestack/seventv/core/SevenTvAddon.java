package dk.codestack.seventv.core;

import dk.codestack.seventv.core.i18n.Texts;
import dk.codestack.seventv.core.api.SevenTvApi;
import dk.codestack.seventv.core.api.SevenTvApi.EmoteSet;
import dk.codestack.seventv.core.chat.ChatEmoteListener;
import dk.codestack.seventv.core.chat.EmoteTabCompleter;
import dk.codestack.seventv.core.command.SevenTvCommand;
import dk.codestack.seventv.core.emote.EmoteRegistry;
import dk.codestack.seventv.core.emote.EmoteSetReference;
import dk.codestack.seventv.core.filter.ContentFilter;
import dk.codestack.seventv.core.picker.EmotePickerOpener;
import dk.codestack.seventv.core.render.EmoteTextureManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.notification.Notification;
import net.labymod.api.notification.Notification.Type;
import net.labymod.api.util.concurrent.task.Task;
import org.jetbrains.annotations.Nullable;

/**
 * Emotify - 7TV emotes for LabyMod 4.
 *
 * <p>Type {@code :EmoteName:} in chat and everyone with the addon sees the 7TV emote. Press the
 * picker key (default V, changeable only in the addon settings) to browse and insert emotes.
 */
@AddonMain
public class SevenTvAddon extends LabyAddon<SevenTvConfiguration> {

  /** Emote sets are re-fetched this often so newly added channel emotes show up on their own. */
  private static final long REFRESH_INTERVAL_MINUTES = 30L;

  private static SevenTvAddon instance;

  private ExecutorService executor;
  private SevenTvApi api;
  private ContentFilter filter;
  private EmoteTextureManager textureManager;
  private EmotePickerOpener pickerOpener;
  private EmoteTabCompleter tabCompleter;
  private Task refreshTask;

  private volatile EmoteRegistry registry = EmoteRegistry.empty();
  private final AtomicBoolean reloading = new AtomicBoolean(false);

  @Override
  protected void enable() {
    instance = this;
    this.registerSettingCategory();

    // WebP decoding is pure Java and CPU bound; a few threads make the picker fill up much faster.
    int threads = Math.max(3, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
    this.executor = Executors.newFixedThreadPool(threads, runnable -> {
      Thread thread = new Thread(runnable, "7TV-Emotes");
      thread.setDaemon(true);
      return thread;
    });

    Texts.configure(this.configuration().language().get());
    this.configuration().language().addChangeListener((p, o, n) -> Texts.configure(n));

    this.api = new SevenTvApi(this.executor);
    this.filter = new ContentFilter(this.configuration().filter());
    this.textureManager = new EmoteTextureManager(this.configuration(), this.api, this.executor);
    this.pickerOpener = new EmotePickerOpener(this);
    this.tabCompleter = new EmoteTabCompleter(this);

    this.registerListener(this.textureManager);
    this.registerListener(new ChatEmoteListener(this));
    this.registerListener(this.tabCompleter);
    this.registerListener(this.pickerOpener);
    this.registerCommand(new SevenTvCommand(this));

    // Reload when the source or filter settings change, without needing the button.
    this.configuration().useGlobalSet().addChangeListener((p, o, n) -> this.reload(false));
    this.configuration().popularEmotes().addChangeListener((p, o, n) -> this.reload(false));
    this.configuration().extraEmoteSets().addChangeListener((p, o, n) -> this.reload(false));
    this.configuration().quality().addChangeListener((p, o, n) -> this.textureManager.clear());
    this.configuration().animated().addChangeListener((p, o, n) -> this.textureManager.clear());

    this.reload(false);
    this.refreshTask = Task.builder(() -> this.reload(false))
        .repeat(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .build();
    this.refreshTask.execute();

    this.logger().info("Emotify enabled (" + this.filter.builtInWordCount()
        + " words in built-in blocklist)");
  }

  /**
   * Fetches every configured emote set and swaps in a fresh registry. Safe to call from any
   * thread and idempotent while a reload is already running.
   *
   * @param notify show a chat message with the result (used by the button and /7tv reload)
   */
  public void reload(boolean notify) {
    if (!this.reloading.compareAndSet(false, true)) {
      return;
    }

    List<CompletableFuture<EmoteSet>> futures = new ArrayList<>();
    if (this.configuration().useGlobalSet().get()) {
      futures.add(this.api.fetchGlobalSet());
    }

    int popular = this.configuration().popularEmotes().get().count();
    if (popular > 0) {
      futures.add(this.api.fetchPopular(popular));
    }

    for (EmoteSetReference reference : EmoteSetReference.parseAll(
        this.configuration().extraEmoteSets().get())) {
      futures.add(this.api.resolve(reference));
    }

    CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
    CompletableFuture.allOf(array).whenComplete((ignored, ignoredError) -> {
      // allOf fails if ANY set fails; collect the ones that worked so one bad id doesn't kill
      // the global set.
      List<EmoteSet> loaded = new ArrayList<>();
      int failed = 0;
      for (CompletableFuture<EmoteSet> future : futures) {
        try {
          loaded.add(future.join());
        } catch (Exception exception) {
          failed++;
          this.logger().warn("Failed to load a 7TV emote set: " + exception.getMessage());
        }
      }

      EmoteRegistry newRegistry = EmoteRegistry.build(loaded, this.filter);
      this.registry = newRegistry;
      this.tabCompleter.reset();
      this.reloading.set(false);

      this.logger().info("Loaded " + newRegistry.size() + " 7TV emotes from " + loaded.size()
          + " set(s), filtered " + newRegistry.blockedTotal() + ", failed sets: " + failed);

      if (notify) {
        this.pushNotification(newRegistry.size(), loaded.size(), failed);
      }
    });
  }

  private void pushNotification(int emotes, int sets, int failed) {
    Component text = Texts.translatable("seventv.notification.reloaded",
        Component.text(String.valueOf(emotes)), Component.text(String.valueOf(sets)));
    if (failed > 0) {
      text = Component.empty().append(text).append(Component.text(" "))
          .append(Texts.translatable("seventv.notification.failed",
              Component.text(String.valueOf(failed))).color(NamedTextColor.RED));
    }

    this.labyAPI().notificationController().push(Notification.builder()
        .title(Texts.translatable("seventv.settings.name"))
        .text(text)
        .type(Type.SYSTEM)
        .build());
  }

  @Override
  protected Class<SevenTvConfiguration> configurationClass() {
    return SevenTvConfiguration.class;
  }

  public static @Nullable SevenTvAddon instance() {
    return instance;
  }

  public EmoteRegistry registry() {
    return this.registry;
  }

  public EmoteTextureManager textureManager() {
    return this.textureManager;
  }

  public EmotePickerOpener pickerOpener() {
    return this.pickerOpener;
  }

  public ContentFilter filter() {
    return this.filter;
  }
}
