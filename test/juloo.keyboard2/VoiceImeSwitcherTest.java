package com.vinisskt.vikey;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** [VoiceImeSwitcher] tests: the pure list helpers and the "last used"
    switching path (the chooser popup path needs the Android UI layer). */
public class VoiceImeSwitcherTest
{
  static VoiceImeSwitcher.IME ime(String id, String mode)
  {
    InputMethodInfo im = mock(InputMethodInfo.class);
    when(im.getId()).thenReturn(id);
    InputMethodSubtype st = mock(InputMethodSubtype.class);
    when(st.getMode()).thenReturn(mode);
    return new VoiceImeSwitcher.IME(im, st);
  }

  @Test
  public void get_ime_by_id_finds_a_matching_ime()
  {
    List<VoiceImeSwitcher.IME> imes = new ArrayList<VoiceImeSwitcher.IME>();
    imes.add(ime("com.a", "voice"));
    imes.add(ime("com.b", "voice"));
    assertEquals("com.b", VoiceImeSwitcher.get_ime_by_id(imes, "com.b").get_id());
    assertNull(VoiceImeSwitcher.get_ime_by_id(imes, "nope"));
    assertNull(VoiceImeSwitcher.get_ime_by_id(imes, null));
    assertNull(VoiceImeSwitcher.get_ime_by_id(new ArrayList<VoiceImeSwitcher.IME>(), "x"));
  }

  @Test
  public void serialize_ime_ids_joins_ids()
  {
    List<VoiceImeSwitcher.IME> imes = new ArrayList<VoiceImeSwitcher.IME>();
    imes.add(ime("com.a", "voice"));
    imes.add(ime("com.b", "voice"));
    assertEquals("com.a,com.b,", VoiceImeSwitcher.serialize_ime_ids(imes));
    assertEquals("", VoiceImeSwitcher.serialize_ime_ids(
        new ArrayList<VoiceImeSwitcher.IME>()));
  }

  @Test
  public void get_voice_ime_list_filters_voice_subtypes_only()
  {
    VoiceImeSwitcher.IME voice_ime = ime("com.voice", "voice");
    VoiceImeSwitcher.IME keyboard_ime = ime("com.key", "keyboard");
    InputMethodManager imm = mock(InputMethodManager.class);
    when(imm.getEnabledInputMethodList())
        .thenReturn(Arrays.asList(voice_ime.im, keyboard_ime.im));
    when(imm.getEnabledInputMethodSubtypeList(voice_ime.im, true))
        .thenReturn(Collections.singletonList(voice_ime.subtype));
    when(imm.getEnabledInputMethodSubtypeList(keyboard_ime.im, true))
        .thenReturn(Collections.singletonList(keyboard_ime.subtype));
    List<VoiceImeSwitcher.IME> imes = VoiceImeSwitcher.get_voice_ime_list(imm);
    assertEquals(1, imes.size());
    assertEquals("com.voice", imes.get(0).get_id());
  }

  @Test
  public void get_ime_display_names_uses_the_loaded_label()
  {
    InputMethodInfo im = mock(InputMethodInfo.class);
    when(im.getId()).thenReturn("com.a");
    when(im.loadLabel(any())).thenReturn("Voice");
    VoiceImeSwitcher.IME voice_ime =
        new VoiceImeSwitcher.IME(im, mock(InputMethodSubtype.class));
    List<VoiceImeSwitcher.IME> imes = new ArrayList<VoiceImeSwitcher.IME>();
    imes.add(voice_ime);
    assertEquals(Arrays.asList("Voice"),
        VoiceImeSwitcher.get_ime_display_names(
            mock(InputMethodService.class), imes));
  }

  @Test
  public void chooser_is_required_until_the_state_is_consistent()
  {
    List<VoiceImeSwitcher.IME> imes = new ArrayList<VoiceImeSwitcher.IME>();
    imes.add(ime("com.a", "voice"));
    imes.add(ime("com.b", "voice"));
    // First use: nothing stored yet.
    assertTrue(VoiceImeSwitcher.chooser_is_required(null, "com.a,com.b,", imes));
    assertTrue(VoiceImeSwitcher.chooser_is_required("com.a", null, imes));
    // The known set changed since the last use.
    assertTrue(VoiceImeSwitcher.chooser_is_required("com.a", "com.a,", imes));
    // The last used ime disappeared from the list.
    assertTrue(VoiceImeSwitcher.chooser_is_required("com.gone", "com.a,com.b,", imes));
    // All consistent: switch straight to the last used ime.
    assertFalse(VoiceImeSwitcher.chooser_is_required("com.a", "com.a,com.b,", imes));
  }

  @Test
  public void switch_to_voice_ime_uses_stored_last_used()
  {
    VoiceImeSwitcher.IME voice_ime = ime("com.voice", "voice");
    InputMethodManager imm = mock(InputMethodManager.class);
    when(imm.getEnabledInputMethodList())
        .thenReturn(Collections.singletonList(voice_ime.im));
    when(imm.getEnabledInputMethodSubtypeList(voice_ime.im, true))
        .thenReturn(Collections.singletonList(voice_ime.subtype));
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString("voice_ime_last_used", null)).thenReturn("com.voice");
    when(prefs.getString("voice_ime_known", null)).thenReturn("com.voice,");
    InputMethodService ims = mock(InputMethodService.class);
    assertTrue(VoiceImeSwitcher.switch_to_voice_ime(ims, imm, prefs));
    verify(ims).switchInputMethod("com.voice");
  }

  @Test
  public void switch_to_voice_ime_without_voice_imes_returns_false()
  {
    InputMethodManager imm = mock(InputMethodManager.class);
    when(imm.getEnabledInputMethodList())
        .thenReturn(Collections.<InputMethodInfo>emptyList());
    SharedPreferences prefs = mock(SharedPreferences.class);
    assertFalse(VoiceImeSwitcher.switch_to_voice_ime(
        mock(InputMethodService.class), imm, prefs));
  }
}