package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import juloo.keyboard2.suggestions.Suggestions;

public final class KeyEventHandler
  implements Config.IKeyEventHandler,
             ClipboardHistoryService.ClipboardPasteCallback,
             CurrentlyTypedWord.Callback
{
  IReceiver _recv;
  Autocapitalisation _autocap;
  Suggestions _suggestions;
  CurrentlyTypedWord _typedword;
  final VimEngine _vim;
  final LuaEngine _lua;
  /** State of the system modifiers. It is updated whether a modifier is down
      or up and a corresponding key event is sent. */
  Pointers.Modifiers _mods;
  /** Consistent with [_mods]. This is a mutable state rather than computed
      from [_mods] to ensure that the meta state is correct while up and down
      events are sent for the modifier keys. */
  int _meta_state = 0;
  /** Whether to force sending arrow keys to move the cursor when
      [setSelection] could be used instead. */
  boolean _move_cursor_force_fallback = false;
  /** Whether the space bar automatically enters the best suggestion. */
  boolean _space_bar_auto_complete = false;
  /** Remember the action that was handled. This is used by autocorrect. */
  LastAction _last_action = null;
  LastAction _next_last_action = null;

  /** Quick-tap feature. Maps the character of a key to the special
      character typed when the key is held down. Populated from the layout by
      [Keyboard2View]. */
  Map<Character, Character> _quick_symbols = new TreeMap<Character, Character>();

  public KeyEventHandler(IReceiver recv, Suggestions sg)
  {
    _recv = recv;
    Handler handler = recv.getHandler();
    _autocap = new Autocapitalisation(handler,
        this.new Autocapitalisation_callback());
    _mods = Pointers.Modifiers.EMPTY;
    _suggestions = sg;
    _typedword = new CurrentlyTypedWord(handler, this);
    _vim = new VimEngine(this);
    Context ctx = recv.getApplicationContext();
    _lua = (ctx == null) ? null : new LuaEngine(this, ctx);
  }

  Handler get_handler()
  {
    return _recv.getHandler();
  }

  /** Editing just started. */
  public void started(Config conf)
  {
    InputConnection ic = _recv.getCurrentInputConnection();
    _autocap.started(conf, ic);
    _typedword.started(conf, ic);
    if (_suggestions != null)
      _suggestions.started();
    _move_cursor_force_fallback =
      conf.editor_config.should_move_cursor_force_fallback;
    _space_bar_auto_complete = conf.space_bar_auto_complete;
    _last_action = null;
    _vim.reset();
  }

  /** Selection has been updated. */
  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    _autocap.selection_updated(oldSelStart, newSelStart);
    _typedword.selection_updated(oldSelStart, newSelStart, newSelEnd);
  }

  /** A key is being pressed. There will not necessarily be a corresponding
      [key_up] event. */
  @Override
  public void key_down(KeyValue key, boolean isSwipe)
  {
    if (key == null)
      return;
    // Stop auto capitalisation when pressing some keys
    switch (key.getKind())
    {
      case Modifier:
        switch (key.getModifier())
        {
          case CTRL:
          case ALT:
          case META:
            _autocap.stop();
            break;
        }
        break;
      case Compose_pending:
        _autocap.stop();
        break;
      case Slider:
        // Don't wait for the next key_up and move the cursor right away. This
        // is called after the trigger distance have been travelled.
        handle_slider(key.getSlider(), key.getSliderRepeat(), true);
        break;
      default: break;
    }
  }

  /** A key has been released. */
  @Override
  public void key_up(KeyValue key, Pointers.Modifiers mods)
  {
    if (key == null)
      return;
    _next_last_action = LastAction.OTHER;
    if (_recv.is_float_open())
    {
      handle_overlay_key(key);
      _last_action = _next_last_action;
      return;
    }
    Pointers.Modifiers old_mods = _mods;
    update_meta_state(mods);
    if (_vim.on_key(key, _meta_state))
    {
      // The key was handled by the VIM engine.
    }
    else
    {
      switch (key.getKind())
      {
        case Char: send_text(String.valueOf(key.getChar())); break;
        case String: send_text(key.getString()); break;
        case Event: _recv.handle_event_key(key.getEvent()); break;
        case Keyevent: send_key_down_up(key.getKeyevent()); break;
        case Modifier: break;
        case Editing: handle_editing_key(key.getEditing()); break;
        case Compose_pending: _recv.set_compose_pending(true); break;
        case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
        case Macro: evaluate_macro(key.getMacro()); break;
        case Stateful: handle_stateful(key.getStateful()); break;
      }
    }
    update_meta_state(old_mods);
    _last_action = _next_last_action;
  }

  /** Called when the keyboard changes. Provides the mapping for the quick
      long-press feature: key character -> special character. */
  public void quick_tap_symbols(Map<Character, Character> symbols)
  {
    _quick_symbols = (symbols == null) ? new TreeMap<Character, Character>() : symbols;
  }

  /** The special character typed by holding a quick-tap key. [0] means the
      hold should fall back to the normal long-press behaviour (no symbol).
      Available in every VIM mode: in NORMAL mode the symbols that are VIM
      commands ([:], [/], [?]&hellip;) are handed to the engine as usual, which
      makes entering command/search/help modes easier by holding a key. */
  public char getQuickTapSymbol(char c)
  {
    if (_quick_symbols.isEmpty())
      return 0;
    Character symbol = _quick_symbols.get(c);
    return (symbol == null) ? 0 : symbol.charValue();
  }

  /** When the floating browser is open, keys go straight to it (no VIM
      engine, no quick double-tap). [esc] closes the window. */
  private void handle_overlay_key(KeyValue key)
  {
    if (key.getKind() == KeyValue.Kind.Keyevent
        && key.getKeyevent() == KeyEvent.KEYCODE_ESCAPE)
    {
      _recv.close_float_panel();
      return;
    }
    switch (key.getKind())
    {
      case Char: send_text(String.valueOf(key.getChar())); break;
      case String: send_text(key.getString()); break;
      case Event: _recv.handle_event_key(key.getEvent()); break;
      case Keyevent: send_key_down_up(key.getKeyevent()); break;
      case Modifier: break;
      case Editing: handle_editing_key(key.getEditing()); break;
      case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
      case Macro: evaluate_macro(key.getMacro()); break;
      case Stateful: handle_stateful(key.getStateful()); break;
      case Compose_pending: _recv.set_compose_pending(true); break;
    }
  }

  @Override
  public void mods_changed(Pointers.Modifiers mods)
  {
    update_meta_state(mods);
  }

  @Override
  public void suggestion_entered(String text)
  {
    String old = _typedword.get();
    int cur_rel = _typedword.cursor_relative();
    replace_surrounding_text(old.length() + cur_rel, -cur_rel, text);
    last_replaced_word = old;
    last_replacement_word_len = text.length();
    _next_last_action = LastAction.SUGGESTION_ENTERED;
  }

  @Override
  public void paste_from_clipboard_pane(String content)
  {
    send_text(content);
  }

  @Override
  public void currently_typed_word(String word)
  {
    if (_suggestions != null)
      _suggestions.currently_typed_word(word);
  }

  public void dictionary_changed()
  {
    // Refresh the suggestions immediately after dictionary changed.
    if (_suggestions != null)
      _suggestions.currently_typed_word(_typedword.get());
  }

  /** Update [_mods] to be consistent with the [mods], sending key events if
      needed. */
  void update_meta_state(Pointers.Modifiers mods)
  {
    // Released modifiers
    Iterator<KeyValue> it = _mods.diff(mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), false);
    // Activated modifiers
    it = mods.diff(_mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), true);
    _mods = mods;
  }

  // private void handleDelKey(int before, int after)
  // {
  //  CharSequence selection = getCurrentInputConnection().getSelectedText(0);

  //  if (selection != null && selection.length() > 0)
  //  getCurrentInputConnection().commitText("", 1);
  //  else
  //  getCurrentInputConnection().deleteSurroundingText(before, after);
  // }

  void sendMetaKey(int eventCode, int meta_flags, boolean down)
  {
    if (down)
    {
      _meta_state = _meta_state | meta_flags;
      send_keyevent(KeyEvent.ACTION_DOWN, eventCode, _meta_state);
    }
    else
    {
      send_keyevent(KeyEvent.ACTION_UP, eventCode, _meta_state);
      _meta_state = _meta_state & ~meta_flags;
    }
  }

  void sendMetaKeyForModifier(KeyValue kv, boolean down)
  {
    switch (kv.getKind())
    {
      case Modifier:
        switch (kv.getModifier())
        {
          case CTRL:
            sendMetaKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON, down);
            break;
          case ALT:
            sendMetaKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_ON, down);
            break;
          case SHIFT:
            sendMetaKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON, down);
            break;
          case META:
            sendMetaKey(KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_ON, down);
            break;
          default:
            break;
        }
        break;
    }
  }

  void send_key_down_up(int keyCode)
  {
    send_key_down_up(keyCode, _meta_state);
  }

  /** Ignores currently pressed system modifiers. */
  void send_key_down_up(int keyCode, int metaState)
  {
    send_keyevent(KeyEvent.ACTION_DOWN, keyCode, metaState);
    send_keyevent(KeyEvent.ACTION_UP, keyCode, metaState);
  }

  void send_keyevent(int eventAction, int eventCode, int metaState)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.sendKeyEvent(new KeyEvent(1, 1, eventAction, eventCode, 0,
          metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    if (eventAction == KeyEvent.ACTION_UP)
    {
      _autocap.event_sent(eventCode, metaState);
      _typedword.event_sent(eventCode, metaState);
    }
  }

  public void send_text(String text)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    _autocap.typed(text);
    _typedword.typed(text);
    conn.commitText(text, 1);
  }

  void replace_surrounding_text(int remove_before, int remove_after,
      String new_text)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.beginBatchEdit();
    conn.deleteSurroundingText(remove_before, remove_after);
    conn.commitText(new_text, 1);
    _typedword.remove_surrounding_text(remove_before, remove_after);
    _typedword.typed(new_text);
    conn.endBatchEdit();
  }

  /** See {!InputConnection.performContextMenuAction}. */
  void send_context_menu_action(int id)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.performContextMenuAction(id);
  }

  @SuppressLint("InlinedApi")
  void handle_editing_key(KeyValue.Editing ev)
  {
    switch (ev)
    {
      case COPY: if(_typedword.is_selection_not_empty()) send_context_menu_action(android.R.id.copy); break;
      case PASTE: send_context_menu_action(android.R.id.paste); break;
      case CUT: if(_typedword.is_selection_not_empty()) send_context_menu_action(android.R.id.cut); break;
      case SELECT_ALL: send_context_menu_action(android.R.id.selectAll); break;
      case SHARE: send_context_menu_action(android.R.id.shareText); break;
      case PASTE_PLAIN: send_context_menu_action(android.R.id.pasteAsPlainText); break;
      case UNDO: send_context_menu_action(android.R.id.undo); break;
      case REDO: send_context_menu_action(android.R.id.redo); break;
      case REPLACE: send_context_menu_action(android.R.id.replaceText); break;
      case ASSIST: send_context_menu_action(android.R.id.textAssist); break;
      case AUTOFILL: send_context_menu_action(android.R.id.autofill); break;
      case DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case FORWARD_DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case SELECTION_CANCEL: cancel_selection(); break;
      case SPACE_BAR: handle_space_bar(); break;
      case BACKSPACE: handle_backspace(); break;
    }
  }

  static ExtractedTextRequest _move_cursor_req = null;

  static ExtractedTextRequest _full_text_req = null;

  /** Query the full text of the editor. Returns an [ExtractedText] with a
      non-null [text] field, or [null] if the editor doesn't support it. */
  ExtractedText get_full_text(InputConnection conn)
  {
    if (_full_text_req == null)
    {
      _full_text_req = new ExtractedTextRequest();
      _full_text_req.hintMaxChars = 200000;
      _full_text_req.hintMaxLines = 0;
    }
    return conn.getExtractedText(_full_text_req, 0);
  }

  /** Query the cursor position. The extracted text is empty. Returns [null] if
      the editor doesn't support this operation. */
  ExtractedText get_cursor_pos(InputConnection conn)
  {
    if (_move_cursor_req == null)
    {
      _move_cursor_req = new ExtractedTextRequest();
      _move_cursor_req.hintMaxChars = 0;
    }
    return conn.getExtractedText(_move_cursor_req, 0);
  }

  /** [r] might be negative, in which case the direction is reversed. */
  void handle_slider(KeyValue.Slider s, int r, boolean key_down)
  {
    switch (s)
    {
      case Cursor_left: move_cursor(-r); break;
      case Cursor_right: move_cursor(r); break;
      case Cursor_up: move_cursor_vertical(-r); break;
      case Cursor_down: move_cursor_vertical(r); break;
      case Selection_cursor_left: move_cursor_sel(r, true, key_down); break;
      case Selection_cursor_right: move_cursor_sel(r, false, key_down); break;
    }
  }

  void handle_stateful(KeyValue.Stateful st)
  {
    switch (st)
    {
      case Complete_first:
      case Complete_second:
      case Complete_third:
      case Complete_emoji:
        suggestion_entered(st.toString());
        break;
    }
  }

  /** Move the cursor right or left, if possible without sending key events.
      Unlike arrow keys, the selection is not removed even if shift is not on.
      Falls back to sending arrow keys events if the editor do not support
      moving the cursor or a modifier other than shift is pressed. */
  void move_cursor(int d)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Continue expanding the selection even if shift is not pressed
      if (sel_end != sel_start)
      {
        sel_end += d;
        if (sel_end == sel_start) // Avoid making the selection empty
          sel_end += d;
      }
      else
      {
        sel_end += d;
        // Leave 'sel_start' where it is if shift is pressed
        if ((_meta_state & KeyEvent.META_SHIFT_ON) == 0)
          sel_start = sel_end;
      }
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Move one of the two side of a selection. If [sel_left] is true, the left
      position is moved, otherwise the right position is moved. */
  void move_cursor_sel(int d, boolean sel_left, boolean key_down)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Reorder the selection when the slider has just been pressed. The
      // selection might have been reversed if one end crossed the other end
      // with a previous slider.
      if (key_down && sel_start > sel_end)
      {
        sel_start = et.selectionEnd;
        sel_end = et.selectionStart;
      }
      do
      {
        if (sel_left)
          sel_start += d;
        else
          sel_end += d;
        // Move the cursor twice if moving it once would make the selection
        // empty and stop selection mode.
      } while (sel_start == sel_end);
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Returns whether the selection can be set using [conn.setSelection()].
      This can happen on Termux or when system modifiers are activated for
      example. */
  boolean can_set_selection(InputConnection conn)
  {
    final int system_mods =
      KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;
    return !_move_cursor_force_fallback && (_meta_state & system_mods) == 0;
  }

  void move_cursor_fallback(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, d);
  }

  /** Move the cursor up and down. This sends UP and DOWN key events that might
      make the focus exit the text box. */
  void move_cursor_vertical(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_UP, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_DOWN, d);
  }

  void evaluate_macro(KeyValue[] keys)
  {
    if (keys.length == 0)
      return;
    // Ignore modifiers that are activated at the time the macro is evaluated
    mods_changed(Pointers.Modifiers.EMPTY);
    evaluate_macro_loop(keys, 0, Pointers.Modifiers.EMPTY, _autocap.pause());
  }

  /** Evaluate the macro asynchronously to make sure event are processed in the
      right order. */
  void evaluate_macro_loop(final KeyValue[] keys, int i, Pointers.Modifiers mods, final boolean autocap_paused)
  {
    boolean should_delay = false;
    KeyValue kv = KeyModifier.modify_no_modmap(keys[i], mods);
    if (kv != null)
    {
      if (kv.hasFlagsAny(KeyValue.FLAG_LATCH))
      {
        // Non-special latchable keys clear latched modifiers
        if (!kv.hasFlagsAny(KeyValue.FLAG_SPECIAL))
          mods = Pointers.Modifiers.EMPTY;
        mods = mods.with_extra_mod(kv);
      }
      else
      {
        key_down(kv, false);
        key_up(kv, mods);
        mods = Pointers.Modifiers.EMPTY;
      }
      should_delay = wait_after_macro_key(kv);
    }
    i++;
    if (i >= keys.length) // Stop looping
    {
      _autocap.unpause(autocap_paused);
    }
    else if (should_delay)
    {
      // Add a delay before sending the next key to avoid race conditions
      // causing keys to be handled in the wrong order. Notably, KeyEvent keys
      // handling is scheduled differently than the other edit functions.
      final int i_ = i;
      final Pointers.Modifiers mods_ = mods;
      _recv.getHandler().postDelayed(new Runnable() {
        public void run()
        {
          evaluate_macro_loop(keys, i_, mods_, autocap_paused);
        }
      }, 1000/30);
    }
    else
      evaluate_macro_loop(keys, i, mods, autocap_paused);
  }

  boolean wait_after_macro_key(KeyValue kv)
  {
    switch (kv.getKind())
    {
      case Keyevent:
      case Editing:
      case Event:
        return true;
      case Slider:
        return _move_cursor_force_fallback;
      default:
        return false;
    }
  }

  /** Repeat calls to [send_key_down_up]. */
  void send_key_down_up_repeat(int event_code, int repeat)
  {
    while (repeat-- > 0)
      send_key_down_up(event_code);
  }

  void cancel_selection()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et == null) return;
    final int curs = et.selectionStart;
    // Notify the receiver as Android's [onUpdateSelection] is not triggered.
    if (conn.setSelection(curs, curs))
      _recv.selection_state_changed(false);
  }

  /** The word that was replaced by a suggestion when the last action was to
      enter a suggestion (with the space bar or the candidates view) or [null]
      otherwise. */
  String last_replaced_word = null;
  /** Length of the text before the cursor that should be replaced by
      backspace. */
  int last_replacement_word_len = 0;

  /** Implement autocorrect when enabled in the settings. */
  void handle_space_bar()
  {
    if (_space_bar_auto_complete && _suggestions != null
        && _suggestions.count > 0
        && !_typedword.is_selection_not_empty()
        && _typedword.cursor_relative() == 0)
      suggestion_entered(_suggestions.suggestions[0] + " ");
    else
      send_text(" ");
  }

  /** Undo the last autocorrect. */
  void handle_backspace()
  {
    if (_last_action == LastAction.SUGGESTION_ENTERED
        && last_replaced_word != null)
    {
      replace_surrounding_text(last_replacement_word_len, 0, last_replaced_word);
      last_replaced_word = null;
    }
    else
    {
      send_key_down_up(KeyEvent.KEYCODE_DEL);
    }
  }

  // ---- Vim command mode ('o' is for commands) ----------------------------

  /** Execute a command typed in the ':' command line. New commands are added
      here, or through Lua scripts (see [LuaEngine]). The command line
      currently returns to normal mode before executing, so commands are
      single-shot. */
  void execute_vim_command(String cmd)
  {
    String[] parts = cmd.split("\\s+", 2);
    String name = parts[0].toLowerCase(Locale.ROOT);
    String arg = (parts.length > 1) ? parts[1].trim() : "";
    switch (name)
    {
      case "help": case "h":
        open_help_page(arg);
        return;
      case "copy": case "y": case "yank":
        vim_copy_selection_or_line();
        return;
      case "paste": case "p":
        vim_paste_clipboard();
        return;
      case "undo": case "u":
        _recv.getHandler().post(new Runnable() { public void run() { send_context_menu_action(android.R.id.undo); } });
        return;
      case "redo":
        _recv.getHandler().post(new Runnable() { public void run() { send_context_menu_action(android.R.id.redo); } });
        return;
      case "goto": case "line":
        vim_goto_line(arg);
        return;
      case "upper": case "lower": case "title":
        vim_transform_selection_or_line(name);
        return;
      case "reload":
        if (_lua == null)
          break;
        _lua.reload();
        _vim.flash_status(_lua.count_commands() + " lua commands (" + _lua.current_dir() + ")", VimEngine.STATUS_COLOR_CMD);
        return;
      case "ls":
        if ("help".equals(arg))
        {
          _recv.open_page("ajuda", vim_help_index_html());
          return;
        }
        _vim.flash_status((_lua == null) ? "" : join_names(_lua.command_names()), VimEngine.STATUS_COLOR_CMD);
        return;
      case "addlua":
        vim_add_lua_script(arg);
        return;
      case "rmlua":
        if (_lua != null)
          _lua.delete_script(arg);
        return;
      case "float": case "browser": case "br":
        vim_float(arg);
        return;
      default:
        if (_lua != null && _lua.execute(name, arg))
          return;
        _vim.flash_status("unknown command: " + name, VimEngine.STATUS_COLOR_CMD);
        return;
    }
  }

  /** Copy the content of the system clipboard to a Lua script file. */
  void vim_add_lua_script(String name)
  {
    if (name.isEmpty())
    {
      _vim.flash_status("usage: addlua <name>", VimEngine.STATUS_COLOR_CMD);
      return;
    }
    String content = get_clipboard_text();
    if (content == null)
    {
      _vim.flash_status("clipboard empty", VimEngine.STATUS_COLOR_CMD);
      return;
    }
    if (_lua != null)
      _lua.save_script(name, content);
  }

  String get_clipboard_text()
  {
    Context ctx = _recv.getApplicationContext();
    if (ctx == null)
      return null;
    ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip())
      return null;
    ClipData clip = cm.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0)
      return null;
    CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
    return (cs == null) ? null : cs.toString();
  }

  static String join_names(String[] names)
  {
    if (names.length == 0)
      return "no lua commands";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < names.length; i++)
    {
      if (i > 0)
        b.append(' ');
      b.append(names[i]);
    }
    return b.toString();
  }

  /** Copy the current selection, or the whole line, to the system clipboard. */
  void vim_copy_selection_or_line()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_full_text(conn);
    if (et == null || et.text == null)
      return;
    String text = et.text.toString();
    int base = et.startOffset;
    int s = et.selectionStart - base;
    int e = et.selectionEnd - base;
    if (s == e)
    {
      int ls = text.lastIndexOf('\n', s - 1) + 1;
      if (ls < 0) ls = 0;
      int le = text.indexOf('\n', s);
      if (le < 0) le = text.length();
      s = ls;
      e = le;
    }
    if (e <= s || s < 0 || e > text.length())
      return;
    set_clipboard_text(text.substring(s, e));
    _vim.flash_status((e - s) + " copied", VimEngine.STATUS_COLOR_CMD);
  }

  /** Paste the content of the system clipboard at the cursor. */
  void vim_paste_clipboard()
  {
    Context ctx = _recv.getApplicationContext();
    if (ctx == null)
      return;
    ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip())
    {
      _vim.flash_status("clipboard empty", VimEngine.STATUS_COLOR_CMD);
      return;
    }
    ClipData clip = cm.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0)
      return;
    CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
    if (cs == null)
      return;
    send_text(cs.toString());
  }

  /** Open or close the embedded floating web browser. */
  void vim_float(String arg)
  {
    boolean was_open = _recv.is_float_open();
    _recv.toggle_float_panel(arg);
    String status = was_open ? "browser fechado" :
      ("browser: " + (arg.isEmpty() ? "google" : arg));
    _vim.flash_status(status, VimEngine.STATUS_COLOR_CMD);
  }

  /** Open the built-in help page (triggered with '?' in normal mode). */
  void open_vim_help()
  {
    _recv.open_help();
    _vim.flash_status("ajuda", VimEngine.STATUS_COLOR_CMD);
  }

  /** Build the basic help page (opened with '?' and [help]): the VIM
      shortcuts and the main ':' commands. Detailed pages are reachable with
      [:help <name>] and listed with [:ls help]. */
  static String vim_help_html()
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>vi_key &mdash; ajuda</h1>");
    b.append("<p class=\"dim\">Digite qualquer coisa na barra acima para navegar. " +
        "<kbd>esc</kbd> fecha esta janela.</p><hr>");

    b.append("<h2>Modo NORMAL &mdash; movimento</h2>");
    b.append("<p><kbd>h</kbd> <kbd>j</kbd> <kbd>k</kbd> <kbd>l</kbd> &rarr; mover esquerda / baixo / cima / direita</p>");
    b.append("<p><kbd>w</kbd> <kbd>b</kbd> <kbd>e</kbd> &rarr; palavra: pr&oacute;xima / anterior / fim</p>");
    b.append("<p><kbd>0</kbd> <kbd>$</kbd> &rarr; in&iacute;cio / fim da linha</p>");
    b.append("<p><kbd>gg</kbd> &rarr; in&iacute;cio do documento &middot; <kbd>G</kbd> &rarr; fim &middot; <kbd>N</kbd><kbd>G</kbd> &rarr; linha N</p>");

    b.append("<h2>Modo NORMAL &mdash; edi&ccedil;&atilde;o</h2>");
    b.append("<p><kbd>i</kbd> &rarr; voltar ao modo INSERT</p>");
    b.append("<p><kbd>o</kbd> / <kbd>O</kbd> &rarr; nova linha abaixo / acima e entrar em INSERT</p>");
    b.append("<p><kbd>x</kbd> &rarr; apagar caractere</p>");
    b.append("<p><kbd>d</kbd><kbd>d</kbd> linha &middot; <kbd>d</kbd><kbd>w</kbd> palavra &middot; <kbd>d</kbd><kbd>e</kbd> fim palavra &middot; <kbd>d</kbd><kbd>0</kbd> / <kbd>d</kbd><kbd>$</kbd> at&eacute; in&iacute;cio/fim da linha</p>");
    b.append("<p><kbd>u</kbd> desfazer &middot; <kbd>Ctrl</kbd>+<kbd>r</kbd> refazer</p>");

    b.append("<h2>Busca</h2>");
    b.append("<p><kbd>/</kbd> &rarr; busca incremental &middot; <kbd>n</kbd> / <kbd>N</kbd> &rarr; pr&oacute;ximo / anterior resultado</p>");

    b.append("<h2>Mudan&ccedil;a de modo</h2>");
    b.append("<p><kbd>ctrl</kbd>+<kbd>esc</kbd> ou <kbd>j</kbd><kbd>k</kbd> (no INSERT) &rarr; modo NORMAL</p>");
    b.append("<p>Contagens funcionam: <kbd>3j</kbd>, <kbd>2dd</kbd>, <kbd>5w</kbd>&hellip;</p>");
    b.append("<p class=\"dim\">Segurar uma tecla digita o s&iacute;mbolo dela (sw), no INSERT e no NORMAL; s&iacute;mbolos que s&atilde;o comandos Vim (<kbd>:</kbd> comando, <kbd>/</kbd> busca, <kbd>?</kbd> ajuda) entram nos modos correspondentes. Deslize para o canto tamb&eacute;m.</p>");

    b.append("<h2>Comandos <kbd>:</kbd></h2>");
    String[][] cmds = {
      {"help (h) [p&aacute;gina]", "abre a ajuda (padr&atilde;o: esta p&aacute;gina)"},
      {"copy (y, yank)", "copia a sele&ccedil;&atilde;o (ou a linha inteira)"},
      {"paste (p)", "cola o clipboard"},
      {"undo (u) / redo", "desfazer / refazer"},
      {"goto N (line N)", "vai para a linha N"},
      {"upper / lower / title", "caixa da sele&ccedil;&atilde;o ou linha"},
      {"browser &lt;url&gt; / float / br", "abre o navegador (esc fecha)"},
      {"ls help", "&iacute;ndice de todas as p&aacute;ginas de ajuda"},
    };
    for (String[] c : cmds)
      b.append("<p><kbd>:").append(c[0]).append("</kbd> &mdash; ").append(c[1]).append("</p>");

    b.append("<h2>Mais ajuda</h2>");
    b.append("<p><kbd>:ls help</kbd> &rarr; lista todas as p&aacute;ginas dispon&iacute;veis.</p>");
    b.append("<p>P&aacute;ginas: <kbd>:help lua</kbd> (scripts e API), <kbd>:help termux</kbd> " +
        "(conectar ao Termux), <kbd>:help comandos</kbd> (todos os comandos <kbd>:</kbd>).</p>");
    b.append(vim_page_foot());
    return b.toString();
  }

  /** Build the index page listing every help page (opened with [:ls help]). */
  static String vim_help_index_html()
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>vi_key &mdash; &iacute;ndice da ajuda</h1>");
    b.append("<p class=\"dim\"><kbd>:help &lt;nome&gt;</kbd> abre uma p&aacute;gina; " +
        "<kbd>esc</kbd> fecha. Cada chamada sobrescreve esta janela.</p><hr>");
    String[][] pages = {
      {"ajuda", "o b&aacute;sico do teclado vim (mesmo que <kbd>?</kbd>)"},
      {"lua", "scripts <kbd>.lua</kbd>, diret&oacute;rios e a API <kbd>vim.*</kbd>"},
      {"termux", "conectar o teclado ao Termux e criar plugins"},
      {"comandos", "todos os comandos <kbd>:</kbd> do teclado"},
    };
    for (String[] p : pages)
      b.append("<p><kbd>:help ").append(p[0]).append("</kbd> &mdash; ").append(p[1]).append("</p>");
    b.append(vim_page_foot());
    return b.toString();
  }

  /** Build the help page about the Lua scripts and the [vim.*] API. */
  static String vim_help_lua_html()
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>:help lua &mdash; scripts</h1>");
    b.append("<p class=\"dim\">Os scripts s&atilde;o executados dentro do teclado; " +
        "n&atilde;o dependem do Termux (que &eacute; opcional).</p><hr>");

    b.append("<h2>Onde ficam os scripts</h2>");
    b.append("<p>Pasta <kbd>/sdcard/keyboard-lua</kbd> (+ subpasta <kbd>plugins</kbd>). " +
        "<b>Essa pasta &eacute; do usu&aacute;rio</b> &mdash; em novas instala&ccedil;&otilde;es ela est&aacute; vazia. " +
        "No Android 11+ isso usa a permiss&atilde;o &ldquo;acesso a todos os arquivos&rdquo;; sem ela " +
        "os scripts ficam na pasta privada do aplicativo e o teclado avisa na barra de status.</p>");

    b.append("<h2>Carregar e gerenciar</h2>");
    b.append("<p><kbd>:reload</kbd> recarrega os scripts &middot; <kbd>:ls</kbd> lista os comandos " +
        "(o <kbd>:ls help</kbd> mostra as p&aacute;ginas de ajuda).</p>");
    b.append("<p><kbd>:addlua &lt;nome&gt;</kbd> salva o conteúdo do clipboard como um script; " +
        "<kbd>:rmlua &lt;nome&gt;</kbd> remove um script.</p>");
    b.append("<p>Cada arquivo <kbd>.lua</kbd> vira um comando <kbd>:&lt;nome&gt;</kbd> (nome sem " +
        "a extens&atilde;o). Um <kbd>vim.register(&quot;nome&quot;, fun&ccedil;&atilde;o)</kbd> dentro do " +
        "arquivo tem preced&ecirc;ncia sobre o nome do arquivo.</p>");

    b.append("<h2>API <kbd>vim.*</kbd></h2>");
    String[][] api = {
      {"register(nome, fn)", "registra um comando <kbd>:nome</kbd>"},
      {"get_text()", "todo o texto do editor"},
      {"get_sel()", "in&iacute;cio e fim da sele&ccedil;&atilde;o"},
      {"set_sel(in&iacute;cio, fim)", "move cursor/sele&ccedil;&atilde;o"},
      {"replace(in&iacute;cio, fim, texto)", "substitui um trecho"},
      {"send(texto)", "digita texto no cursor"},
      {"copy(texto) / paste()", "clipboard: copiar / colar"},
      {"clipboard()", "conte&uacute;do atual do clipboard"},
      {"status(msg)", "mensagem r&aacute;pida na barra de status"},
      {"page(texto)", "abre/sobrescreve uma p&aacute;gina estilo a de ajuda com o texto"},
    };
    for (String[] a : api)
      b.append("<p><kbd>vim.").append(a[0]).append("</kbd> &mdash; ").append(a[1]).append("</p>");
    b.append("<p class=\"dim\">Posi&ccedil;&otilde;es de <kbd>get_sel</kbd>/<kbd>set_sel</kbd>/" +
        "<kbd>replace</kbd> s&atilde;o relativas ao in&iacute;cio do texto de <kbd>get_text</kbd>.</p>");

    b.append("<h2>Exemplo</h2>");
    b.append("<pre>vim.register(\"ola\", function()\n" +
        "  vim.status(\"olá \" .. vim.clipboard())\n" +
        "end)\n</pre>");
    b.append("<p>Salve como <kbd>ola.lua</kbd>, rode <kbd>:reload</kbd> e use <kbd>:ola</kbd>. " +
        "Veja tamb&eacute;m <kbd>:help termux</kbd> para plugins que rodam comandos externos.</p>");
    b.append(vim_page_foot());
    return b.toString();
  }

