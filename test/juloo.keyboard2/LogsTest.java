package juloo.keyboard2;

import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class LogsTest
{
  @Test public void debugFlagGatesDebugOutput()
  {
    Logs.set_debug_logs(false);
    Logs.debug("not printed");
    Logs.trace();
    Logs.set_debug_logs(true);
    Logs.debug("printed");
    Logs.trace();
  }

  @Test public void exnCallsLogger()
  {
    Logs.exn("a message", new Exception("boom"));
  }

  @Test public void debugConfigMigrationLogs()
  {
    Logs.set_debug_logs(false);
    Logs.debug_config_migration(1, 2);
  }

  @Test public void startupInputViewSkipWhenDisabled()
  {
    Logs.set_debug_logs(false);
    Logs.debug_startup_input_view(new EditorInfo(), null);
  }

  @Test public void startupInputViewDumpWhenEnabled()
  {
    Logs.set_debug_logs(true);
    EditorInfo info = mock(EditorInfo.class);
    info.inputType = 1;
    Logs.debug_startup_input_view(info, null);
  }
}