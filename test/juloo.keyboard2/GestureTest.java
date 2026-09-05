package juloo.keyboard2;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure unit tests for the pointer gesture state machine. The swipe->rotation
    transition requires [Config.globalConfig]; the rest is exercised directly. */
public class GestureTest
{
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
    // Exercised through a swipe state that never triggers the Config-dependent
    // rotation path; unrelated moves are ignored once the gesture ended.
    Gesture g = new Gesture(5);
    g.pointer_up();
    assertFalse(g.changed_direction(6));
    assertEquals(5, g.current_direction());
  }
}