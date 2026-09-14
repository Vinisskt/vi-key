package com.vinisskt.vikey;

import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;
import org.junit.After;
import org.junit.Test;
import static org.mockito.Mockito.*;

/** [VibratorCompat.vibrate] behavior: system haptics vs the custom
    [Vibrator] path. */
public class VibratorCompatTest
{
  @After
  public void reset_vibrator_service()
  {
    VibratorCompat.vibrator_service = null;
  }

  @Test
  public void default_config_uses_system_haptics()
  {
    View v = mock(View.class);
    Config config = mock(Config.class);
    VibratorCompat.vibrate(v, config);
    verify(v).performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
  }

  @Test
  public void custom_config_vibrates_with_duration()
  {
    Vibrator vibrator = mock(Vibrator.class);
    VibratorCompat.vibrator_service = vibrator;
    Config config = mock(Config.class);
    config.vibrate_custom = true;
    config.vibrate_duration = 20L;
    VibratorCompat.vibrate(mock(View.class), config);
    verify(vibrator).vibrate(20L);
    verify(mock(View.class), never()).performHapticFeedback(anyInt(), anyInt());
  }

  @Test
  public void custom_config_with_zero_duration_does_not_vibrate()
  {
    Vibrator vibrator = mock(Vibrator.class);
    VibratorCompat.vibrator_service = vibrator;
    Config config = mock(Config.class);
    config.vibrate_custom = true;
    config.vibrate_duration = 0L;
    VibratorCompat.vibrate(mock(View.class), config);
    verify(vibrator, never()).vibrate(anyLong());
  }
}