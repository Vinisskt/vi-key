package juloo.keyboard2;

import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;

/** Vim-style editing engine.
    Characters typed are interpreted according to the current mode: INSERT
    types them, NORMAL interprets them as editing commands and SEARCH appends
    them to a search query.
    Only the main Vim commands are implemented: [hjkl] movement, [wbe] word
    motions, [0$] line start/end, [ggG] document start/end, [dd], [dw], [de],
    [x], undo with [u], redo with ctrl+r, [i] to switch back to insert mode,
    [o] to open a new line below and [O] to open one above, [/?] search forward
    and backward ([/] searches, [?] opens the built-in help),
    and [jk] or [ctrl]+escape to switch back to normal mode. Counts are
    supported for most commands.
    Unknown commands are silently ignored, mimicking a "beep". */
public final class VimEngine
{
  public static final int MODE_INSERT = 0;
  public static final int MODE_NORMAL = 1;
  public static final int MODE_SEARCH = 2;
  public static final int MODE_CMD = 3;

  private static final long JK_DELAY_MS = 300;

  // Status bar background colors (Neovim-style mode indicators). The text
  // drawn on top of them is always dark.
  private static final int STATUS_COLOR_INSERT = 0xFF83A598;
  private static final int STATUS_COLOR_NORMAL = 0xFF8EC07C;
  private static final int STATUS_COLOR_SEARCH = 0xFFD79921;
  final static int STATUS_COLOR_CMD = 0xFFFE8019;

  final KeyEventHandler _handler;
  final SearchBar _search;
  final CommandBar _cmd;

  int _mode = MODE_INSERT;
  boolean _pending_jk = false;
  final Runnable _jk_delay = new Runnable() { public void run() { flush_pending_j(); } };
  final StringBuilder _count = new StringBuilder();
  char _op = 0;
  boolean _pending_g = false;

  VimEngine(KeyEventHandler handler)
  {
    _handler = handler;
    _search = new SearchBar(this);
    _cmd = new CommandBar(this);
    update_status();
  }

  void reset()
  {
    _mode = MODE_INSERT;
    _pending_jk = false;
    _op = 0;
    _pending_g = false;
    _count.setLength(0);
    _handler.get_handler().removeCallbacks(_jk_delay);
    _search.reset();
    _cmd.reset();
    update_status();
  }

  /** Called before every key is dispatched. */
  boolean on_key(KeyValue kv, int metaState)
  {
    // While a 'j' is pending in insert mode, the next key is allowed to be a
    // 'k' to switch to normal mode. Any other key flushes the pending 'j'.
    if (_mode == MODE_INSERT && _pending_jk
        && !(kv.getKind() == KeyValue.Kind.Char && kv.getChar() == 'k'))
      flush_pending_j();
    switch (kv.getKind())
    {
      case Char:
      {
        char c = kv.getChar();
        if (_mode == MODE_SEARCH)
        {
          _search.type(c);
          return true;
        }
        if (_mode == MODE_CMD)
        {
          _cmd.type(c);
          return true;
        }
        if (_mode == MODE_INSERT)
          return on_insert_char(c);
        if ((metaState & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) != 0)
        {
          if (c == 'r' || c == 'R')
            _handler.handle_editing_key(KeyValue.Editing.REDO);
          // Consume ctrl+char keys in normal mode
          return true;
        }
        return on_normal_char(c);
      }
      case Editing: return on_editing_key(kv.getEditing());
      case Keyevent:
        switch (kv.getKeyevent())
        {
          case KeyEvent.KEYCODE_ENTER: return on_enter();
          case KeyEvent.KEYCODE_ESCAPE:
            if (_mode == MODE_INSERT
                && (metaState & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) == 0)
              return false; // Pass the escape key to the app (Termux, nvim...)
            return on_escape();
          default: return false;
        }
      default:
        return false;
    }
  }

