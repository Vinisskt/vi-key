package juloo.keyboard2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;

public class SettingsActivity extends PreferenceActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);

    setup_lua_scripts_preference();

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);
  }

  void setup_lua_scripts_preference()
  {
    final Preference pref = findPreference("lua_scripts");
    if (pref == null)
      return;
    pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
    {
      @Override public boolean onPreferenceClick(Preference p)
      {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            || Environment.isExternalStorageManager())
          open_lua_folder();
        else
          grant_file_access();
        return true;
      }
    });
  }

  /** Launch the system screen granting All files access, or directly open
      the scripts folder when it is already granted. */
  @Override protected void onResume()
  {
    super.onResume();
    update_lua_scripts_summary();
  }

  void update_lua_scripts_summary()
  {
    Preference pref = findPreference("lua_scripts");
    if (pref == null)
      return;
    LuaEngine.ensure_user_lua_dir();
    String state = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && Environment.isExternalStorageManager())
        ? getString(R.string.pref_lua_scripts_granted)
        : getString(R.string.pref_lua_scripts_grant);
    pref.setSummary(state + "\n" +
        getString(R.string.pref_lua_scripts_folder));
  }

  void open_lua_folder()
  {
    LuaEngine.ensure_user_lua_dir();
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setDataAndType(Uri.fromFile(LuaEngine.user_lua_dir()), "resource/folder");
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try
    {
      startActivity(i);
    }
    catch (Exception _e)
    {
      update_lua_scripts_summary();
    }
  }

  void grant_file_access()
  {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
      return;
    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:" + getPackageName()));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (i.resolveActivity(getPackageManager()) == null)
      i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    try
    {
      startActivity(i);
    }
    catch (Exception _e)
    {
      update_lua_scripts_summary();
    }
  }

  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }
}
