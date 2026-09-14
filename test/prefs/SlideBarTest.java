package com.vinisskt.vikey.prefs;

import org.junit.Test;
import static org.junit.Assert.*;

/** The pure slider conversions shared by [SlideBarPreference] (floats) and
    [IntSlideBarPreference] (ints): none of them can crash or invert when the
    attributes are missing or corrupt. */
public class SlideBarTest
{
  @Test
  public void float_of_string_falls_back_to_zero()
  {
    assertEquals(0.f, SlideBarPreference.float_of_string(null), 0.f);
    assertEquals(0.f, SlideBarPreference.float_of_string(""), 0.f);
    assertEquals(0.f, SlideBarPreference.float_of_string("abc"), 0.f);
    assertEquals(12.5f, SlideBarPreference.float_of_string("12.5"), 0.f);
  }

  @Test
  public void value_at_progress_scales_between_min_and_max()
  {
    assertEquals(5.f, SlideBarPreference.value_at_progress(50, 0.f, 10.f), 0.f);
    assertEquals(2.f, SlideBarPreference.value_at_progress(0, 2.f, 6.f), 0.f);
    assertEquals(6.f, SlideBarPreference.value_at_progress(100, 2.f, 6.f), 0.f);
    assertEquals(4.f, SlideBarPreference.value_at_progress(50, 2.f, 6.f), 0.f);
  }

  @Test
  public void progress_of_value_is_clamped_to_the_step_range()
  {
    assertEquals(50, SlideBarPreference.progress_of_value(5.f, 0.f, 10.f));
    assertEquals(0, SlideBarPreference.progress_of_value(0.f, 0.f, 10.f));
    assertEquals(100, SlideBarPreference.progress_of_value(10.f, 0.f, 10.f));
    // Out-of-range persisted values stay inside [0, STEPS] instead of sending
    // the SeekBar an invalid progress.
    assertEquals(100, SlideBarPreference.progress_of_value(999.f, 0.f, 10.f));
    assertEquals(0, SlideBarPreference.progress_of_value(-999.f, 0.f, 10.f));
  }

  @Test
  public void int_sliders_convert_values_and_progresses()
  {
    assertEquals(7, IntSlideBarPreference.value_at_progress(5, 2));
    assertEquals(5, IntSlideBarPreference.progress_of_value(7, 2));
    assertEquals(0, IntSlideBarPreference.progress_of_value(2, 2));
  }
}