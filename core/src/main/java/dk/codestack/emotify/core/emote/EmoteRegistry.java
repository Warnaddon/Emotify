package dk.codestack.emotify.core.emote;

import dk.codestack.emotify.core.api.SevenTvApi.EmoteSet;
import dk.codestack.emotify.core.filter.ContentFilter;
import dk.codestack.emotify.core.filter.ContentFilter.FilterReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class EmoteRegistry {
  private static final EmoteRegistry EMPTY = new EmoteRegistry(
      Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
      Collections.emptyList(), new EnumMap<>(FilterReason.class));

  private final List<Emote> emotes;
  private final Map<String, Emote> byExactName;
  private final Map<String, Emote> byLowerName;
  private final List<String> setNames;
  private final Map<FilterReason, Integer> blockedCounts;

  private EmoteRegistry(
      List<Emote> emotes,
      Map<String, Emote> byExactName,
      Map<String, Emote> byLowerName,
      List<String> setNames,
      Map<FilterReason, Integer> blockedCounts
  ) {
    this.emotes = emotes;
    this.byExactName = byExactName;
    this.byLowerName = byLowerName;
    this.setNames = setNames;
    this.blockedCounts = blockedCounts;
  }

  public static EmoteRegistry empty() {
    return EMPTY;
  }

  public static EmoteRegistry build(List<EmoteSet> sets, ContentFilter filter) {
    List<Emote> emotes = new ArrayList<>();
    Map<String, Emote> exact = new HashMap<>();
    Map<String, Emote> lower = new HashMap<>();
    List<String> setNames = new ArrayList<>();
    Map<FilterReason, Integer> blocked = new EnumMap<>(FilterReason.class);

    for (EmoteSet set : sets) {
      setNames.add(set.name());
      for (Emote emote : set.emotes()) {
        FilterReason reason = filter.check(emote);
        if (reason != null) {
          blocked.merge(reason, 1, Integer::sum);
          continue;
        }

        if (exact.containsKey(emote.name())) {
          continue;
        }

        exact.put(emote.name(), emote);
        lower.putIfAbsent(emote.lowerName(), emote);
        emotes.add(emote);
      }
    }

    emotes.sort((a, b) -> a.lowerName().compareTo(b.lowerName()));
    return new EmoteRegistry(
        Collections.unmodifiableList(emotes),
        exact,
        lower,
        Collections.unmodifiableList(setNames),
        blocked
    );
  }

  public @Nullable Emote find(String name, boolean caseInsensitive) {
    Emote emote = this.byExactName.get(name);
    if (emote != null || !caseInsensitive) {
      return emote;
    }

    return this.byLowerName.get(name.toLowerCase(Locale.ROOT));
  }

  public List<Emote> search(String query, int limit) {
    String lowerQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
    List<Emote> result = new ArrayList<>();
    if (lowerQuery.isEmpty()) {
      for (int i = 0; i < this.emotes.size() && result.size() < limit; i++) {
        result.add(this.emotes.get(i));
      }

      return result;
    }

    List<Emote> contains = new ArrayList<>();
    for (Emote emote : this.emotes) {
      if (result.size() >= limit) {
        break;
      }

      String lower = emote.lowerName();
      if (lower.startsWith(lowerQuery)) {
        result.add(emote);
      } else if (lower.contains(lowerQuery)) {
        contains.add(emote);
      }
    }

    for (int i = 0; i < contains.size() && result.size() < limit; i++) {
      result.add(contains.get(i));
    }

    return result;
  }

  public List<Emote> all() {
    return this.emotes;
  }

  public int size() {
    return this.emotes.size();
  }

  public List<String> setNames() {
    return this.setNames;
  }

  public Map<FilterReason, Integer> blockedCounts() {
    return this.blockedCounts;
  }

  public int blockedTotal() {
    int total = 0;
    for (Integer count : this.blockedCounts.values()) {
      total += count;
    }

    return total;
  }
}
