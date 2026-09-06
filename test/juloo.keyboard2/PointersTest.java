package juloo.keyboard2;

import android.os.Message;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pointers unit tests: pointer tracking, latched/locked modifiers, swipes
    into side keys, sliders and long-press handling. The pointer handler and
    the config are fakes; Android [Handler]s are stubs under the JVM so the
    long-press timeout is fed directly through [handleMessage]. */
public class PointersTest
{
  static final int NUM_KEYS = 9;

  DummyHandler _events;
  Config _config;
  Config _saved_config;
  Pointers _ptrs;

  @Before
  public void setUp() throws Exception
  {
    Field f = Config.class.getDeclaredField("_globalConfig");
    f.setAccessible(true);
    _saved_config = (Config)f.get(null);
    _config = newConfig();
    setGlobalConfig(_config);
    _events = new DummyHandler();
    _ptrs = new Pointers(_events, _config);
  }

  @After
  public void tearDown() throws Exception
  {
    setGlobalConfig(_saved_config);
  }

  static Config newConfig() throws Exception
  {
    Class<?> unsafe_class = Class.forName("sun.misc.Unsafe");
    Field unsafe_field = unsafe_class.getDeclaredField("theUnsafe");
    unsafe_field.setAccessible(true);
    Object unsafe = unsafe_field.get(null);
    Config c = (Config)unsafe_class
        .getMethod("allocateInstance", Class.class).invoke(unsafe, Config.class);
    c.swipe_dist_px = 8.f;
    c.slide_step_px = 5.f;
    c.longPressTimeout = 0;
    c.longPressInterval = 0;
    c.keyrepeat_enabled = true;
    c.double_tap_lock_shift = true;
    return c;
  }

  static void setGlobalConfig(Config c) throws Exception
  {
    Field f = Config.class.getDeclaredField("_globalConfig");
    f.setAccessible(true);
    f.set(null, c);
  }

  /**
   * Build a [KeyboardData.Key] with the nine direction slots filled:
   *
   *       1 7 2
   *       5 0 6
   *       3 8 4
   *
   * [DIRECTION_TO_INDEX]: 0→7, 1→2, 2→2, 3→6, 4→6, 5→4, 6→4,
   *                       7→8, 8→8, 9→3, 10→3, 11→5, 12→5, 13→1, 14→1, 15→7
   *
   * So idx 1 = top-left, 2 = top-right, 3 = bottom-left, 4 = bottom-right,
   * 5 = left, 6 = right, 7 = top, 8 = bottom. */
  static KeyboardData.Key key(KeyValue center, KeyValue up, KeyValue right,
      KeyValue down, KeyValue left, KeyValue anticircle)
  {
    KeyValue[] ks = new KeyValue[NUM_KEYS];
    ks[0] = center;
    ks[1] = left;   // top-left
    ks[2] = right;  // top-right
    ks[3] = left;   // bottom-left
    ks[4] = right;  // bottom-right
    ks[5] = left;   // left
    ks[6] = right;  // right
    ks[7] = up;     // top
    ks[8] = down;   // bottom
    return new KeyboardData.Key(ks, anticircle, 0, 1.f, 0.f, "k",
        KeyboardData.Key.Role.Normal);
  }

  static KeyValue kv(String name)
  {
    return KeyValue.getKeyByName(name);
  }

  static Pointers.Modifiers mods(KeyValue... m)
  {
    return Pointers.Modifiers.ofArray(m, m.length);
  }

  /** A normal (non-latchable) key with center=a, right=b, all others=a. */
  KeyboardData.Key plainKey()
  {
    return key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
  }

  static final KeyValue SLIDER_H = new KeyValue(KeyValue.Slider.Cursor_right,
      KeyValue.Kind.Slider, 1, KeyValue.FLAG_SPECIAL);

  static final KeyValue SLIDER_V = new KeyValue(KeyValue.Slider.Cursor_down,
      KeyValue.Kind.Slider, 1, KeyValue.FLAG_SPECIAL);

