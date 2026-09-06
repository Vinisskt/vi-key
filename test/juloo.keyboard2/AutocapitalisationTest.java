package juloo.keyboard2;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-JVM [Autocapitalisation] state machine. [Config] needs an Android
    [Context], so the tests use the package-private [started(boolean, EditorConfig,
    InputConnection)] seam. The shift state is normally reported through a
    delayed [Handler] post; on the JVM that never fires, so tests flush
    [Autocapitalisation.delayed_callback] manually. */
public class AutocapitalisationTest
{
  static final int CAP_MODE_SENTENCES = 0x4000;

  FakeInputConnection _ic;
  RecordingCallback _cb;
  Autocapitalisation _cap;

  static final class RecordingCallback implements Autocapitalisation.Callback
  {
    final List<Boolean> enable_calls = new ArrayList<Boolean>();
    final List<Boolean> disable_calls = new ArrayList<Boolean>();

    public void update_shift_state(boolean enable, boolean disable)
    {
      enable_calls.add(Boolean.valueOf(enable));
      disable_calls.add(Boolean.valueOf(disable));
    }
  }

  @Before
  public void setup()
  {
    _ic = new FakeInputConnection();
    _cb = new RecordingCallback();
    _cap = new Autocapitalisation(new Handler(Looper.getMainLooper()), _cb);
  }

  EditorConfig caps_config(boolean initially_enabled, boolean initially_updated)
  {
    EditorConfig ec = new EditorConfig();
    ec.caps_mode = CAP_MODE_SENTENCES;
    ec.caps_initially_enabled = initially_enabled;
    ec.caps_initially_updated = initially_updated;
    return ec;
  }

  /** Start with autocapitalisation on and shift initially enabled, but don't
      update the caps state right away so that [_should_enable_shift] stays. */
  void start_caps()
  {
    _cap.started(true, caps_config(true, false), _ic);
  }

  boolean last_enable()
  {
    return _cb.enable_calls.get(_cb.enable_calls.size() - 1).booleanValue();
  }

  boolean last_disable()
  {
    return _cb.disable_calls.get(_cb.disable_calls.size() - 1).booleanValue();
  }

  void flush()
  {
    _cap.delayed_callback.run();
  }

  @Test
  public void disabled_config_keeps_autocap_off()
  {
    _cap.started(false, caps_config(true, false), _ic);
    assertFalse(_cap._enabled);
    assertEquals(0, _cb.enable_calls.size());
  }

  @Test
  public void zero_caps_mode_keeps_autocap_off()
  {
    _cap.started(true, new EditorConfig(), _ic);
    assertFalse(_cap._enabled);
    assertEquals(0, _cb.enable_calls.size());
  }

  @Test
  public void started_reports_initial_shift_state()
  {
    start_caps();
    assertTrue(_cap._enabled);
    assertEquals(1, _cb.enable_calls.size());
    assertTrue(last_enable());
    assertTrue(last_disable());
  }

  @Test
  public void typing_a_letter_cancels_shift()
  {
    start_caps();
    _cap.typed("a");
    assertEquals(1, _cap._cursor);
    assertFalse(_cap._should_enable_shift);
    flush();
    assertFalse(last_enable());
    assertFalse(last_disable());
  }

  @Test
  public void typing_a_space_asks_for_a_new_caps_state()
  {
    start_caps();
    _cap.typed("hello ");
    assertTrue(_cap._should_update_caps_mode);
    // The fake editor reports no caps mode, so shift goes off.
    flush();
    assertFalse(last_enable());
  }

  @Test
  public void only_space_is_a_trigger_character()
  {
    assertTrue(_cap.is_trigger_character(' '));
    assertFalse(_cap.is_trigger_character('a'));
    assertFalse(_cap.is_trigger_character('\n'));
    assertFalse(_cap.is_trigger_character(','));
  }

  @Test
  public void del_event_moves_cursor_back_and_rechecks_caps()
  {
    start_caps();
    _cap._cursor = 2;
    _cap.event_sent(KeyEvent.KEYCODE_DEL, 0);
    assertEquals(1, _cap._cursor);
    assertTrue(_cap._should_update_caps_mode);
    assertTrue(_cap._should_disable_shift);
    flush();
    assertFalse(last_enable());
  }

  @Test
  public void del_at_beginning_does_not_go_negative()
  {
    start_caps();
    _cap._cursor = 0;
    _cap.event_sent(KeyEvent.KEYCODE_DEL, 0);
    assertEquals(0, _cap._cursor);
  }

  @Test
  public void enter_event_rechecks_caps()
  {
    start_caps();
    _cap.event_sent(KeyEvent.KEYCODE_ENTER, 0);
    assertTrue(_cap._should_update_caps_mode);
    flush();
    assertFalse(last_enable());
  }

  @Test
  public void event_with_modifiers_does_not_touch_shift()
  {
    start_caps();
    _cap._should_enable_shift = true;
    _cap._should_update_caps_mode = true;
    _cap.event_sent(KeyEvent.KEYCODE_DEL, KeyEvent.META_SHIFT_ON);
    assertFalse(_cap._should_update_caps_mode);
    assertFalse(_cap._should_enable_shift);
    assertEquals(1, _cb.enable_calls.size()); // Only the initial report.
  }

  @Test
  public void selection_change_disables_shift()
  {
    start_caps();
    _cap._cursor = 3;
    _cap.selection_updated(3, 6);
    assertEquals(6, _cap._cursor);
    assertFalse(_cap._should_enable_shift);
    flush();
    assertTrue(last_disable());
  }

  @Test
  public void unchanged_selection_is_ignored()
  {
    start_caps();
    boolean enable_before = _cap._should_enable_shift;
    _cap.selection_updated(0, 0);
    assertTrue(_cap._should_enable_shift == enable_before);
    assertEquals(1, _cb.enable_calls.size());
  }

  @Test
  public void pause_stops_autocap_and_unpause_resumes_it()
  {
    start_caps();
    assertTrue(_cap.pause());
    assertFalse(_cap._enabled);
    assertFalse(_cap._should_enable_shift);
    _cap.unpause(true);
    assertTrue(_cap._enabled);
    assertEquals(3, _cb.enable_calls.size()); // start, pause, unpause
    assertTrue(last_disable());
  }

  @Test
  public void stop_clears_the_pending_shift()
  {
    start_caps();
    _cap._should_enable_shift = true;
    _cap.stop();
    assertFalse(_cap._should_enable_shift);
    assertFalse(_cap._should_update_caps_mode);
    assertEquals(2, _cb.enable_calls.size());
  }
}