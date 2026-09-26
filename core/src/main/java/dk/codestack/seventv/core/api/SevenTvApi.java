package dk.codestack.seventv.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.codestack.seventv.core.emote.Emote;
import dk.codestack.seventv.core.emote.EmoteSetReference;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Thin client for the parts of the 7TV REST API (v3) this addon needs.
 *
 * <p>Everything here runs on the addon's background executor and never touches the game thread.
 * Only public, unauthenticated endpoints are used. Requests contain nothing but the emote set or
 * user id the user configured.
 *
 * @see <a href="https://7tv.app/api/docs">7TV API documentation</a>
 */
public final class SevenTvApi {

  private static final Logging LOGGER = Logging.create(SevenTvApi.class);

  /** Primary and fallback hosts; both serve the v3 REST API. */
  private static final String[] API_HOSTS = {"https://7tv.io/v3", "https://api.7tv.app/v3"};
  private static final String USER_AGENT = "LabyMod4-7TV-Addon";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);
  private static final long MAX_IMAGE_BYTES = 4L * 1024L * 1024L;

  /** The v3 GraphQL endpoint rejects pages larger than this. */
  private static final int POPULAR_PAGE_SIZE = 100;
  private static final String POPULAR_SET_ID = "popular";
  private static final String POPULAR_SET_NAME = Emote.TRANSLATED_SET_PREFIX + "popular";
  private static final String GLOBAL_SET_NAME = Emote.TRANSLATED_SET_PREFIX + "global";

  private final HttpClient client;
  private final Executor executor;

  public SevenTvApi(Executor executor) {
    this.executor = executor;
    // Separate pool for the http client so response handling never waits on decoding.
    Executor httpExecutor = Executors.newCachedThreadPool(runnable -> {
      Thread thread = new Thread(runnable, "7TV-Http");
      thread.setDaemon(true);
      return thread;
    });
    this.client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(httpExecutor)
        .build();
  }

  /** Loads the global 7TV emote set. */
  public CompletableFuture<EmoteSet> fetchGlobalSet() {
    return this.fetchJson("/emote-sets/global").thenApply(json -> parseEmoteSet(json, GLOBAL_SET_NAME));
  }

  /** Loads any emote set by id. */
  public CompletableFuture<EmoteSet> fetchEmoteSet(String setId) {
    return this.fetchJson("/emote-sets/" + setId).thenApply(json -> parseEmoteSet(json, null));
  }

  /**
   * Loads the {@code count} most popular emotes on 7TV (site wide, sorted by usage) as a synthetic
   * "Popular" set. The GraphQL endpoint caps a page at {@value #POPULAR_PAGE_SIZE} emotes, so the
   * pages are fetched one after another and the result is cut to {@code count}.
   */
  public CompletableFuture<EmoteSet> fetchPopular(int count) {
    List<Emote> emotes = new ArrayList<>();
    if (count <= 0) {
      return CompletableFuture.completedFuture(new EmoteSet(POPULAR_SET_ID, POPULAR_SET_NAME, emotes));
    }

    int pages = (count + POPULAR_PAGE_SIZE - 1) / POPULAR_PAGE_SIZE;
    CompletableFuture<List<Emote>> chain = CompletableFuture.completedFuture(emotes);
    for (int page = 1; page <= pages; page++) {
      int pageNumber = page;
      chain = chain.thenCompose(collected -> this.fetchPopularPage(pageNumber).thenApply(items -> {
        for (Emote emote : items) {
          if (collected.size() < count) {
            collected.add(emote);
          }
        }

        return collected;
      }));
    }

    return chain.thenApply(collected -> new EmoteSet(POPULAR_SET_ID, POPULAR_SET_NAME, collected));
  }

  private CompletableFuture<List<Emote>> fetchPopularPage(int page) {
    String query = "query($page: Int!, $limit: Int!) { emotes(query: \"\", page: $page,"
        + " limit: $limit, sort: {value: \"popularity\", order: DESCENDING},"
        + " filter: {category: TOP, exact_match: false, case_sensitive: false,"
        + " ignore_tags: false, zero_width: false, animated: false, aspect_ratio: \"\"})"
        + " { items { id name flags listed animated owner { display_name username }"
        + " host { url files { name width height frame_count format } } } } }";

    JsonObject variables = new JsonObject();
    variables.addProperty("page", page);
    variables.addProperty("limit", POPULAR_PAGE_SIZE);
    JsonObject body = new JsonObject();
    body.addProperty("query", query);
    body.add("variables", variables);

    return this.postJson("/gql", body.toString(), 0).thenApply(json -> {
      JsonObject data = optObject(json, "data");
      JsonObject emotes = optObject(data, "emotes");
      JsonArray items = optArray(emotes, "items");
      if (items == null) {
        throw new IllegalStateException("7TV popular emotes page " + page + " has no items");
      }

      List<Emote> result = new ArrayList<>();
      for (JsonElement element : items) {
        if (!element.isJsonObject()) {
          continue;
        }

        // The GraphQL emote object has the same shape as the "data" object of a set entry.
        JsonObject active = new JsonObject();
        active.add("name", element.getAsJsonObject().get("name"));
        active.add("data", element);
        Emote emote = parseEmote(active, POPULAR_SET_ID, POPULAR_SET_NAME);
        if (emote != null) {
          result.add(emote);
        }
      }

      return result;
    });
  }

  /** Resolves a reference (set id / user / twitch user) to a fully loaded emote set. */
  public CompletableFuture<EmoteSet> resolve(EmoteSetReference reference) {
    switch (reference.type()) {
      case EMOTE_SET:
        return this.fetchEmoteSet(reference.value());
      case USER:
        return this.fetchJson("/users/" + reference.value()).thenCompose(user -> {
          String setId = findActiveSetId(user);
          if (setId == null) {
            return CompletableFuture.failedFuture(
                new IOException("7TV user " + reference.value() + " has no active emote set"));
          }

          return this.fetchEmoteSet(setId);
        });
      case TWITCH_USER:
        return this.fetchJson("/users/twitch/" + reference.value()).thenApply(json -> {
          JsonObject set = optObject(json, "emote_set");
          if (set == null) {
            throw new IllegalStateException(
                "Twitch user " + reference.value() + " has no 7TV emote set");
          }

          return parseEmoteSet(set, null);
        });
      default:
        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown reference"));
    }
  }

  /** Downloads the raw WEBP bytes of an emote. Rejects anything over {@link #MAX_IMAGE_BYTES}. */
  public CompletableFuture<byte[]> downloadImage(String url) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(TIMEOUT)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "image/webp")
        .GET()
        .build();

    return this.client.sendAsync(request, BodyHandlers.ofByteArray()).thenApply(response -> {
      if (response.statusCode() != 200) {
        throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
      }

      byte[] body = response.body();
      if (body.length > MAX_IMAGE_BYTES) {
        throw new IllegalStateException("Emote image too large: " + body.length + " bytes");
      }

      return body;
    });
  }

  private CompletableFuture<JsonObject> fetchJson(String path) {
    return this.fetchJson(path, 0);
  }

  private CompletableFuture<JsonObject> fetchJson(String path, int hostIndex) {
    return this.postJson(path, null, hostIndex);
  }

  /** GET when {@code body} is null, otherwise a JSON POST. Falls back to the next host on errors. */
  private CompletableFuture<JsonObject> postJson(String path, @Nullable String body, int hostIndex) {
    String url = API_HOSTS[hostIndex] + path;
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(TIMEOUT)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json");
    if (body == null) {
      builder.GET();
    } else {
      builder.header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    HttpRequest request = builder.build();

    CompletableFuture<JsonObject> future = new CompletableFuture<>();
    this.client.sendAsync(request, BodyHandlers.ofString()).whenCompleteAsync((response, error) -> {
      if (error == null && response.statusCode() == 200) {
        try {
          future.complete(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (Exception exception) {
          future.completeExceptionally(exception);
        }

        return;
      }

      // 4xx means the id is wrong - retrying on another host won't help.
      boolean clientError = error == null && response.statusCode() >= 400
          && response.statusCode() < 500;
      if (!clientError && hostIndex + 1 < API_HOSTS.length) {
        LOGGER.warn("7TV request failed on " + API_HOSTS[hostIndex] + ", trying fallback host");
        this.postJson(path, body, hostIndex + 1).whenComplete((json, fallbackError) -> {
          if (fallbackError != null) {
            future.completeExceptionally(fallbackError);
          } else {
            future.complete(json);
          }
        });
        return;
      }

      if (error != null) {
        future.completeExceptionally(error);
      } else {
        future.completeExceptionally(
            new IOException("HTTP " + response.statusCode() + " for " + url));
      }
    }, this.executor);

    return future;
  }

  private static @Nullable String findActiveSetId(JsonObject user) {
    JsonArray connections = optArray(user, "connections");
    if (connections != null) {
      for (JsonElement element : connections) {
        if (!element.isJsonObject()) {
          continue;
        }

        JsonObject set = optObject(element.getAsJsonObject(), "emote_set");
        String id = optString(set, "id");
        if (id != null && !id.isEmpty()) {
          return id;
        }
      }
    }

    JsonArray sets = optArray(user, "emote_sets");
    if (sets != null && !sets.isEmpty() && sets.get(0).isJsonObject()) {
      return optString(sets.get(0).getAsJsonObject(), "id");
    }

    return null;
  }

  /**
   * Converts a 7TV emote set JSON object into our lightweight model. Unknown / broken entries are
   * skipped instead of failing the whole set.
   */
  static EmoteSet parseEmoteSet(JsonObject json, @Nullable String nameOverride) {
    String setId = optString(json, "id");
    String setName = nameOverride != null ? nameOverride : optString(json, "name");
    if (setId == null) {
      setId = "unknown";
    }

    if (setName == null || setName.isEmpty()) {
      setName = setId;
    }

    List<Emote> emotes = new ArrayList<>();
    JsonArray array = optArray(json, "emotes");
    if (array == null) {
      return new EmoteSet(setId, setName, emotes);
    }

    for (JsonElement element : array) {
      if (!element.isJsonObject()) {
        continue;
      }

      Emote emote = parseEmote(element.getAsJsonObject(), setId, setName);
      if (emote != null) {
        emotes.add(emote);
      }
    }

    return new EmoteSet(setId, setName, emotes);
  }

  private static @Nullable Emote parseEmote(JsonObject active, String setId, String setName) {
    JsonObject data = optObject(active, "data");
    if (data == null) {
      return null;
    }

    String id = optString(data, "id");
    // The top-level name is the alias used in this set; fall back to the emote's own name.
    String name = optString(active, "name");
    if (name == null || name.isEmpty()) {
      name = optString(data, "name");
    }

    JsonObject host = optObject(data, "host");
    String hostUrl = optString(host, "url");
    if (id == null || name == null || name.isEmpty() || hostUrl == null) {
      return null;
    }

    JsonObject owner = optObject(data, "owner");
    String ownerName = optString(owner, "display_name");
    if (ownerName == null) {
      ownerName = optString(owner, "username");
    }

    if (ownerName == null) {
      ownerName = "";
    }

    int width = 0;
    int height = 0;
    int frameCount = 1;
    JsonArray files = optArray(host, "files");
    if (files != null) {
      for (JsonElement fileElement : files) {
        if (!fileElement.isJsonObject()) {
          continue;
        }

        JsonObject file = fileElement.getAsJsonObject();
        String fileName = optString(file, "name");
        if (fileName != null && fileName.equalsIgnoreCase("1x.webp")) {
          width = optInt(file, "width", 0);
          height = optInt(file, "height", 0);
          frameCount = Math.max(1, optInt(file, "frame_count", 1));
          break;
        }
      }
    }

    return new Emote(
        id,
        name,
        ownerName,
        setId,
        setName,
        optBoolean(data, "animated", false),
        optBoolean(data, "listed", true),
        optInt(data, "flags", 0),
        hostUrl,
        width,
        height,
        frameCount
    );
  }

  private static @Nullable JsonObject optObject(@Nullable JsonObject object, String key) {
    if (object == null) {
      return null;
    }

    JsonElement element = object.get(key);
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  private static @Nullable JsonArray optArray(@Nullable JsonObject object, String key) {
    if (object == null) {
      return null;
    }

    JsonElement element = object.get(key);
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
  }

  private static @Nullable String optString(@Nullable JsonObject object, String key) {
    if (object == null) {
      return null;
    }

    JsonElement element = object.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
  }

  private static int optInt(@Nullable JsonObject object, String key, int fallback) {
    if (object == null) {
      return fallback;
    }

    JsonElement element = object.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }

    try {
      return element.getAsInt();
    } catch (Exception exception) {
      return fallback;
    }
  }

  private static boolean optBoolean(@Nullable JsonObject object, String key, boolean fallback) {
    if (object == null) {
      return fallback;
    }

    JsonElement element = object.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }

    try {
      return element.getAsBoolean();
    } catch (Exception exception) {
      return fallback;
    }
  }

  /** A loaded emote set. */
  public record EmoteSet(String id, String name, List<Emote> emotes) {

  }
}
