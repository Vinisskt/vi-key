package juloo.keyboard2;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-JVM [CurrentlyTypedWord]: word tracking as text is typed, edited and
    the selection moves. [Config] needs an Android [Context], so the tests use
    the package-private [started(EditorConfig, InputConnection)] seam. */
public class CurrentlyTypedWordTest
{
  FakeInputConnection _ic;
  RecordingCallback _cb;
  CurrentlyTypedWord _word;

  static final class RecordingCallback implements CurrentlyTypedWord.Callback
  {
    final List<String> words = new ArrayList<String>();

    public void currently_typed_word(String word)
    {
      words.add(word);
    }
  }

  @Before
  public void setup()
  {
    _ic = new FakeInputConnection();
    _cb = new RecordingCallback();
    _word = new CurrentlyTypedWord(new Handler(Looper.getMainLooper()), _cb);
  }

  /** Start with the cursor at [sel] (no selection, no surrounding text). */
  void start_word()
  {
    EditorConfig ec = new EditorConfig();
    ec.initial_sel_start = 0;
    ec.initial_sel_end = 0;
    _word.started(ec, _ic);
  }

  EditorConfig midword_config()
  {
    EditorConfig ec = new EditorConfig();
    ec.initial_text_before_cursor = "he";
    ec.initial_text_after_cursor = "ll";
    ec.initial_sel_start = 2;
    ec.initial_sel_end = 2;
    return ec;
  }

  @Test
  public void empty_editor_has_no_word()
  {
    start_word();
    assertEquals("", _word.get());
    assertEquals(0, _word.cursor_relative());
    assertFalse(_word.is_selection_not_empty());
    assertEquals(0, _cb.words.size());
  }

  @Test
  public void initial_text_builds_the_word()
  {
    start_word();
    EditorConfig ec = new EditorConfig();
    ec.initial_text_before_cursor = "world";
    ec.initial_sel_start = 5;
    ec.initial_sel_end = 5;
    _word.started(ec, _ic);
    assertEquals("world", _word.get());
    assertEquals(0, _word.cursor_relative());
  }

  @Test
  public void cursor_in_the_middle_of_the_word()
  {
    _word.started(midword_config(), _ic);
    assertEquals("hell", _word.get());
    assertEquals(-2, _word.cursor_relative());
  }

  @Test
  public void typed_letter_extends_the_word()
  {
    start_word();
    _word.typed("h");
    assertEquals("h", _word.get());
    _word.typed("ello");
    assertEquals("hello", _word.get());
    assertEquals(0, _word.cursor_relative());
  }

  @Test
  public void typing_in_the_middle_of_the_word()
  {
    _word.started(midword_config(), _ic);
    _word.typed("X");
    assertEquals("heXll", _word.get());
    assertEquals(-2, _word.cursor_relative());
  }

  @Test
  public void non_word_character_starts_a_new_word()
  {
    start_word();
    _word.typed("abc");
    assertEquals("abc", _word.get());
    _word.typed(" def");
    assertEquals("def", _word.get());
    assertEquals(7, _word._cursor);
  }

  @Test
  public void is_word_char_heuristics()
  {
    assertTrue(CurrentlyTypedWord.is_word_char('a'));
    assertTrue(CurrentlyTypedWord.is_word_char('Z'));
    assertTrue(CurrentlyTypedWord.is_word_char('5'));
    assertTrue(CurrentlyTypedWord.is_word_char('\''));
    assertFalse(CurrentlyTypedWord.is_word_char(' '));
    assertFalse(CurrentlyTypedWord.is_word_char('-'));
    assertFalse(CurrentlyTypedWord.is_word_char('.'));
    assertFalse(CurrentlyTypedWord.is_word_char('\n'));
  }

  @Test
  public void selection_disables_the_word()
  {
    EditorConfig ec = new EditorConfig();
    ec.initial_sel_start = 1;
    ec.initial_sel_end = 3;
    _word.started(ec, _ic);
    assertTrue(_word.is_selection_not_empty());
    assertEquals("", _word.get());
  }

  @Test
  public void typing_clears_the_selection()
  {
    EditorConfig ec = new EditorConfig();
    ec.initial_sel_start = 1;
    ec.initial_sel_end = 3;
    _word.started(ec, _ic);
    _word.typed("x");
    assertFalse(_word.is_selection_not_empty());
    assertEquals("x", _word.get());
  }

  @Test
  public void backspace_removes_the_last_char()
  {
    start_word();
    _word.typed("hello");
    _word.remove_surrounding_text(1, 0);
    assertEquals("hell", _word.get());
    assertEquals(4, _word._cursor);
  }

  @Test
  public void del_key_event_is_a_backspace()
  {
    start_word();
    _word.typed("hello");
    _word.event_sent(KeyEvent.KEYCODE_DEL, 0);
    assertEquals("hell", _word.get());
  }

  @Test
  public void other_key_event_schedules_a_refresh()
  {
    start_word();
    _word.typed("abc");
    _word.event_sent(KeyEvent.KEYCODE_A, 0);
    assertTrue(_word._refresh_pending);
    _word.delayed_refresh_run.run();
    assertFalse(_word._refresh_pending);
  }

  @Test
  public void refresh_recomputes_the_word_from_the_editor()
  {
    start_word();
    _word.typed("abc");
    _ic.set_text("hello world", 11, 11);
    _word.event_sent(KeyEvent.KEYCODE_A, 0);
    _word.delayed_refresh_run.run();
    assertEquals("world", _word.get());
  }

  @Test
  public void selection_update_without_selection_adjusts_cursor()
  {
    start_word();
    _word.typed("hello");
    _word.selection_updated(5, 3, 3);
    assertEquals(3, _word._cursor);
    assertEquals(-2, _word.cursor_relative());
  }

  @Test
  public void cursor_move_past_the_word_refreshes()
  {
    start_word();
    _word.typed("hello");
    _ic.set_text("hi there", 8, 8);
    _word.selection_updated(5, 7, 7);
    assertEquals("there", _word.get());
  }

  @Test
  public void selection_update_turns_the_word_off()
  {
    start_word();
    _word.typed("abc");
    _ic.set_text("zz", 1, 2);
    _word.selection_updated(3, 1, 2);
    assertTrue(_word.is_selection_not_empty());
    assertEquals("", _word.get());
  }
}