  static KeyboardData.Key sliderKey()
  {
    return key(SLIDER_H, SLIDER_H, SLIDER_H, SLIDER_H, SLIDER_H, kv("z"));
  }

  static final class DummyHandler implements Pointers.IPointerEventHandler
  {
    final List<String> downs = new ArrayList<String>();
    final List<String> ups = new ArrayList<String>();
    final List<String> holds = new ArrayList<String>();
    final List<Boolean> flagsChanged = new ArrayList<Boolean>();
    KeyValue nullValue = null;

    @Override public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
    { return k == nullValue ? null : k; }
    @Override public void onPointerDown(KeyValue k, boolean isSwipe)
    { downs.add(k == null ? "null" : k.toString() + (isSwipe ? "/swipe" : "")); }
    @Override public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
    { ups.add(k == null ? "null" : k.toString()); }
    @Override public void onPointerFlagsChanged(boolean shouldVibrate)
    { flagsChanged.add(shouldVibrate); }
    @Override public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
    { holds.add(k.toString()); }
  }

  int timeoutWhatOf(int i)
  {
    try
    {
      Field ptrs_field = Pointers.class.getDeclaredField("_ptrs");
      ptrs_field.setAccessible(true);
      List<Object> ptrs = (List<Object>)ptrs_field.get(_ptrs);
      Object pointer = ptrs.get(i);
      Field tw_field = pointer.getClass().getDeclaredField("timeoutWhat");
      tw_field.setAccessible(true);
      return tw_field.getInt(pointer);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  int modsSizeOf(int i)
  {
    try
    {
      Field ptrs_field = Pointers.class.getDeclaredField("_ptrs");
      ptrs_field.setAccessible(true);
      List<Object> ptrs = (List<Object>)ptrs_field.get(_ptrs);
      Object pointer = ptrs.get(i);
      Field m_field = pointer.getClass().getDeclaredField("modifiers");
      m_field.setAccessible(true);
      return ((Pointers.Modifiers)m_field.get(pointer)).size();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  // ---- Basic pointer tracking ------------------------------------------

  @Test
  public void no_pointers_no_modifiers()
  {
    assertEquals(0, _ptrs.getModifiers().size());
  }

  @Test
  public void touch_down_and_up_on_a_char_key()
  {
    KeyboardData.Key k = plainKey();
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    assertEquals(1, _events.downs.size());
    assertEquals("Char:a", _events.downs.get(0));
    _ptrs.onTouchUp(1);
    assertEquals(1, _events.ups.size());
    assertEquals("Char:a", _events.ups.get(0));
    assertEquals(0, _ptrs.getModifiers().size());
  }

  @Test
  public void touch_up_with_unknown_id_is_ignored()
  {
    _ptrs.onTouchUp(99);
    assertTrue(_events.ups.isEmpty());
  }

  @Test
  public void touch_down_while_sliding_is_ignored()
  {
    _ptrs.onTouchDown(0, 0, 1, sliderKey());
    _ptrs.onTouchMove(40.f, 0.f, 1);
    assertTrue(_ptrs.isSliding());
    int downs = _events.downs.size();
    // A second touch_down is suppressed while sliding.
    _ptrs.onTouchDown(50.f, 50.f, 2, plainKey());
    assertEquals(downs, _events.downs.size());
  }

  @Test
  public void is_key_down_checks_current_pointers()
  {
    KeyboardData.Key k = plainKey();
    assertFalse(_ptrs.isKeyDown(k));
    _ptrs.onTouchDown(0, 0, 1, k);
    assertTrue(_ptrs.isKeyDown(k));
  }

  @Test
  public void get_key_flags_returns_minus_one_for_released_keys()
  {
    assertEquals(-1, _ptrs.getKeyFlags(kv("a")));
  }

  @Test
  public void get_key_flags_returns_flags_for_pressed_keys()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    assertEquals(0, _ptrs.getKeyFlags(kv("a")));
  }

  @Test
  public void clear_stops_all_pointers()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    _ptrs.clear();
    assertEquals(0, _ptrs.getModifiers().size());
    assertFalse(_ptrs.isSliding());
  }

  @Test
  public void touch_cancel_clears_and_notifies()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    _ptrs.onTouchCancel();
    assertTrue(_events.flagsChanged.get(0));
  }

  // ---- Latched & fake pointers ------------------------------------------

  @Test
  public void latchable_key_latches_on_up()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    _ptrs.onTouchUp(1);
    // The key is latched: no onPointerUp, and it appears in getModifiers.
    assertTrue(_events.ups.isEmpty());
    assertTrue(_ptrs.getModifiers().size() >= 1);
    assertFalse(_events.flagsChanged.isEmpty());
  }

