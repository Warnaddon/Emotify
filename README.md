# Emotify – 7TV emotes for LabyMod 4

Type `:PogChamp:` in any Minecraft chat and every player with the addon sees the real 7TV emote.
Press **V** (changeable only under *ESC → LabyMod Settings → Emotify*) to open the emote picker.

Namespace `seventv` · package `dk.codestack.seventv` · Minecraft 1.8.9 → 26.3 (every version LabyMod 4 ships).

## How it works

| Piece | What it does |
|---|---|
| `SevenTvApi` | Async HTTP client for the public 7TV v3 REST API (`/emote-sets/global`, `/emote-sets/{id}`, `/users/{id}`, `/users/twitch/{id}`). Falls back from `7tv.io` to `api.7tv.app`. Never sends anything about the player. |
| `EmoteSetReference` | Parses what the user typed into *Extra emote sets*: raw set IDs, `7tv.app/emote-sets/...`, `7tv.app/users/...`, `twitch:<twitch user id>`. |
| `ContentFilter` | Runs **once when a set is loaded**, so a blocked emote never exists in the addon at all (can't be typed, tab-completed, picked or rendered). See *Safety* below. |
| `EmoteRegistry` | Immutable name → emote index, swapped atomically on reload. Case-insensitive lookup optional. |
| `WebPDecoder` | 7TV only serves WEBP/AVIF. Pixels are decoded by TwelveMonkeys (pure Java, no natives); the RIFF/ANMF container is parsed by us to get frame offsets, durations and blend/dispose flags, then frames are composited onto a canvas exactly like a browser does. Animations are thinned to max 64 frames (durations preserved). |
| `EmoteTexture` / `EmoteTextureManager` | One `DynamicTexture` per emote. Chat gets an `Icon.completable(...)` that shows a placeholder until the texture is uploaded. Animated emotes re-upload the current frame on the game tick (max 24 uploads/tick), LRU eviction above the configured cache size, all GL work on the render thread. |
| `ChatEmoteListener` | `ChatReceiveEvent` (priority 125): fast `indexOf(':')` bail-out, then rebuilds the component tree replacing `:name:` tokens with `Component.icon(...)` + hover (name, set, author). Max N emotes per message. |
| `EmoteTabCompleter` | `:po` + TAB → `:PogChamp: `, TAB again cycles. Only for tokens starting with `:`; commands are untouched. |
| `EmotePickerActivity` / `EmotePickerOpener` | The V key: search box, "recently used" row, grid of all emotes. Click (or Enter for the first match) closes the picker and opens the chat with `:Name: ` pre-typed, or copies to clipboard (setting). Picker key is ignored while chat/any screen is open, so typing "v" never triggers it. |
| `SevenTvCommand` | `/7tv` (info + filter stats), `/7tv reload`, `/7tv search <name>` (clickable results), `/7tv picker`. |

**Important:** what you *send* is never modified. Your message goes to the server as the plain text
`:PogChamp:`; only clients with the addon render it. Server-side chat filters keep working.

## Safety / NSFW filter (layered)

1. **Allowlist model** – only emotes from the sets *you* configured exist. Nobody can make an
   arbitrary 7TV emote appear on your screen by typing its name or id.
2. **7TV moderation flags** – `sexual` (bit 16) is **always blocked, no switch**. `edgy` (bit 18),
   `epilepsy` (bit 17), `twitch-disallowed` (bit 24), `private` and **unlisted** (never moderated by
   7TV) are blocked by default.
3. **Word lists** – bundled `assets/seventv/blocklist.txt` + the user's own words, matched
   CamelCase-aware (`cum` hits `peepoCum`, not `Cucumber`). Exact-name allowlist for false positives
   (never bypasses layer 2).
4. **Spam cap** – max emotes per message (default 10), max image size 4 MB, texture cache limit.

Filtered counts per reason are shown by `/7tv`.

## Build

```bash
./gradlew build            # dev build
./gradlew createReleaseJar # build/libs/seventv-<version>-release.jar
./gradlew runClient1.21.11 # dev client (see `./gradlew tasks` for all versions)
```

Requires JDK 21+ (the CI workflow uses 25, same as the official template). The GitHub workflow is
byte-identical to `LabyMod/addon-template` and the Gradle wrapper is SHA-pinned, both required by
the LabyMod publishing guidelines.

## Things to double-check in your IDE before the first build

I verified almost every API call against the official addons (spotify, minimap, waypoints,
betterperspective, customcrosshair) and the LabyMod docs, but I could not compile against the
LabyMod API jar here. These few calls are "by analogy" – if one of them doesn't resolve, the
replacement is a one-liner:

| Call | Where | If it doesn't exist |
|---|---|---|
| `TranslatableComponent` (`getKey()`, `getArguments()`) and `Component.translatable(key, Component...)` | `ChatEmoteListener.transform` | Delete the `instanceof TranslatableComponent` branch – translatable messages then simply aren't scanned. |
| `Component#hoverEvent(HoverEvent.showText(...))` | `ChatEmoteListener.emoteComponent` | Use `Style.builder().hoverEvent(...)` + `.style(...)`, or drop the hover. |
| `Component#clickEvent(ClickEvent.suggestCommand(...))` | `SevenTvCommand.search` | Same as above with `ClickEvent.Action.SUGGEST_COMMAND`. |
| `SamplerDescription.Filter.LINEAR` | `EmoteTexture` | Use `Filter.NEAREST` (used by the minimap addon). |
| `TextFieldWidget#isFocused()` | `EmotePickerActivity.keyPressed` | Track focus yourself or just let ESC close the picker. |
| `Notification.Builder#icon(Icon)` | `EmotePickerActivity.pick` | Remove the `.icon(...)` line. |

Everything else (`DynamicTexture`, `CompletableResourceLocation`, `Icon.completable`, `Component.icon`,
`ChatReceiveEvent#setMessage`, `KeyEvent`, `chatExecutor().suggestCommand`, `GridWidget`,
`TextFieldWidget#updateListener/submitHandler`, `ComponentWidget.i18n`, `Task.builder`, settings
annotations, `ConfigProperty.createEnum`, `addChangeListener`) is used exactly as the official addons use it.

## Adding a channel's emotes

Settings → *Extra emote sets*, e.g.

```
https://7tv.app/emote-sets/01FRG0ZGSR00084PQ73P1BYDX8, twitch:71092938
```

Sets reload automatically when the field changes and every 30 minutes; `/7tv reload` forces it.
The global set is always loaded first so custom sets can't shadow global names.

## Guidelines compliance

No `java.util.stream`, no reflection, no `System.out`, no legacy `§` codes, no LabyMod core packages,
every user-visible string goes through i18n (`en_us`, `da_dk`, `de_de`), objects are cached rather
than created per frame, the addon can be fully disabled by the user or by servers via `enabled()`.

## Credits

All emotes and emote artwork are served by [7TV](https://7tv.app) and belong to 7TV and their respective creators. Emotify only displays them and is not affiliated with or endorsed by 7TV.
