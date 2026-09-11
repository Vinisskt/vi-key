package com.vinisskt.vikey;

import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Incremental in-text search used in VIM mode: query typing, live matching,
    next/prev navigation and the abort paths when the editor can't provide its
    full text. */
public class SearchBarTest
{
  FakeReceiver _receiver;
  KeyEventHandler _handler;
  SearchBar _search;
  FakeInputConnection _conn;

  @Before
  public void setup()
  {
    _receiver = new FakeReceiver();
    _handler = new KeyEventHandler(_receiver);
    _search = new SearchBar(_handler._vim);
    _conn = _receiver.conn;
  }

  void buffer(String text, int sel)
  {
    _conn.set_text(text, sel, sel);
  }

  @Test
  public void begin_activates_and_clears_state()
  {
    _search.reset();
    _search.begin();
    assertTrue(_search.active());
    assertEquals("", _search.query());
    assertFalse(_search.no_match());
  }

  @Test
  public void typing_outside_search_is_ignored()
  {
    assertFalse(_search.active());
    _search.type('x');
    assertFalse(_search.active());
    _search.backspace();
  }

  @Test
  public void typing_is_appended_and_matches()
  {
    buffer("hello world", 0);
    _search.begin();
    _search.type('h');
    assertEquals("h", _search.query());
    assertTrue(_search.active());
    assertFalse(_search.no_match());
    // The match [0,1) is selected and made visible in the editor.
    assertEquals(0, _conn.selStart());
    assertEquals(1, _conn.selEnd());
  }

  @Test
  public void no_match_query_is_reported()
  {
    buffer("hello world", 0);
    _search.begin();
    _search.type('x');
    assertTrue(_search.no_match());
    assertEquals(0, _search.find_now());
  }

  @Test
  public void backspace_erases_last_query_char()
  {
    buffer("hello", 0);
    _search.begin();
    _search.type('h');
    _search.type('e');
    assertEquals("he", _search.query());
    _search.backspace();
    assertEquals("h", _search.query());
  }

  @Test
  public void backspace_with_empty_query_does_nothing()
  {
    buffer("hello", 0);
    _search.begin();
    _search.backspace();
    assertEquals("", _search.query());
  }

  @Test
  public void backspace_outside_search_is_ignored()
  {
    _search.backspace();
    assertEquals("", _search.query());
  }

  @Test
  public void commit_without_matches_cancels()
  {
    buffer("hello", 0);
    _search.begin();
    _search.type('z');
    _search.commit();
    assertFalse(_search.active());
    assertEquals(VimEngine.MODE_NORMAL, _handler._vim._mode);
  }

  @Test
  public void commit_with_match_leaves_search()
  {
    buffer("hello", 0);
    _search.begin();
    _search.type('h');
    _search.commit();
    assertFalse(_search.active());
    assertEquals(VimEngine.MODE_NORMAL, _handler._vim._mode);
  }

  @Test
  public void commit_when_not_active_is_ignored()
  {
    _search.commit();
    assertEquals(VimEngine.MODE_INSERT, _handler._vim._mode);
  }

  @Test
  public void next_and_prev_cycle_through_matches()
  {
    buffer("ab ab ab", 0);
    _search.begin();
    _search.type('a');
    assertEquals(3, _search.find_now());
    _search.next();
    _search.next();
    assertTrue(_search._match_i == 2);
    _search.next();
    // Wraps around.
    assertTrue(_search._match_i == 0);
    _search.prev();
    assertTrue(_search._match_i == 2);
  }

  @Test
  public void find_now_with_origin_moves_first_match()
  {
    buffer("ab ab ab", 0);
    _search.begin();
    // Start the search at the third match ("ab ab [ab]").
    _search._origin = 4;
    _search.type('a');
    assertTrue(_search._match_i == 2);
  }

  @Test
  public void find_now_with_empty_query_returns_zero()
  {
    buffer("hello", 0);
    _search.begin();
    assertEquals(0, _search.find_now());
    assertFalse(_search.no_match());
  }