  @Test
  public void add_fake_pointer_latches_and_locks()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.add_fake_pointer(shift, kv("shift"), true);
    assertEquals(1, _ptrs.getModifiers().size());
    assertTrue((_ptrs.getKeyFlags(kv("shift")) & Pointers.FLAG_P_LOCKED) != 0);
  }

  @Test
  public void set_fake_pointer_state_latches()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.set_fake_pointer_state(shift, kv("shift"), true, false);
    assertEquals(1, _ptrs.getModifiers().size());
    _ptrs.set_fake_pointer_state(shift, kv("shift"), false, false);
    assertEquals(0, _ptrs.getModifiers().size());
  }

  @Test
  public void set_fake_pointer_state_does_nothing_for_non_fake_latched()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    _ptrs.onTouchUp(1);
    int flagsBefore = _ptrs.getKeyFlags(kv("shift"));
    _ptrs.set_fake_pointer_state(shift, kv("shift"), false, true);
    assertEquals(flagsBefore, _ptrs.getKeyFlags(kv("shift")));
  }

  @Test
  public void touch_up_on_double_tap_lock_key_locks_it()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    _ptrs.onTouchUp(1); // First tap: latch.
    int latched = _ptrs.getKeyFlags(kv("shift"));
    _ptrs.onTouchDown(0, 0, 2, shift);
    _ptrs.onTouchUp(2); // Second tap: lock.
    int now = _ptrs.getKeyFlags(kv("shift"));
    assertTrue((latched & Pointers.FLAG_P_LOCKED) == 0);
    assertTrue((now & Pointers.FLAG_P_LOCKED) != 0);
  }

  @Test
  public void touch_up_on_non_latchable_key_clears_latched()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    _ptrs.onTouchUp(1); // latch
    assertTrue(_ptrs.getModifiers().size() >= 1);
    // Press a non-latchable key: clears the latched shift.
    _ptrs.onTouchDown(5, 5, 2, plainKey());
    _ptrs.onTouchUp(2);
    // Shift is cleared because a non-latchable (plainKey) pressed.
  }

  @Test
  public void clear_latched_removes_latched_keys()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    _ptrs.onTouchUp(1); // latch
    assertTrue(_ptrs.getModifiers().size() >= 1);
    _ptrs.clear();
    assertEquals(0, _ptrs.getModifiers().size());
  }

  // ---- Swipe & gestures -------------------------------------------------

  @Test
  public void get_key_at_direction_uses_16_sections()
  {
    KeyValue up = kv("u");
    KeyValue right = kv("r");
    KeyValue down = kv("d");
    KeyValue left = kv("l");
    KeyboardData.Key k = key(up, up, right, down, left, kv("z"));
    assertEquals(up, Pointers.getKeyAtDirection(k, 0));    // top (idx 7)
    assertEquals(right, Pointers.getKeyAtDirection(k, 4)); // right (idx 6)
    assertEquals(down, Pointers.getKeyAtDirection(k, 8));  // bottom (idx 8)
    assertEquals(left, Pointers.getKeyAtDirection(k, 12)); // left (idx 5)
  }

  @Test
  public void swipe_into_side_key_selects_it()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _events.downs.clear();
    // Move far right: swipes into the "b" key (idx 6 = right).
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1);
    assertEquals(1, _events.downs.size());
    assertEquals("Char:b/swipe", _events.downs.get(0));
    _ptrs.onTouchUp(1);
    assertEquals("Char:b", _events.ups.get(0));
  }

  @Test
  public void move_within_swipe_distance_does_nothing()
  {
    _ptrs.onTouchDown(10.f, 10.f, 1, plainKey());
    _events.downs.clear();
    _ptrs.onTouchMove(12.f, 10.f, 1);
    assertTrue(_events.downs.isEmpty());
  }

  @Test
  public void move_upward_swipes_into_top_key()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 40.f, 1, k);
    _events.downs.clear();
    // Move far up: swipes into "a" top (idx 7).
    _ptrs.onTouchMove(10.f, 0.f, 1);
    assertEquals(1, _events.downs.size());
    assertEquals("Char:a/swipe", _events.downs.get(0));
  }

  @Test
  public void sliding_key_starts_sliding_mode()
  {
    _ptrs.onTouchDown(10.f, 10.f, 1, sliderKey());
    _ptrs.onTouchMove(10.f + 20.f, 10.f, 1);
    assertTrue(_ptrs.isSliding());
  }

  @Test
  public void sliding_pointer_repeats_keys()
  {
    _ptrs.onTouchDown(10.f, 10.f, 1, sliderKey());
    _config.slide_step_px = 1.f;
    _ptrs.onTouchMove(10.f + 40.f, 10.f, 1);
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1);
    assertFalse(_events.holds.isEmpty());
  }

  @Test
  public void sliding_pointer_up_cancels_it()
  {
    _ptrs.onTouchDown(10.f, 10.f, 1, sliderKey());
    _ptrs.onTouchMove(10.f + 30.f, 10.f, 1);
    assertTrue(_ptrs.isSliding());
    _ptrs.onTouchUp(1);
    assertFalse(_ptrs.isSliding());
  }

  @Test
  public void gesture_circle_applies_extra_modifier()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    // Swipe right (direction 4), then rotate clockwise (direction 6).
    _ptrs.onTouchMove(10.f + 30.f, 10.f, 1);
    _ptrs.onTouchMove(10.f + 30.f, 10.f + 30.f, 1);
    _ptrs.onTouchUp(1);
    assertFalse(_events.ups.isEmpty());
  }

  @Test
  public void gesture_ends_by_returning_to_center()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(10.f + 30.f, 10.f, 1); // start a gesture on "b"
    assertFalse(_events.downs.isEmpty());
    int downs = _events.downs.size();
    _ptrs.onTouchMove(10.f, 10.f, 1); // back to center -> gesture roundtrip
    // No new pointer down is reported for the roundtrip.
    assertEquals(downs, _events.downs.size());
  }

  @Test
  public void touch_up_with_active_gesture_calls_pointer_up()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("b"), kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(10.f + 30.f, 10.f, 1);
    // Pointer up with gesture in progress.
    _ptrs.onTouchUp(1);
    assertFalse(_events.ups.isEmpty());
  }

  // ---- Sliding - vertical slider with ctrl modifier ---------------------

  @Test
  public void sliding_vertical_slider()
  {
    KeyValue sliderVert = new KeyValue(KeyValue.Slider.Cursor_up,
        KeyValue.Kind.Slider, 1, KeyValue.FLAG_SPECIAL);
    KeyboardData.Key k = key(sliderVert, sliderVert, sliderVert, sliderVert,
        sliderVert, kv("z"));
    _ptrs.onTouchDown(10.f, 40.f, 1, k);
    _ptrs.onTouchMove(10.f, 0.f, 1);
    assertTrue(_ptrs.isSliding());
  }

  // ---- Long press via handleMessage -------------------------------------

  @Test
  public void handle_message_with_unknown_what_is_ignored()
  {
    Message m = new Message();
    m.what = 0xF00D;
    assertFalse(_ptrs.handleMessage(m));
  }

  @Test
  public void long_press_locks_a_latchable_key()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    Message m = new Message();
    m.what = timeoutWhatOf(0);
    assertTrue(_ptrs.handleMessage(m));
    assertTrue((_ptrs.getKeyFlags(kv("shift")) & Pointers.FLAG_P_LOCKED) != 0);
  }

  @Test
  public void long_press_repeats_a_char_key()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    Message m = new Message();
    m.what = timeoutWhatOf(0);
    assertTrue(_ptrs.handleMessage(m));
    assertFalse(_events.holds.isEmpty());
  }

  @Test
  public void long_press_on_locked_key_is_ignored()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, shift);
    // Lock the key via handleMessage.
    Message lock = new Message();
    lock.what = timeoutWhatOf(0);
    assertTrue(_ptrs.handleMessage(lock));
    assertTrue((_ptrs.getKeyFlags(kv("shift")) & Pointers.FLAG_P_LOCKED) != 0);
    // Long press again: no action (already locked, can't double-lock).
    assertTrue(_ptrs.handleMessage(lock));
  }

  // ---- Pointer flags ----------------------------------------------------

  @Test
  public void pointer_flags_of_shift_key()
  {
    KeyValue shift = kv("shift");
    int flags = _ptrs.pointer_flags_of_kv(shift);
    assertTrue((flags & Pointers.FLAG_P_LATCHABLE) != 0);
    assertTrue((flags & Pointers.FLAG_P_DOUBLE_TAP_LOCK) != 0);
  }

  @Test
  public void pointer_flags_of_non_special_latchable_key()
  {
    KeyValue capslock = new KeyValue("caps", KeyValue.Kind.Modifier,
        0, KeyValue.FLAG_LATCH);
    int flags = _ptrs.pointer_flags_of_kv(capslock);
    assertTrue((flags & Pointers.FLAG_P_CLEAR_LATCHED) != 0);
    assertTrue((flags & Pointers.FLAG_P_CANT_LOCK) != 0);
    assertTrue((flags & Pointers.FLAG_P_LATCHABLE) != 0);
  }

  @Test
  public void pointer_flags_of_plain_key_are_zero()
  {
    int flags = _ptrs.pointer_flags_of_kv(kv("a"));
    assertEquals(0, flags);
  }

  @Test
  public void is_key_down_true_for_pressed_key()
  {
    KeyboardData.Key k = plainKey();
    _ptrs.onTouchDown(0, 0, 1, k);
    assertTrue(_ptrs.isKeyDown(k));
  }

  @Test
  public void get_key_flags_matches_value()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    assertTrue((_ptrs.getKeyFlags(kv("a")) & Pointers.FLAG_P_LATCHABLE) == 0);
  }

  @Test
  public void set_fake_pointer_state_replace_locked_pointer()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.set_fake_pointer_state(shift, kv("shift"), true, true); // lock
    int before = _ptrs.getModifiers().size();
    _ptrs.set_fake_pointer_state(shift, kv("shift"), true, true); // replace
    assertEquals(before, _ptrs.getModifiers().size());
  }

  @Test
  public void set_fake_pointer_state_locked_ignored_when_no_lock()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.set_fake_pointer_state(shift, kv("shift"), true, true); // lock
    int before = _ptrs.getKeyFlags(kv("shift"));
    // Locking a second time while it's a locked fake pointer does nothing.
    _ptrs.set_fake_pointer_state(shift, kv("shift"), true, false);
    assertEquals(before, _ptrs.getKeyFlags(kv("shift")));
  }

  @Test
  public void touch_up_on_latched_key_unlatches_it()
  {
    // Use a latchable key without DOUBLE_TAP_LOCK so the unlatch path fires.
    KeyValue caps = new KeyValue("caps", KeyValue.Kind.Modifier,
        0, KeyValue.FLAG_LATCH);
    KeyboardData.Key k = key(caps, caps, caps, caps, caps, kv("z"));
    _ptrs.onTouchDown(0, 0, 1, k);
    _ptrs.onTouchUp(1); // latch
    assertTrue(_ptrs.getModifiers().size() >= 1);
    _ptrs.onTouchDown(0, 0, 2, k);
    _events.ups.clear();
    _ptrs.onTouchUp(2); // second tap: should unlatch
    assertFalse(_events.ups.isEmpty());
  }

  @Test
  public void on_touch_move_with_unknown_pointer_is_ignored()
  {
    _ptrs.onTouchMove(0, 0, 88);
    assertTrue(_events.downs.isEmpty());
  }

  @Test
  public void slider_far_away_from_swipe_direction_is_not_selected()
  {
    // Slider ONLY on top (idx 7); right (idx 6) is a normal char key.
    KeyValue sliderUp = new KeyValue(KeyValue.Slider.Cursor_up,
        KeyValue.Kind.Slider, 1, KeyValue.FLAG_SPECIAL);
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("a"), kv("a"), kv("a"), kv("z"));
    // Put a slider only at idx 7 (top): ks[7] = sliderUp.
    KeyValue[] ks = k.keys;
    ks[7] = sliderUp;
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _events.downs.clear();
    _ptrs.onTouchMove(40.f, 10.f, 1); // right: top slider is far (abs(i)>=2)
    assertFalse(_ptrs.isSliding());
  }

  @Test
  public void sliding_first_move_below_threshold_does_nothing()
  {
    KeyValue slider = new KeyValue(KeyValue.Slider.Cursor_right,
        KeyValue.Kind.Slider, 1, KeyValue.FLAG_SPECIAL);
    KeyboardData.Key k = key(slider, slider, slider, slider, slider, kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(40.f, 10.f, 1); // enter sliding
    _config.slide_step_px = 20.f;
    int holds = _events.holds.size();
    _ptrs.onTouchMove(43.f, 10.f, 1); // below threshold
    assertEquals(holds, _events.holds.size());
  }

  @Test
  public void modifiers_hash_eq_and_reflexivity()
  {
    Pointers.Modifiers m1 = mods(kv("shift"), kv("ctrl"));
    Pointers.Modifiers m2 = mods(kv("ctrl"), kv("shift"));
    assertEquals(m1.hashCode(), m2.hashCode());
    assertTrue(m1.equals(m2));
    assertTrue(m1.equals(m1));
    assertFalse(m1.equals(mods(kv("shift"))));
  }

  @Test
  public void modifiers_has_finds_modifier_keys()
  {
    assertTrue(mods(kv("ctrl"), kv("a")).has(KeyValue.Modifier.CTRL));
    assertFalse(mods(kv("a")).has(KeyValue.Modifier.ALT));
  }

  @Test
  public void modifiers_diff_returns_extra_keys()
  {
    Pointers.Modifiers m1 = mods(kv("shift"), kv("ctrl"));
    Pointers.Modifiers m2 = mods(kv("ctrl"));
    java.util.Iterator<KeyValue> it = m1.diff(m2);
    assertTrue(it.hasNext());
    assertTrue(it.next().equals(kv("shift")));
    assertFalse(it.hasNext());
  }

  @Test
  public void modifiers_with_extra_mod_appends()
  {
    Pointers.Modifiers base = mods(kv("shift"));
    Pointers.Modifiers extended =
        base.with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL));
    assertEquals(2, extended.size());
    assertTrue(extended.has(KeyValue.Modifier.CTRL));
  }

  @Test
  public void modifiers_get_returns_keys_in_reverse_sorted_order()
  {
    Pointers.Modifiers m = mods(kv("ctrl"), kv("shift"));
    assertEquals(2, m.size());
    assertTrue(m.has(KeyValue.Modifier.CTRL));
    assertTrue(m.has(KeyValue.Modifier.SHIFT));
  }

  @Test
  public void long_press_special_key_is_ignored()
  {
    KeyValue copy = KeyValue.getKeyByName("copy");
    KeyboardData.Key k = key(copy, copy, copy, copy, copy, kv("z"));
    _ptrs.onTouchDown(0, 0, 1, k);
    _events.holds.clear();
    Message m = new Message();
    m.what = timeoutWhatOf(0);
    _ptrs.handleMessage(m);
    assertTrue(_events.holds.isEmpty());
  }

  @Test
  public void latched_modifier_becomes_inactive_when_other_key_pressed()
  {
    KeyboardData.Key shift = key(kv("shift"), kv("shift"), kv("shift"),
        kv("shift"), kv("shift"), kv("shift"));
    _ptrs.onTouchDown(0, 0, 1, plainKey());  // pointer 0: regular key
    _ptrs.onTouchDown(0, 0, 2, shift);       // pointer 1: shift
    _ptrs.onTouchUp(2);                      // latch shift
    _ptrs.onTouchDown(0, 0, 3, plainKey());  // pointer 2: other regular key
    // The latched shift is skipped because an other key is pressed: only the
    // first pressed "a" key remains in the modifiers.
    assertEquals(1, modsSizeOf(2));
  }

  @Test
  public void swipe_with_all_side_keys_removed_does_nothing()
  {
    KeyValue gone = new KeyValue("gone", KeyValue.Kind.Char, 1, 0);
    KeyboardData.Key k = new KeyboardData.Key(new KeyValue[]{
        kv("a"), gone, gone, gone, gone, gone, gone, gone, gone
      }, kv("z"), 0, 1.f, 0.f, "k", KeyboardData.Key.Role.Normal);
    _events.nullValue = gone;
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    assertEquals(1, _events.downs.size());
    _events.downs.clear();
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1); // every direction is removed
    assertTrue(_events.downs.isEmpty());
    assertFalse(_ptrs.isSliding());
  }

  @Test
  public void slider_far_from_initial_swipe_direction_is_skipped()
  {
    KeyValue gone = new KeyValue("gone", KeyValue.Kind.Char, 1, 0);
    KeyboardData.Key k = new KeyboardData.Key(new KeyValue[]{
        kv("a"), gone, SLIDER_H, gone, gone, gone, gone, gone, SLIDER_H
      }, kv("z"), 0, 1.f, 0.f, "k", KeyboardData.Key.Role.Normal);
    _events.nullValue = gone;
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _events.downs.clear();
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1); // swipe right
    // The far sliders are skipped: no sliding is started.
    assertFalse(_ptrs.isSliding());
    assertTrue(_events.downs.isEmpty());
  }

  @Test
  public void sliding_left_into_left_slider()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("a"), kv("a"), SLIDER_H, kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _events.downs.clear();
    _ptrs.onTouchMove(-10.f, 10.f, 1); // dx < 0 -> left slider
    assertTrue(_ptrs.isSliding());
  }

  @Test
  public void sliding_into_vertical_slider_repeats_on_y_moves()
  {
    KeyboardData.Key k = key(kv("a"), kv("a"), kv("a"), SLIDER_V, kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _config.slide_step_px = 1.f;
    _ptrs.onTouchMove(10.f, 10.f + 40.f, 1); // down: vertical slider
    assertTrue(_ptrs.isSliding());
    _events.holds.clear();
    _ptrs.onTouchMove(10.f, 10.f + 100.f, 1); // further down: repeat
    assertFalse(_events.holds.isEmpty());
  }

  @Test
  public void sliding_with_ctrl_held_still_slides()
  {
    KeyboardData.Key ctrl = key(kv("ctrl"), kv("ctrl"), kv("ctrl"),
        kv("ctrl"), kv("ctrl"), kv("ctrl"));
    _ptrs.onTouchDown(0, 0, 1, ctrl); // ctrl held
    KeyboardData.Key k = key(kv("a"), kv("a"), SLIDER_H, kv("a"), kv("a"), kv("z"));
    _ptrs.onTouchDown(10.f, 10.f, 2, k);
    _config.slide_step_px = 1.f;
    _ptrs.onTouchMove(10.f + 60.f, 10.f, 2); // swipe right: sliding starts
    assertTrue(_ptrs.isSliding());
    _events.holds.clear();
    _ptrs.onTouchMove(10.f + 120.f, 10.f, 2); // further: repeat with ctrl
    assertFalse(_events.holds.isEmpty());
  }

  @Test
  public void gesture_rotating_clockwise_changes_value()
  {
    KeyboardData.Key k = plainKey();
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1); // swipe right (Swipe)
    _events.flagsChanged.clear();
    _ptrs.onTouchMove(10.f + 50.f, 10.f + 50.f, 1); // rotate clockwise
    assertFalse(_events.flagsChanged.isEmpty());
    assertTrue(_events.flagsChanged.get(_events.flagsChanged.size() - 1));
  }

  @Test
  public void gesture_cancelled_by_reversing_rotation()
  {
    KeyboardData.Key k = plainKey();
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1); // swipe right (state Swiped)
    _ptrs.onTouchMove(10.f + 50.f, 10.f + 50.f, 1); // rotate clockwise
    _events.flagsChanged.clear();
    _ptrs.onTouchMove(10.f + 100.f, 10.f + 50.f, 1); // reverse: cancelled
    assertFalse(_events.flagsChanged.isEmpty());
    assertTrue(_events.flagsChanged.get(_events.flagsChanged.size() - 1));
  }

  @Test
  public void gesture_anticlockwise_uses_anticircle_key()
  {
    KeyboardData.Key k = plainKey(); // anticircle = "z"
    _ptrs.onTouchDown(10.f, 10.f, 1, k);
    _ptrs.onTouchMove(10.f + 50.f, 10.f, 1); // swipe right (state Swiped)
    _ptrs.onTouchMove(10.f + 90.f, 10.f - 20.f, 1); // rotate anticlockwise
    _ptrs.onTouchUp(1);
    assertTrue(_events.ups.get(0).equals("Char:z"));
  }

  @Test
  public void handle_message_with_unknown_timeout_returns_false()
  {
    _ptrs.onTouchDown(0, 0, 1, plainKey());
    Message m = new Message();
    m.what = 100000; // no pointer has this timeout
    assertFalse(_ptrs.handleMessage(m));
  }

  @Test
  public void long_press_event_key_switches_value()
  {
    KeyboardData.Key k = key(KeyValue.CHANGE_METHOD_PREV,
        KeyValue.CHANGE_METHOD_PREV, KeyValue.CHANGE_METHOD_PREV,
        KeyValue.CHANGE_METHOD_PREV, KeyValue.CHANGE_METHOD_PREV,
        KeyValue.CHANGE_METHOD_PREV);
    _ptrs.onTouchDown(0, 0, 1, k);
    Message m = new Message();
    m.what = timeoutWhatOf(0);
    assertTrue(_ptrs.handleMessage(m)); // long press
    assertFalse(_events.downs.isEmpty());
    _ptrs.onTouchUp(1);
    assertFalse(_events.ups.isEmpty());
  }

  @Test
  public void long_press_key_removed_by_handler_is_ignored()
  {
    KeyValue gone = new KeyValue("gone", KeyValue.Kind.Char, 1, 0);
    KeyboardData.Key k = new KeyboardData.Key(new KeyValue[]{
        gone, gone, gone, gone, gone, gone, gone, gone, gone
      }, kv("z"), 0, 1.f, 0.f, "k", KeyboardData.Key.Role.Normal);
    _events.nullValue = gone;
    _ptrs.onTouchDown(0, 0, 1, k); // value = null
    _events.holds.clear();
    Message m = new Message();
    m.what = timeoutWhatOf(0);
    _ptrs.handleMessage(m);
    assertTrue(_events.holds.isEmpty());
    _ptrs.onTouchUp(1); // getLatched(null value)
    assertFalse(_events.ups.isEmpty());
  }

  @Test
  public void modifiers_diff_equal_modifiers_yields_nothing()
  {
    Pointers.Modifiers m = mods(kv("shift"));
    java.util.Iterator<KeyValue> it = m.diff(m);
    assertFalse(it.hasNext());
  }

  @Test
  public void modifiers_next_beyond_end_throws()
  {
    Pointers.Modifiers m = mods(kv("shift"));
    java.util.Iterator<KeyValue> it = m.diff(mods());
    assertTrue(it.hasNext());
    it.next();
    try
    {
      it.next();
      fail("Expected NoSuchElementException");
    }
    catch (java.util.NoSuchElementException _e) {}
  }
}