  boolean on_insert_char(char c)
  {
    if (_pending_jk)
    {
      if (c == 'k')
      {
        _pending_jk = false;
        _handler.get_handler().removeCallbacks(_jk_delay);
        set_mode(MODE_NORMAL);
        return true;
      }
      flush_pending_j();
    }
    if (c == 'j')
    {
      _pending_jk = true;
      _handler.get_handler().postDelayed(_jk_delay, JK_DELAY_MS);
      return true;
    }
    _handler.send_text(String.valueOf(c));
    return true;
  }

  /** Whether the engine currently accepts plain text input. */
  boolean is_insert()
  {
    return _mode == MODE_INSERT;
  }

  /** Cancels a character buffered by the quick 'jk' escape (the first 'j' of
      an eventual 'jk' sequence), if any. Returns [true] when a buffered
      character was cancelled. Used by the quick double-tap feature so it can
      replace the pending character instead of outputting it. */
  boolean cancel_pending_char()
  {
    if (!_pending_jk)
      return false;
    _pending_jk = false;
    _handler.get_handler().removeCallbacks(_jk_delay);
    return true;
  }

  boolean on_enter()
  {
    if (_mode == MODE_NORMAL)
    {
      _move_j(1);
      return true;
    }
    if (_mode == MODE_SEARCH)
    {
      _search.commit();
      return true;
    }
    if (_mode == MODE_CMD)
    {
      _cmd.execute();
      return true;
    }
    return false;
  }

  boolean on_escape()
  {
    if (_mode == MODE_SEARCH)
    {
      _search.cancel();
      return true;
    }
    if (_mode == MODE_CMD)
    {
      _cmd.cancel();
      return true;
    }
    if (_mode == MODE_INSERT)
    {
      set_mode(MODE_NORMAL);
      return true;
    }
    return true;
  }

  boolean on_editing_key(KeyValue.Editing ev)
  {
    if (_mode == MODE_SEARCH)
    {
      switch (ev)
      {
        case BACKSPACE: _search.backspace(); return true;
        case SPACE_BAR: _search.type(' '); return true;
        default: return false;
      }
    }
    if (_mode == MODE_CMD)
    {
      switch (ev)
      {
        case BACKSPACE: _cmd.backspace(); return true;
        case SPACE_BAR: _cmd.type(' '); return true;
        default: return false;
      }
    }
    if (_mode == MODE_NORMAL)
    {
      switch (ev)
      {
        case BACKSPACE: delete_forward(1); return true;
        case SPACE_BAR: return true; // Consume the space bar in normal mode
        default: return false;
      }
    }
    return false;
  }

  boolean on_normal_char(char c)
  {
    if (c == 'N')
    {
      _search.prev();
      _clear_count();
      return true;
    }
    if (c == 'G')
    {
      move_doc_or_line();
      _clear_count();
      return true;
    }
    if (c == 'O')
    {
      open_line_above();
      _clear_count();
      return true;
    }
    c = Character.toLowerCase(c);
    if (_pending_g)
    {
      _pending_g = false;
      if (c == 'g')
      {
        move_doc_start();
        _clear_count();
        return true;
      }
    }
    // Digits build a count, except for a leading '0' which is a command.
    if (c >= '0' && c <= '9')
    {
      if (_count.length() > 0 || c != '0')
      {
        _count.append(c);
        return true;
      }
    }
    if (_op == 'd')
    {
      switch (c)
      {
        case 'd': _op = 0; action_delete_line(count()); _clear_count(); return true;
        case 'w': _op = 0; action_delete_word(count()); _clear_count(); return true;
        case 'e': _op = 0; action_delete_word_end(count()); _clear_count(); return true;
        case '0': _op = 0; action_delete_col_start(); _clear_count(); return true;
        case '$': _op = 0; action_delete_col_end(); _clear_count(); return true;
        default: _op = 0; break;
      }
    }
    switch (c)
    {
      case 'i': _clear_count(); set_mode(MODE_INSERT); return true;
      case 'o': open_line_below(); return true;
      case 'O': open_line_above(); return true;
      case 'h': _move_h(count()); _clear_count(); return true;
      case 'j': _move_j(count()); _clear_count(); return true;
      case 'k': _move_k(count()); _clear_count(); return true;
      case 'l': _move_l(count()); _clear_count(); return true;
      case '0': col_start(); _clear_count(); return true;
      case '$': col_end(); _clear_count(); return true;
      case 'w': move_word_forward(count()); _clear_count(); return true;
      case 'b': move_word_backward(count()); _clear_count(); return true;
      case 'e': move_word_end_forward(count()); _clear_count(); return true;
      case 'g': _pending_g = true; return true;
      case 'x': delete_forward(count()); _clear_count(); return true;
      case 'd': _op = 'd'; return true;
      case 'u': _handler.send_context_menu_action(android.R.id.undo); _clear_count(); return true;
      case '/': _begin_search(); _clear_count(); return true;
      case ':': _begin_command(); _clear_count(); return true;
      case '?': _handler.open_vim_help(); _clear_count(); return true;
      case 'n': _search.next(); _clear_count(); return true;
      default: _clear_count(); return true;
    }
  }