  @Test
  public void find_now_without_connection_returns_zero()
  {
    _handler = new KeyEventHandler(new NullConnReceiver());
    _search = new SearchBar(_handler._vim);
    _search.begin();
    _search.type('x');
    assertFalse(_search.no_match());
  }

  @Test
  public void find_now_when_editor_has_no_text_is_skipped()
  {
    _handler = new KeyEventHandler(new NullExtractReceiver());
    _search = new SearchBar(_handler._vim);
    _search.begin();
    _search.type('h');
    assertFalse(_search.no_match());
  }

  @Test
  public void select_current_without_matches_is_ignored()
  {
    buffer("hello", 0);
    _search.begin();
    _search.type('h');
    _search._matches = null;
    _search.select_current();
    // No selection change.
    assertEquals(0, _conn.selStart());
    assertEquals(1, _conn.selEnd());
  }

  @Test
  public void select_current_without_connection_is_ignored()
  {
    _handler = new KeyEventHandler(new NullConnReceiver());
    _search = new SearchBar(_handler._vim);
    _search._matches = new java.util.ArrayList<Integer>();
    _search._matches.add(0);
    _search._match_i = 0;
    _search.select_current();
  }

  @Test
  public void cancel_clears_state_and_enters_normal_mode()
  {
    buffer("hello", 0);
    _search.begin();
    _search.type('h');
    _search.cancel();
    assertFalse(_search.active());
    assertEquals("", _search.query());
    assertEquals(VimEngine.MODE_NORMAL, _handler._vim._mode);
  }

  @Test
  public void match_ending_at_text_end_is_handled()
  {
    // The second match ends exactly at the end of the text: the loop must
    // stop to avoid scanning past the end.
    buffer("aba", 0);
    _search.begin();
    _search.type('a');
    assertEquals(2, _search.find_now());
  }

  /** A receiver whose input connection always returns [null], so the search
      aborts instead of crashing. */
  static final class NullConnReceiver implements KeyEventHandler.IReceiver
  {
    @Override public InputConnection getCurrentInputConnection() { return null; }
    @Override public void handle_event_key(KeyValue.Event ev) {}
    @Override public void set_shift_state(boolean s, boolean l) {}
    @Override public void set_compose_pending(boolean p) {}
    @Override public void selection_state_changed(boolean s) {}
    @Override public android.os.Handler getHandler() { return new android.os.Handler(android.os.Looper.getMainLooper()); }
    @Override public android.content.Context getApplicationContext() { return null; }
    @Override public void set_vim_status(String text, int color) {}
    @Override public boolean is_float_open() { return false; }
    @Override public void toggle_float_panel(String url) {}
    @Override public void close_float_panel() {}
    @Override public void open_help() {}
    @Override public void open_page(String title, String html) {}
  }

  /** A receiver whose connection reports no extracted text. */
  static final class NullExtractReceiver implements KeyEventHandler.IReceiver
  {
    final FakeInputConnection base = new FakeInputConnection();
    final InputConnection conn = (InputConnection)Proxy.newProxyInstance(
        InputConnection.class.getClassLoader(),
        new Class[] { InputConnection.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getExtractedText"))
            return null;
          return method.invoke(base, args);
        });

    @Override public InputConnection getCurrentInputConnection() { return conn; }
    @Override public void handle_event_key(KeyValue.Event ev) {}
    @Override public void set_shift_state(boolean s, boolean l) {}
    @Override public void set_compose_pending(boolean p) {}
    @Override public void selection_state_changed(boolean s) {}
    @Override public android.os.Handler getHandler() { return new android.os.Handler(android.os.Looper.getMainLooper()); }
    @Override public android.content.Context getApplicationContext() { return null; }
    @Override public void set_vim_status(String text, int color) {}
    @Override public boolean is_float_open() { return false; }
    @Override public void toggle_float_panel(String url) {}
    @Override public void close_float_panel() {}
    @Override public void open_help() {}
    @Override public void open_page(String title, String html) {}
  }
}