/** Build the help page about connecting the keyboard to the Termux. */
  static String vim_help_termux_html()
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>:help termux &mdash; conectar ao Termux</h1>");
    b.append("<p class=\"dim\">A pasta <kbd>/sdcard/keyboard-lua</kbd> &eacute; do <b>usu&aacute;rio</b>, n&atilde;o do aplicativo. " +
        "Em novas instala&ccedil;&otilde;es ela est&aacute; vazia. Voc&ecirc; cria os arquivos abaixo.</p><hr>");

    b.append("<h2>O servi&ccedil;o <kbd>ipc-loop.sh</kbd></h2>");
    b.append("<p>Rode no Termux (em background). Ele vigia <kbd>data/cmd</kbd>, executa como shell " +
        "e grava a sa&iacute;da em <kbd>data/out</kbd>.</p>");
    b.append("<pre>#!/data/data/com.termux/files/usr/bin/bash\n" +
        "# ipc-loop.sh &mdash; ponte vi-key <-> Termux\n" +
        "#   uso: bash ~/storage/shared/keyboard-lua/ipc-loop.sh\n" +
        "DIR=\"$HOME/storage/shared/keyboard-lua\"\n" +
        "DATA=\"$DIR/data\"\n" +
        "mkdir -p \"$DIR\" \"$DATA\"\n" +
        "termux-wake-lock\n" +
        "while true; do\n" +
        "  if [ -f \"$DATA/cmd\" ]; then\n" +
        "    : > \"$DATA/out\"\n" +
        "    bash \"$DATA/cmd\" > \"$DATA/out\" 2>&1\n" +
        "    rm -f \"$DATA/cmd\"\n" +
        "  fi\n" +
        "  sleep 0.3\n" +
        "done</pre>");
    b.append("<p>No Termux: <kbd>mkdir -p ~/storage/shared/keyboard-lua/data</kbd>, " +
        "salve o c&oacute;digo acima como <kbd>ipc-loop.sh</kbd>, torne execut&aacute;vel " +
        "(<kbd>chmod +x ipc-loop.sh</kbd>) e rode <kbd>bash ipc-loop.sh &</kbd>.</p>");

    b.append("<h2>Plugin <kbd>termux.lua</kbd> (envia comandos)</h2>");
    b.append("<p>Salve como <kbd>/sdcard/keyboard-lua/plugins/termux.lua</kbd> (pode criar no Termux " +
        "em <kbd>~/storage/shared/keyboard-lua/plugins/termux.lua</kbd>) e rode <kbd>:reload</kbd>. " +
        "Uso: <kbd>:termux <comando></kbd>.</p>");
    b.append("<pre>-- termux.lua &mdash; roda um comando no Termux e mostra a sa&iacute;da na statusbar\n" +
        "--   uso: :termux <comando shell>\n" +
        "local DIR = \"/sdcard/keyboard-lua/data\"\n" +
        "local cmd = table.concat({...}, \" \")\n" +
        "if cmd == \"\" then\n" +
        "  vim.status(\"uso: :termux <comando>\")\n" +
        "  return\n" +
        "end\n" +
        "os.execute(\"mkdir -p \" .. DIR)\n" +
        "local tmp = DIR .. \"/cmd.tmp\"\n" +
        "local f = io.open(tmp, \"w\")\n" +
        "f:write(cmd .. \"\\n\")\n" +
        "f:close()\n" +
        "os.remove(DIR .. \"/cmd\")\n" +
        "os.rename(tmp, DIR .. \"/cmd\")\n" +
        "local done = false\n" +
        "for i = 1, 50 do\n" +
        "  local c = io.open(DIR .. \"/cmd\", \"r\")\n" +
        "  if c then\n" +
        "    c:close()\n" +
        "    os.execute(\"sleep 0.2\")\n" +
        "  else\n" +
        "    done = true\n" +
        "    break\n" +
        "  end\n" +
        "end\n" +
        "if not done then\n" +
        "  vim.status(\"termux n\u00e3o respondeu (checar servi\u00e7o keyboard-ipc)\")\n" +
        "  return\n" +
        "end\n" +
        "local o = io.open(DIR .. \"/out\", \"r\")\n" +
        "if not o then\n" +
        "  vim.status(\"(sem sa\u00edda)\")\n" +
        "  return\n" +
        "end\n" +
        "local s = o:read(\"*a\")\n" +
        "o:close()\n" +
        "if s == \"\" then s = \"(sem sa\u00edda)\" end\n" +
        "vim.status(s:sub(1, 300))</pre>");

    b.append("<h2>Plugin <kbd>termuxout.lua</kbd> (mostra &uacute;ltima sa&iacute;da)</h2>");
    b.append("<p>Salve como <kbd>/sdcard/keyboard-lua/plugins/termuxout.lua</kbd>, <kbd>:reload</kbd>. " +
        "Uso: <kbd>:termuxout</kbd>.</p>");
    b.append("<pre>-- termuxout.lua &mdash; mostra a &uacute;ltima sa&iacute;da do Termux na statusbar\n" +
        "--   uso: :termuxout\n" +
        "local f = io.open(\"/sdcard/keyboard-lua/data/out\", \"r\")\n" +
        "if not f then\n" +
        "  vim.status(\"(sem sa\u00edda ainda)\")\n" +
        "  return\n" +
        "end\n" +
        "local s = f:read(\"*a\")\n" +
        "f:close()\n" +
        "if s == \"\" then s = \"(vazio)\" end\n" +
        "vim.status(s:sub(1, 200))</pre>");

    b.append("<p class=\"dim\">Depois da integra&ccedil;&atilde;o, voc&ecirc; cria os seus pr&oacute;prios scripts " +
        "Lua na pasta <kbd>plugins</kbd> e usa <kbd>:reload</kbd> para ativ&aacute;-los. " +
        "Veja <kbd>:help lua</kbd> para a API <kbd>vim.*</kbd> completa.</p>");
    b.append(vim_page_foot());
    return b.toString();
  }

  /** Build the help page listing every built-in ':' command. */
  static String vim_help_commands_html()
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>:help comandos &mdash; comandos <kbd>:</kbd></h1>");
    b.append("<p class=\"dim\">Sem argumentos o comando volta ao modo NORMAL.</p><hr>");
    String[][] cmds = {
      {"help (h) [p&aacute;gina]", "abre a ajuda; <kbd>:ls help</kbd> lista as p&aacute;ginas"},
      {"ls [help]", "lista os comandos Lua; com <kbd>help</kbd>, &iacute;ndice das p&aacute;ginas"},
      {"copy (y, yank)", "copia a sele&ccedil;&atilde;o (ou a linha inteira)"},
      {"paste (p)", "cola o clipboard"},
      {"undo (u) / redo", "desfazer / refazer"},
      {"goto N (line N)", "vai para a linha N"},
      {"upper / lower / title", "caixa da sele&ccedil;&atilde;o ou da linha"},
      {"reload", "recarrega os scripts Lua"},
      {"addlua &lt;nome&gt;", "salva o clipboard como um script Lua"},
      {"rmlua &lt;nome&gt;", "remove um script Lua"},
      {"browser (br / float) [url]", "abre o navegador; esc fecha"},
    };
    for (String[] c : cmds)
      b.append("<p><kbd>:").append(c[0]).append("</kbd> &mdash; ").append(c[1]).append("</p>");
    b.append("<p class=\"dim\">Al&eacute;m dos built-in, cada script Lua vira um comando <kbd>:&lt;nome&gt;</kbd> " +
        "e plugins de exemplo como <kbd>termux</kbd>/<kbd>cat</kbd>/<kbd>termuxout</kbd> ficam " +
        "dispon&iacute;veis (veja <kbd>:help termux</kbd>).</p>");
    b.append(vim_page_foot());
    return b.toString();
  }

  /** Names of the pages known by [vim_help_page_html]. */
  static String[] vim_help_page_names()
  {
    return new String[] { "ajuda", "comandos", "lua", "termux" };
  }

  /** The full HTML of the help page [name], or [null] when unknown. */
  static String vim_help_page_html(String name)
  {
    if (name == null)
      return null;
    name = name.toLowerCase(Locale.ROOT);
    if (name.equals("ajuda") || name.equals("help"))
      return vim_help_html();
    if (name.equals("lua"))
      return vim_help_lua_html();
    if (name.equals("termux"))
      return vim_help_termux_html();
    if (name.equals("comandos"))
      return vim_help_commands_html();
    return null;
  }

  /** Open (or update) the help page [name]; an empty name opens the basics
      page. Unknown names flash a hint instead of opening a page. */
  void open_help_page(String name)
  {
    name = (name == null) ? "" : name.trim().toLowerCase(Locale.ROOT);
    if (name.isEmpty())
      name = "ajuda";
    String html = vim_help_page_html(name);
    if (html == null)
    {
      _vim.flash_status("p\u00e1gina de ajuda desconhecida: " + name + " (use :ls help)",
          VimEngine.STATUS_COLOR_CMD);
      return;
    }
    _recv.open_page(name, html);
  }

  /** The HTML head shared by the help page and the script content pages. */
  static String vim_page_head()
  {
    StringBuilder b = new StringBuilder();
    b.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
    b.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    b.append("<style>body{background:#1d2021;color:#ebdbb2;font-family:monospace;margin:16px;font-size:15px;line-height:1.6}");
    b.append("h1{color:#fe8019;font-size:20px;margin:4px 0}h2{color:#83a598;font-size:16px;margin:20px 0 8px}");
    b.append("kbd{background:#3c3836;color:#fb4934;padding:1px 6px;border-radius:3px;font-weight:bold}");
    b.append(".dim{color:#a89984}hr{border:0;border-top:1px solid #3c3836}");
    b.append("pre{background:#282828;border:1px solid #3c3836;padding:8px;border-radius:4px;overflow-x:auto}</style></head><body>");
    return b.toString();
  }

  static String vim_page_foot()
  {
    return "</body></html>";
  }

  /** Escape [text] for safe embedding in HTML. */
  static String vim_escape_html(String text)
  {
    StringBuilder b = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++)
    {
      switch (text.charAt(i))
      {
        case '&': b.append("&amp;"); break;
        case '<': b.append("&lt;"); break;
        case '>': b.append("&gt;"); break;
        case '"': b.append("&quot;"); break;
        default: b.append(text.charAt(i));
      }
    }
    return b.toString();
  }

  /** An HTML page (styled like the help page) showing [text] as plain text in
      a [pre] block. */
  static String vim_page_html(String title, String text)
  {
    StringBuilder b = new StringBuilder();
    b.append(vim_page_head());
    b.append("<h1>").append(title).append("</h1>");
    b.append("<p class=\"dim\">Esc fecha esta janela; outra chamada sobrescreve " +
        "o conte&uacute;do.</p><hr>");
    b.append("<pre>").append(vim_escape_html(text)).append("</pre>");
    b.append(vim_page_foot());
    return b.toString();
  }

  /** Open (or update) a browser page with [text] as plain text, styled like
      the help page. Listed in the Lua API as [vim.page]. */
  void open_page(String title, String text)
  {
    _recv.open_page(title, vim_page_html(title, text));
  }

  /** Move the cursor to the start of line [arg] (1-indexed). */
  void vim_goto_line(String arg)
  {
    int line;
    try { line = Integer.parseInt(arg); }
    catch (NumberFormatException _e)
    {
      _vim.flash_status("usage: goto N", VimEngine.STATUS_COLOR_CMD);
      return;
    }
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_full_text(conn);
    if (et == null || et.text == null)
      return;
    String text = et.text.toString();
    int off = 0;
    int n = 1;
    while (n < line)
    {
      int i = text.indexOf('\n', off);
      if (i < 0)
      {
        off = text.length();
        break;
      }
      off = i + 1;
      n++;
    }
    int abs = et.startOffset + off;
    conn.setSelection(abs, abs);
  }

  /** Change the case of the current selection, or of the whole line. */
  void vim_transform_selection_or_line(String mode)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_full_text(conn);
    if (et == null || et.text == null)
      return;
    String text = et.text.toString();
    int base = et.startOffset;
    int s = et.selectionStart - base;
    int e = et.selectionEnd - base;
    if (s == e)
    {
      int ls = text.lastIndexOf('\n', s - 1) + 1;
      if (ls < 0) ls = 0;
      int le = text.indexOf('\n', s);
      if (le < 0) le = text.length();
      s = ls;
      e = le;
    }
    if (e <= s || s < 0 || e > text.length())
      return;
    String sel = text.substring(s, e);
    String res;
    if (mode.equals("upper"))
      res = sel.toUpperCase(Locale.ROOT);
    else if (mode.equals("lower"))
      res = sel.toLowerCase(Locale.ROOT);
    else
      res = titlize(sel);
    replace_surrounding_text_abs(base + s, e - s, res);
  }

  /** Replace the [len] characters starting at absolute position [abs] with
      [replace], leaving the cursor after it. This uses [InputConnection.setSelection]
      plus [InputConnection.commitText], which replaces the selection. That works
      in editors whose [InputConnection.deleteSurroundingText] ignores the after
      length or can't touch the selection contents (e.g. Termux). */
  void replace_surrounding_text_abs(int abs, int len, String replace)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.setSelection(abs, abs + len);
    conn.commitText(replace, 1);
  }

  /** Uppercase the first letter of every word, lowercase the rest. */
  static String titlize(String s)
  {
    StringBuilder out = new StringBuilder(s.length());
    boolean word_start = true;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (Character.isWhitespace(c))
      {
        out.append(c);
        word_start = true;
      }
      else if (word_start)
      {
        out.append(Character.toUpperCase(c));
        word_start = false;
      }
      else
        out.append(Character.toLowerCase(c));
    }
    return out.toString();
  }

  void set_clipboard_text(String text)
  {
    Context ctx = _recv.getApplicationContext();
    if (ctx == null)
      return;
    ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null)
      cm.setPrimaryClip(ClipData.newPlainText("vim", text));
  }

  public static interface IReceiver extends Suggestions.Callback
  {
    public void handle_event_key(KeyValue.Event ev);
    public void set_shift_state(boolean state, boolean lock);
    public void set_compose_pending(boolean pending);
    public void selection_state_changed(boolean selection_is_ongoing);
    public InputConnection getCurrentInputConnection();
    public Handler getHandler();
    public Context getApplicationContext();
    /** Update the VIM mode status bar. */
    public void set_vim_status(String text, int color);
    /** Whether the embedded floating web browser panel is open. */
    public boolean is_float_open();
    /** Open or close the embedded floating web browser panel. */
    public void toggle_float_panel(String url);
    /** Close the embedded floating web browser panel. */
    public void close_float_panel();
    /** Open the built-in VIM/command help page. */
    public void open_help();
    /** Open (or update) a browser page showing the given HTML content, in the
        same window as the help page. Re-calling replaces the content. */
    public void open_page(String title, String html);
  }

  class Autocapitalisation_callback implements Autocapitalisation.Callback
  {
    @Override
    public void update_shift_state(boolean should_enable, boolean should_disable)
    {
      if (should_enable)
        _recv.set_shift_state(true, false);
      else if (should_disable)
        _recv.set_shift_state(false, false);
    }
  }

  public static enum LastAction
  {
    SUGGESTION_ENTERED,
    OTHER
  }
}