  int count()
  {
    int n = _count.length();
    if (n == 0)
      return 1;
    try { return Integer.parseInt(_count.toString()); }
    catch (NumberFormatException _e) { return 1; }
  }

  void _clear_count()
  {
    _count.setLength(0);
  }

  void set_mode(int mode)
  {
    _mode = mode;
    _pending_jk = false;
    _op = 0;
    _pending_g = false;
    _count.setLength(0);
    _handler.get_handler().removeCallbacks(_jk_delay);
    update_status();
  }

  void flush_pending_j()
  {
    if (!_pending_jk)
      return;
    _pending_jk = false;
    _handler.get_handler().removeCallbacks(_jk_delay);
    _handler.send_text("j");
  }

  void _begin_search()
  {
    _search.begin();
    set_mode(MODE_SEARCH);
  }

  void _begin_command()
  {
    _cmd.begin();
    set_mode(MODE_CMD);
  }

  /** Display a transient message in the status bar (used to report the result
      of a command while the keyboard is in normal mode). */
  void flash_status(String text, int color)
  {
    _handler._recv.set_vim_status(text, color);
    _handler.get_handler().postDelayed(new Runnable() {
      public void run() { update_status(); }
    }, 1600);
  }

  void update_status()
  {
    String text;
    int color;
    switch (_mode)
    {
      case MODE_INSERT: text = "INSERT"; color = STATUS_COLOR_INSERT; break;
      case MODE_NORMAL: text = "NORMAL"; color = STATUS_COLOR_NORMAL; break;
      case MODE_CMD: text = ":" + _cmd.command(); color = STATUS_COLOR_CMD; break;
      default:
        text = "/" + _search.query();
        if (_search.no_match())
          text += " [no match]";
        color = STATUS_COLOR_SEARCH;
        break;
    }
    _handler._recv.set_vim_status(text, color);
  }

  // ---- Movement --------------------------------------------------------
  //
  // [h] [j] [k] [l] and [0$] are implemented with key events, which are
  // universally supported. Word motions and document start/end use the full
  // text and [InputConnection.setSelection].

