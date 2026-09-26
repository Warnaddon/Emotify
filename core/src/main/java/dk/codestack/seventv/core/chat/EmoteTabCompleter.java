package dk.codestack.seventv.core.chat;

import dk.codestack.seventv.core.SevenTvAddon;
import dk.codestack.seventv.core.emote.Emote;
import java.util.List;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

/**
 * TAB completion for emote tokens inside the chat input.
 *
 * <p>Type {@code :po} and press TAB -> {@code :PogChamp: }. Pressing TAB again on the completed
 * token cycles to the next match. Anything that does not start with {@code :} is left to the
 * normal (command) tab completion.
 */
public final class EmoteTabCompleter {

  private static final int MAX_MATCHES = 25;

  private final SevenTvAddon addon;

  /** State for cycling: the query the current cycle started with and where we are in the list. */
  private String cycleQuery = "";
  private int cycleIndex = -1;
  private String lastInserted = "";

  public EmoteTabCompleter(SevenTvAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    if (event.state() != State.PRESS || event.key() != Key.TAB) {
      return;
    }

    if (!this.addon.configuration().enabled().get()
        || !this.addon.configuration().tabCompletion().get()
        || !Laby.references().chatAccessor().isChatOpen()) {
      return;
    }

    String input = Laby.references().chatExecutor().getChatInputMessage();
    if (input == null || input.isEmpty() || input.startsWith("/")) {
      return;
    }

    int tokenStart = input.lastIndexOf(' ') + 1;
    String token = input.substring(tokenStart);

    String query;
    boolean cycling = false;
    if (!this.lastInserted.isEmpty() && input.endsWith(this.lastInserted)) {
      // TAB pressed again right after a completion: cycle to the next match of the same query.
      query = this.cycleQuery;
      tokenStart = input.length() - this.lastInserted.length();
      cycling = true;
    } else if (token.startsWith(":") && token.length() >= 2 && !token.endsWith(":")) {
      query = token.substring(1);
    } else {
      return;
    }

    List<Emote> matches = this.addon.registry().search(query, MAX_MATCHES);
    if (matches.isEmpty()) {
      return;
    }

    if (cycling) {
      this.cycleIndex = (this.cycleIndex + 1) % matches.size();
    } else {
      this.cycleQuery = query;
      this.cycleIndex = 0;
    }

    String insert = matches.get(this.cycleIndex).token() + " ";
    String completed = input.substring(0, tokenStart) + insert;
    this.lastInserted = insert;

    // Re-opens the chat with the new text; when the chat is already open this just sets the input.
    Laby.references().chatExecutor().suggestCommand(completed);
  }

  public void reset() {
    this.cycleQuery = "";
    this.cycleIndex = -1;
    this.lastInserted = "";
  }
}
