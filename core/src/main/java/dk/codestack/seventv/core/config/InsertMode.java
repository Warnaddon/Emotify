package dk.codestack.seventv.core.config;

/** What happens when an emote is clicked in the picker. */
public enum InsertMode {
  /** Sends {@code :name:} as a chat message immediately. */
  SEND,
  /** Opens the chat with {@code :name: } already typed. */
  OPEN_CHAT,
  /** Copies {@code :name:} to the clipboard and shows a notification. */
  CLIPBOARD
}
