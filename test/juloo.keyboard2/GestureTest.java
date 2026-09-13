package com.vinisskt.vikey;

import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure unit tests for the pointer gesture state machine. The swipe->rotation
    transition reads [Config.globalConfig]; a fake config is injected through
    reflection so the whole state machine can be exercised. */
public class GestureTest
{
  Config _saved_config;

  @Before
  public void setUp() throws Exception
  {
    Field config_field = Config.class.getDeclaredField("_globalConfig");
    config_field.setAccessible(true);
    _saved_config = (Config)config_field.get(null);
    setGlobalConfig(makeFakeConfig(2));
  }

  @After
  public void tearDown() throws Exception
  {
    setGlobalConfig(_saved_config);
  }

  /** A real [Config] without running its constructor (which needs Android
      resources); only [circle_sensitivity] is touched by gestures. The JVM
      compiles tests against the Android bootclasspath, so `sun.misc.Unsafe`
      is resolved by reflection instead of at compile time. */
  static Config makeFakeConfig(int circleSensitivity) throws Exception
  {
    Class<?> unsafe_class = Class.forName("sun.misc.Unsafe");
    Field unsafe_field = unsafe_class.getDeclaredField("theUnsafe");
    unsafe_field.setAccessible(true);
    Object unsafe = unsafe_field.get(null);
    Config c = (Config)unsafe_class
        .getMethod("allocateInstance", Class.class).invoke(unsafe, Config.class);
    c.circle_sensitivity = circleSensitivity;
    return c;
  }

  static void setGlobalConfig(Config c) throws Exception
  {
    Field config_field = Config.class.getDeclaredField("_globalConfig");
    config_field.setAccessible(true);
    config_field.set(null, c);
  }

  @Test
  public void new_gesture_starts_as_swipe_in_progress()
  {
    Gesture g = new Gesture(4);
    assertEquals(4, g.current_direction());
    assertEquals(Gesture.Name.Swipe, g.get_gesture());
    assertTrue(g.is_in_progress());
  }

  @Test
  public void dir_diff_wraps_modulo_16()
  {
    assertEquals(0, Gesture.dir_diff(4, 4));
    assertEquals(1, Gesture.dir_diff(4, 5));
    assertEquals(-1, Gesture.dir_diff(4, 3));
    assertEquals(2, Gesture.dir_diff(0, 2));
    assertEquals(-2, Gesture.dir_diff(2, 0));
    assertEquals(1, Gesture.dir_diff(15, 0));
    assertEquals(-1, Gesture.dir_diff(0, 15));
    assertEquals(8, Gesture.dir_diff(0, 8));
  }

  @Test
  public void dir_diff_half_turn_goes_clockwise()
  {
    assertEquals(8, Gesture.dir_diff(8, 0));
    assertEquals(-6, Gesture.dir_diff(0, 10));
  }

  @Test
  public void swipe_to_center_ends_as_roundtrip()
  {
    Gesture g = new Gesture(4);
    assertTrue(g.moved_to_center());
    assertEquals(Gesture.Name.Roundtrip, g.get_gesture());
    assertFalse(g.is_in_progress());
  }

  @Test
  public void swipe_pointer_up_ends_swipe()
  {
    Gesture g = new Gesture(9);
    g.pointer_up();
    assertEquals(Gesture.Name.Swipe, g.get_gesture());
    assertFalse(g.is_in_progress());
  }

  @Test
  public void direction_changes_below_rotation_threshold_are_ignored()
  {
    Gesture g = new Gesture(5);
    assertFalse(g.changed_direction(5));
    assertFalse(g.changed_direction(6));
    assertEquals(5, g.current_direction());
    assertEquals(Gesture.Name.Swipe, g.get_gesture());
    assertTrue(g.is_in_progress());
  }

  @Test
  public void threshold_reached_starts_rotation()
  {
    Gesture g = new Gesture(4);
    assertTrue(g.changed_direction(6)); // d = +2 -> clockwise
    assertEquals(6, g.current_direction());
    assertEquals(Gesture.Name.Circle, g.get_gesture());
    assertTrue(g.is_in_progress());
  }

  @Test
  public void anticlockwise_rotation()
  {
    Gesture g = new Gesture(4);
    assertTrue(g.changed_direction(2)); // d = -2 -> anticlockwise
    assertEquals(Gesture.Name.Anticircle, g.get_gesture());
    assertTrue(g.is_in_progress());
  }

  @Test
  public void reversing_rotation_cancels()
  {
    Gesture g = new Gesture(4);
    g.changed_direction(6); // Rotating_clockwise
    assertTrue(g.changed_direction(4)); // reversal
    assertEquals(Gesture.Name.None, g.get_gesture());
    assertFalse(g.is_in_progress());
    // The cancelled gesture ignores further changes.
    assertFalse(g.changed_direction(6));
  }

  @Test
  public void moving_to_center_while_rotating_ends_rotation()
  {
    Gesture g = new Gesture(4);
    g.changed_direction(6);
    assertFalse(g.moved_to_center());
    assertEquals(Gesture.Name.Circle, g.get_gesture());
    assertFalse(g.is_in_progress());
    Gesture a = new Gesture(4);
    a.changed_direction(2);
    assertFalse(a.moved_to_center());
    assertEquals(Gesture.Name.Anticircle, a.get_gesture());
  }

  @Test
  public void pointer_up_while_rotating_ends_rotation()
  {
    Gesture g = new Gesture(4);
    g.changed_direction(6);
    g.pointer_up();
    assertEquals(Gesture.Name.Circle, g.get_gesture());
    assertFalse(g.is_in_progress());
    Gesture a = new Gesture(4);
    a.changed_direction(2);
    a.pointer_up();
    assertEquals(Gesture.Name.Anticircle, a.get_gesture());
  }

  @Test
  public void cancelled_gesture_is_inactive()
  {
    Gesture g = new Gesture(0);
    g.changed_direction(13); // d = -3 -> Rotating_anticlockwise
    g.changed_direction(0); // reversal -> Cancelled
    assertEquals(Gesture.Name.None, g.get_gesture());
    assertFalse(g.is_in_progress());
    assertFalse(g.moved_to_center());
    g.pointer_up(); // no-op
    assertEquals(Gesture.Name.None, g.get_gesture());
  }
}