  void _move_h(int n) { vim_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, n); }
  void _move_j(int n) { vim_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_DOWN, n); }
  void _move_k(int n) { vim_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_UP, n); }
  void _move_l(int n) { vim_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, n); }

  void col_start() { vim_key_down_up(KeyEvent.KEYCODE_MOVE_HOME); }
  void col_end() { vim_key_down_up(KeyEvent.KEYCODE_MOVE_END); }

  void vim_key_down_up(int code)
  {
    _handler.send_key_down_up(code, 0);
  }

  void vim_key_down_up_repeat(int code, int repeat)
  {
    while (repeat-- > 0)
      vim_key_down_up(code);
  }

  void set_sel(int abs)
  {
    InputConnection conn = get_conn();
    if (conn != null)
      conn.setSelection(abs, abs);
  }

  /** [o] command: open a new line below the cursor line and switch to insert
      mode. */
  void open_line_below()
  {
    TextData td = get_text();
    if (td == null)
    {
      _clear_count();
      return;
    }
    int rel = td.cursor_rel;
    int line_end = rel;
    while (line_end < td.n && td.text.charAt(line_end) != '\n')
      line_end++;
    set_sel(td.base + line_end);
    _handler.send_text("\n");
    _clear_count();
    set_mode(MODE_INSERT);
  }

  /** [O] command: open a new line above the cursor line and switch to insert
      mode. */
  void open_line_above()
  {
    TextData td = get_text();
    if (td == null)
    {
      _clear_count();
      return;
    }
    int rel = td.cursor_rel;
    int line_start = rel;
    while (line_start > 0 && td.text.charAt(line_start - 1) != '\n')
      line_start--;
    set_sel(td.base + line_start);
    _handler.send_text("\n");
    _clear_count();
    set_mode(MODE_INSERT);
  }

  void move_word_forward(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int start = word_start_after(td.text, td.cursor_rel);
      if (start == td.cursor_rel || start >= td.n)
        return;
      set_sel(td.base + start);
    }
  }

  void move_word_backward(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int start = word_start_before(td.text, td.cursor_rel);
      if (start == td.cursor_rel)
        return;
      set_sel(td.base + start);
    }
  }

  void move_word_end_forward(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int end = word_end_forward(td.text, td.cursor_rel);
      if (end <= td.cursor_rel)
        return;
      set_sel(td.base + end - 1);
    }
  }

  void move_doc_start()
  {
    TextData td = get_text();
    if (td == null)
      return;
    set_sel(td.base);
  }

  void move_doc_or_line()
  {
    TextData td = get_text();
    if (td == null)
      return;
    int n = count();
    if (n <= 1)
    {
      set_sel(td.base + td.n);
      return;
    }
    // Move to the start of the [n]-th line. Lines are separated by '\n'.
    int lines = 0;
    int target = 0;
    for (int i = 0; i < td.n && lines < n - 1; i++)
    {
      if (td.text.charAt(i) == '\n')
      {
        lines++;
        target = i + 1;
      }
    }
    set_sel(td.base + target);
  }

  // ---- Deletions -------------------------------------------------------

  void delete_range(TextData td, int before, int after)
  {
    InputConnection conn = get_conn();
    if (conn == null || (before == 0 && after == 0))
      return;
    int new_cursor = td.cursor_rel - before;
    if (new_cursor < 0)
      new_cursor = 0;
    conn.beginBatchEdit();
    conn.deleteSurroundingText(before, after);
    conn.setSelection(td.base + new_cursor, td.base + new_cursor);
    conn.endBatchEdit();
  }

  void delete_forward(int count)
  {
    TextData td = get_text();
    if (td == null)
    {
      InputConnection conn = get_conn();
      if (conn != null)
        conn.deleteSurroundingText(0, count);
      return;
    }
    if (td.sel_s_rel != td.sel_e_rel)
    {
      // Delete the selection instead of a character.
      int before = td.cursor_rel - td.sel_s_rel;
      int after = td.sel_e_rel - td.cursor_rel;
      delete_range(td, before, after);
      return;
    }
    delete_range(td, 0, count);
  }

  void action_delete_line(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int rel = td.cursor_rel;
      int line_start = rel;
      while (line_start > 0 && td.text.charAt(line_start - 1) != '\n')
        line_start--;
      int line_end = rel;
      while (line_end < td.n && td.text.charAt(line_end) != '\n')
        line_end++;
      int before = rel - line_start;
      int after = line_end - rel;
      if (line_end < td.n)
        after++; // Include the line ending
      delete_range(td, before, after);
    }
  }

  void action_delete_word(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int end = word_start_after(td.text, td.cursor_rel);
      if (end <= td.cursor_rel || end >= td.n)
      {
        // Nothing to delete: still delete trailing separators before EOF.
        if (end > td.cursor_rel)
          delete_range(td, 0, end - td.cursor_rel);
        return;
      }
      delete_range(td, 0, end - td.cursor_rel);
    }
  }

  void action_delete_word_end(int count)
  {
    for (int i = 0; i < count; i++)
    {
      TextData td = get_text();
      if (td == null)
        return;
      int end = word_end_forward(td.text, td.cursor_rel);
      if (end <= td.cursor_rel)
        return;
      delete_range(td, 0, end - td.cursor_rel);
    }
  }

  void action_delete_col_start()
  {
    TextData td = get_text();
    if (td == null)
      return;
    int rel = td.cursor_rel;
    int line_start = rel;
    while (line_start > 0 && td.text.charAt(line_start - 1) != '\n')
      line_start--;
    delete_range(td, rel - line_start, 0);
  }

  void action_delete_col_end()
  {
    TextData td = get_text();
    if (td == null)
      return;
    int rel = td.cursor_rel;
    int line_end = rel;
    while (line_end < td.n && td.text.charAt(line_end) != '\n')
      line_end++;
    delete_range(td, 0, line_end - rel);
  }

  // ---- Text access -----------------------------------------------------

  InputConnection get_conn()
  {
    return _handler._recv.getCurrentInputConnection();
  }

  static final class TextData
  {
    final String text;
    final int base;
    final int n;
    final int cursor_rel;
    final int sel_s_rel;
    final int sel_e_rel;

    TextData(String t, int base_, int cursor_rel_, int sel_s_, int sel_e_)
    {
      text = t;
      base = base_;
      n = t.length();
      cursor_rel = cursor_rel_;
      sel_s_rel = sel_s_;
      sel_e_rel = sel_e_;
    }
  }

  TextData get_text()
  {
    InputConnection conn = get_conn();
    if (conn == null)
      return null;
    ExtractedText et = _handler.get_full_text(conn);
    if (et == null || et.text == null)
      return null;
    String text = et.text.toString();
    int base = et.startOffset;
    int sel_s = et.selectionStart;
    int sel_e = et.selectionEnd;
    if (sel_s < 0)
      return null;
    if (sel_e < sel_s)
    {
      int tmp = sel_e;
      sel_e = sel_s;
      sel_s = tmp;
    }
    if (sel_s > text.length())
      return null;
    return new TextData(text, base, sel_s, sel_s, sel_e);
  }

  // ---- Word helpers ----------------------------------------------------

  static boolean is_word_char(char c)
  {
    return (c >= 'a' && c <= 'z')
      || (c >= 'A' && c <= 'Z')
      || (c >= '0' && c <= '9')
      || c == '_';
  }

  /** Start of the next word (after the current word if any and the
      separators). */
  static int word_start_after(String s, int i)
  {
    int n = s.length();
    if (i < n && is_word_char(s.charAt(i)))
    {
      while (i < n && is_word_char(s.charAt(i)))
        i++;
    }
    while (i < n && !is_word_char(s.charAt(i)))
      i++;
    return i;
  }

  /** Start of the previous word. */
  static int word_start_before(String s, int i)
  {
    int j = i;
    while (j > 0 && !is_word_char(s.charAt(j - 1)))
      j--;
    while (j > 0 && is_word_char(s.charAt(j - 1)))
      j--;
    return j;
  }

  /** Position after the end of the current word, or of the next word. */
  static int word_end_forward(String s, int i)
  {
    int n = s.length();
    if (i >= n)
      return n;
    if (is_word_char(s.charAt(i)))
    {
      while (i < n && is_word_char(s.charAt(i)))
        i++;
      return i;
    }
    while (i < n && !is_word_char(s.charAt(i)))
      i++;
    while (i < n && is_word_char(s.charAt(i)))
      i++;
    return i;
  }
}