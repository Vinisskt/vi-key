package com.vinisskt.vikey;

import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;
import java.util.HashMap;

/** Vim-style editing engine.
    Characters typed are interpreted according to the current mode: INSERT
    types them, NORMAL interprets them as editing commands and SEARCH appends
    them to a search query.
    Only the main Vim commands are implemented: [hjkl] movement, [wbe] word
    motions, [0$] line start/end, [ggG] document start/end, [dd], [dw], [de], [yy], [yw], [ye], [x], undo with [u],
    redo with ctrl+r, [p]/[P] paste, registers selected with ["]name] ([a-z]),
    [i] to switch back to insert mode
    before the cursor and [a] to append after it,
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
  public static final int MODE_SCROLL = 4;

  private static final long JK_DELAY_MS = 300;

  // ---- Multi-tap accent (PT-BR) ----
  // 1 toque = letra normal; 2+ toques rápidos na mesma tecla ciclam pelos
  // acentos (2=agudo, 3=circunflexo, 4=til, 5=grave; c: 2=ç). A primeira letra
  // é digitada na hora e os toques adicionais a substituem (replace), então
  // não há latência; digitar duas letras iguais seguidas exige uma pausa maior
  // que MULTI_TAP_TIMEOUT_MS entre os toques.
  static final boolean MULTI_TAP_ENABLED = true;
  static final long MULTI_TAP_TIMEOUT_MS = 350;
  /** Letra da sequência atual (0 = nenhuma em andamento). */
  char _mt_key = 0;
  /** Contagem de toques rápidos na [_mt_key]. */
  int _mt_count = 0;
  /** Momento do último toque (System.currentTimeMillis). */
  long _mt_ts = 0;
  /** Um toque real está em andamento (key_down visto, key_up ainda por vir).
      Repetições por segurar chamam key_up SEM um key_down novo, então nunca
      são contadas como toque. */
  boolean _mt_press_pending = false;

  /** Acentos por letra e nível de toque (a prioridade é agudo, circunflexo,
      til, grave, diérese). */
  static char accent_variant(char lc, int n)
  {
    switch (lc)
    {
      case 'a': switch (n) { case 2: return 'á'; case 3: return 'â'; case 4: return 'ã'; case 5: return 'à'; }
      case 'e': switch (n) { case 2: return 'é'; case 3: return 'ê'; case 4: return 'è'; }
      case 'i': switch (n) { case 2: return 'í'; case 3: return 'î'; case 4: return 'ì'; }
      case 'o': switch (n) { case 2: return 'ó'; case 3: return 'ô'; case 4: return 'õ'; case 5: return 'ò'; }
      case 'u': switch (n) { case 2: return 'ú'; case 3: return 'û'; case 4: return 'ü'; case 5: return 'ù'; }
      case 'c': switch (n) { case 2: return 'ç'; }
    }
    return 0;
  }

  static boolean is_accent_key(char c)
  {
    switch (Character.toLowerCase(c))
    {
      case 'a': case 'e': case 'i': case 'o': case 'u': case 'c': return true;
      default: return false;
    }
  }

  /** Quando uma tecla desce (toque real ou não). Prepara o dedup de toques. */
  void note_key_down(char c)
  {
    if (!MULTI_TAP_ENABLED || !is_insert() || !is_accent_key(c))
    {
      reset_multitap();
      return;
    }
    _mt_press_pending = true;
  }

  /** Um key_up de repetição por segurar (não é toque). */
  void on_pointer_repeat()
  {
    _mt_press_pending = false;
  }

  void reset_multitap()
  {
    _mt_key = 0;
    _mt_count = 0;
    _mt_ts = 0;
    _mt_press_pending = false;
  }

  /** Decide o que digitar para um toque em [c], em modo INSERT. Retorna o
      caractere acentuado (substituindo o anterior) ou 0 para digitar normal. */
  char multitap_accent(char c)
  {
    if (!MULTI_TAP_ENABLED)
      return 0;
    long now = System.currentTimeMillis();
    boolean tap = _mt_press_pending;
    _mt_press_pending = false;
    if (!tap)  // key_up sem key_down: repetição por segurar, não conta
    {
      reset_multitap();
      return 0;
    }
    if (_mt_key == c && now - _mt_ts <= MULTI_TAP_TIMEOUT_MS)
      _mt_count++;
    else
    {
      _mt_key = c;
      _mt_count = 1;
    }
    _mt_ts = now;
    if (_mt_count <= 1)
      return 0;
    char v = accent_variant(Character.toLowerCase(c), _mt_count);
    if (v == 0)
      return 0;
    if (Character.isUpperCase(c))
      return Character.toUpperCase(v);
    return v;
  }

  /** Substitui o caractere anterior pelo acento do multi-tap. */
  void accent_multi_tap(char c)
  {
    _handler.accent_multi_tap_replace(c);
  }

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
  /** Clipboard registers: named registers [a-z] and the unnamed register (the
      last yanked or deleted text). A register is selected with ["a]..["z] and
      applies to the next yank/delete/paste command. */
  final HashMap<Character,String> _registers = new HashMap<Character,String>();
  String _unnamed_register = "";
  char _cmd_register = 0;
  /** [true] when an uppercase register (["A]) was selected: the next yank
      appends to the register instead of overwriting it (vim semantics). */
  boolean _register_append = false;
  boolean _register_pending = false;

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
    _cmd_register = 0;
    _register_append = false;
    _register_pending = false;
    _handler.get_handler().removeCallbacks(_jk_delay);
    _search.reset();
    _cmd.reset();
    _registers.clear();
    _unnamed_register = "";
    reset_multitap();
    update_status();
  }

  /** Called before every key is dispatched. */
  boolean on_key(KeyValue kv, int metaState)
  {
    // A multi-tap só faz sentido entre toques da mesma letra: qualquer outra
    // tecla (espaço, enter, backspace, seta, modificador...) interrompe a
    // sequência.
    if (kv.getKind() != KeyValue.Kind.Char)
      reset_multitap();
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
        boolean consumed = on_normal_char(c);
        // Live-update the composition hint at the right of the status bar,
        // without touching the mode label or any flashed status message.
        if (_mode == MODE_NORMAL)
          _handler._recv.set_vim_hint(normal_hint());
        return consumed;
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
    char accent = multitap_accent(c);
    if (accent != 0)
    {
      accent_multi_tap(accent);
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
      character was cancelled. Not currently used, as the special character is
      typed directly by the long-press instead of replacing a pending 'j'. */
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
    if (_mode == MODE_NORMAL || _mode == MODE_SCROLL)
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
    if (_mode == MODE_SCROLL)
    {
      set_mode(MODE_INSERT);
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
    if (_mode == MODE_NORMAL || _mode == MODE_SCROLL)
    {
      switch (ev)
      {
        case BACKSPACE: delete_forward(1); return true;
        case SPACE_BAR: return true; // Consume the space bar in normal/scroll mode
        default: return false;
      }
    }
    return false;
  }

  boolean on_normal_char(char c)
  {
    if (c == '"')
    {
      _register_pending = true;
      return true;
    }
    if (_register_pending)
    {
      _register_pending = false;
      boolean uppercase = Character.isUpperCase(c);
      char reg = Character.toLowerCase(c);
      if (is_register_name(reg))
      {
        _cmd_register = reg;
        _register_append = uppercase;
        return true;
      }
      _clear_register();
      // Invalid register name: ignore it and process [c] normally.
    }
    if (c == 'P')
    {
      paste_register(false);
      _clear_count();
      return true;
    }
    if (c == 'p')
    {
      paste_register(true);
      _clear_count();
      return true;
    }
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
    if (_op == 'y')
    {
      switch (c)
      {
        case 'y': _op = 0; yank_line(count()); _clear_count(); return true;
        case 'w': _op = 0; yank_word(count()); _clear_count(); return true;
        case 'e': _op = 0; yank_word_end(count()); _clear_count(); return true;
        case '0': _op = 0; yank_col_start(); _clear_count(); return true;
        case '$': _op = 0; yank_col_end(); _clear_count(); return true;
        default: _op = 0; break;
      }
    }
    switch (c)
    {
      case 'i': _clear_count(); set_mode(MODE_INSERT); return true;
      case 'a': _clear_count(); _move_l(1); set_mode(MODE_INSERT); return true;
      case 'o': open_line_below(); return true;
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
      case 'y': _op = 'y'; return true;
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
    reset_multitap();
    update_status();
  }

  int mode()
  {
    return _mode;
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
  private static final int STATUS_DURATION_MS = 4000;

  void flash_status(String text, int color)
  {
    _handler._recv.set_vim_status(text, color);
    _handler.get_handler().postDelayed(new Runnable() {
      public void run() { update_status(); }
    }, STATUS_DURATION_MS);
  }

  /** Like [flash_status], but keeps the text until the next status update
      (mode change, a new command, etc.). Used for persistent, rolling output. */
  void set_status(String text, int color)
  {
    _handler._recv.set_vim_status(text, color);
  }

  void update_status()
  {
    String text;
    int color;
    switch (_mode)
    {
      case MODE_INSERT: text = "INSERT"; color = STATUS_COLOR_INSERT; break;
      case MODE_NORMAL: text = "NORMAL"; color = STATUS_COLOR_NORMAL; break;
      case MODE_SCROLL: text = "SCROLL"; color = STATUS_COLOR_NORMAL; break;
      case MODE_CMD: text = ":" + _cmd.command(); color = STATUS_COLOR_CMD; break;
      default:
        text = "/" + _search.query();
        if (_search.no_match())
          text += " [no match]";
        color = STATUS_COLOR_SEARCH;
        break;
    }
    _handler._recv.set_vim_status(text, color);
    // In normal mode, while a command is being composed (pending [g],
    // operator or count) show the valid completion keys at the right of the
    // status bar, so the user learns the navigation shortcuts.
    _handler._recv.set_vim_hint(
        (_mode == MODE_NORMAL) ? normal_hint() : "");
  }

  /** Hint shown at the right of the status bar in normal mode while a command
      is being composed (pending [g], operator or count): the valid completion
      keys, so the user learns the shortcuts. Empty otherwise. */
  String normal_hint()
  {
    if (_pending_g)
      return "gg G";
    if (_op == 'd')
      return "dd dw de d0 d$";
    if (_op == 'y')
      return "yy yw ye y0 y$";
    if (_count.length() > 0)
      return "h j k l w b e G 0 $";
    return "";
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
    if (_count.length() == 0)
    {
      // Plain 'G': go to the end of the document.
      set_sel(td.base + td.n);
      return;
    }
    int n = count();
    if (n <= 1)
    {
      // '1G': go to the first line of the document.
      set_sel(td.base);
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
      deleted_to_register(td.text.substring(td.sel_s_rel, td.sel_e_rel));
      delete_range(td, before, after);
      return;
    }
    deleted_to_register(td.text.substring(td.cursor_rel,
        Math.min(td.n, td.cursor_rel + count)));
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
      int line_len = after;
      if (line_end < td.n)
      {
        line_len++; // Include the line ending in the register
        after++;    // Include the line ending in the deletion
      }
      deleted_to_register(td.text.substring(line_start, line_start + line_len));
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
        {
          deleted_to_register(td.text.substring(td.cursor_rel, end));
          delete_range(td, 0, end - td.cursor_rel);
        }
        return;
      }
      deleted_to_register(td.text.substring(td.cursor_rel, end));
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
      deleted_to_register(td.text.substring(td.cursor_rel, end));
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
    deleted_to_register(td.text.substring(line_start, rel));
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
    deleted_to_register(td.text.substring(rel, line_end));
    delete_range(td, 0, line_end - rel);
  }

  // ---- Clipboard registers ---------------------------------------------

  /** Register names: [a-z]. The unnamed register holds the last yanked or
      deleted text. */
  static boolean is_register_name(char c)
  {
    return c >= 'a' && c <= 'z';
  }

  /** Clear the selected register without touching its contents. */
  void _clear_register()
  {
    _cmd_register = 0;
    _register_append = false;
  }

  /** Text to paste with [p]/[P]: the selected register, the unnamed register
      (last yank/delete) or, as a fallback, the system clipboard. */
  String register_text()
  {
    if (_cmd_register != 0)
    {
      String s = _registers.get(_cmd_register);
      _clear_register();
      if (s != null)
        return s;
    }
    if (!_unnamed_register.isEmpty())
      return _unnamed_register;
    String sys = _handler.get_clipboard_text();
    return (sys == null) ? "" : sys;
  }

  /** Store [text] in the selected register and in the unnamed register,
      mirroring vim: yanking to a named register also updates the unnamed
      register. An uppercase register (["A]) appends to the register instead
      of overwriting it. Also updates the system clipboard so the text can be
      pasted anywhere. */
  void yank_to_register(String text)
  {
    if (_cmd_register != 0)
    {
      String old = _registers.get(_cmd_register);
      String value = (_register_append && old != null) ? old + text : text;
      _registers.put(_cmd_register, value);
      _clear_register();
    }
    _unnamed_register = text;
    _handler.set_clipboard_text(text);
  }

  /** Same as [yank_to_register] but for deletions (vim also puts deleted text
      into the register). */
  void deleted_to_register(String text)
  {
    if (_cmd_register != 0)
    {
      String old = _registers.get(_cmd_register);
      String value = (_register_append && old != null) ? old + text : text;
      _registers.put(_cmd_register, value);
      _clear_register();
    }
    _unnamed_register = text;
  }

  /** Paste at the cursor. [after] moves one character to the right first so
      the text is inserted after the current character (vim [p]); otherwise it
      inserts before it (vim [P]). */
  void paste_register(boolean after)
  {
    String text = register_text();
    if (text.isEmpty())
    {
      flash_status("registrador vazio", STATUS_COLOR_CMD);
      return;
    }
    if (after)
      _move_l(1);
    _handler.send_text(text);
  }

  // ---- Yank (copy) ------------------------------------------------------

  void yank_line(int count)
  {
    TextData td = get_text();
    if (td == null)
      return;
    int rel = td.cursor_rel;
    int line_start = rel;
    while (line_start > 0 && td.text.charAt(line_start - 1) != '\n')
      line_start--;
    // The yank goes from the start of the current line up to the end of the
    // [count]-th line, including the line endings (vim: 2yy copies two lines).
    int end = rel;
    int lines = 0;
    while (end < td.n && lines < count)
    {
      if (td.text.charAt(end) == '\n')
        lines++;
      end++;
    }
    yank_to_register(td.text.substring(line_start, end));
  }

  void yank_word(int count)
  {
    TextData td = get_text();
    if (td == null)
      return;
    // Advance past [count] words (word + trailing separators each).
    int i = td.cursor_rel;
    for (int w = 0; w < count; w++)
    {
      while (i < td.n && is_word_char(td.text.charAt(i)))
        i++;
      while (i < td.n && !is_word_char(td.text.charAt(i)))
        i++;
    }
    if (i > td.cursor_rel)
      yank_to_register(td.text.substring(td.cursor_rel, i));
  }

  void yank_word_end(int count)
  {
    TextData td = get_text();
    if (td == null)
      return;
    // Advance past [count] words (word ends, no trailing separators).
    int i = td.cursor_rel;
    for (int w = 0; w < count; w++)
    {
      int end = word_end_forward(td.text, i);
      if (end <= i)
        return;
      i = end;
    }
    yank_to_register(td.text.substring(td.cursor_rel, i));
  }

  void yank_col_start()
  {
    TextData td = get_text();
    if (td == null)
      return;
    int rel = td.cursor_rel;
    int line_start = rel;
    while (line_start > 0 && td.text.charAt(line_start - 1) != '\n')
      line_start--;
    yank_to_register(td.text.substring(line_start, rel));
  }

  void yank_col_end()
  {
    TextData td = get_text();
    if (td == null)
      return;
    int rel = td.cursor_rel;
    int line_end = rel;
    while (line_end < td.n && td.text.charAt(line_end) != '\n')
      line_end++;
    yank_to_register(td.text.substring(rel, line_end));
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