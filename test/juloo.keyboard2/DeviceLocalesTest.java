package com.vinisskt.vikey;

import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceLocalesTest
{
  static final String PKG = "pkg.keyboard";

  static Context contextWith(InputMethodManager imm)
  {
    Context ctx = mock(Context.class);
    when(ctx.getSystemService(Context.INPUT_METHOD_SERVICE)).thenReturn(imm);
    when(ctx.getPackageName()).thenReturn(PKG);
    return ctx;
  }

  static InputMethodManager immWith(InputMethodInfo imi)
  {
    InputMethodManager imm = mock(InputMethodManager.class);
    when(imm.getEnabledInputMethodList()).thenReturn(
        imi == null ? Collections.<InputMethodInfo>emptyList()
            : Collections.singletonList(imi));
    when(imm.getEnabledInputMethodSubtypeList(
        any(InputMethodInfo.class), anyBoolean())).thenReturn(
            Collections.<InputMethodSubtype>emptyList());
    return imm;
  }

  static InputMethodInfo imiForPkg()
  {
    InputMethodInfo imi = mock(InputMethodInfo.class);
    when(imi.getPackageName()).thenReturn(PKG);
    return imi;
  }

  static InputMethodSubtype subtype(String lang, String script,
      String extra_keys, String dictionary)
  {
    InputMethodSubtype st = mock(InputMethodSubtype.class);
    when(st.getLanguageTag()).thenReturn(lang);
    when(st.getExtraValueOf("script")).thenReturn(script);
    when(st.getExtraValueOf("default_layout")).thenReturn(null);
    when(st.getExtraValueOf("extra_keys")).thenReturn(extra_keys);
    when(st.getExtraValueOf("dictionary")).thenReturn(dictionary);
    return st;
  }

  @Test public void loadWithNoEnabledLocales()
  {
    InputMethodManager imm = immWith(null);
    when(imm.getCurrentInputMethodSubtype()).thenReturn(null);
    DeviceLocales dl = DeviceLocales.load(contextWith(imm));
    assertTrue(dl.installed.isEmpty());
    assertNull(dl.default_);
  }

  @Test public void loadParsesSubtypeFields()
  {
    InputMethodSubtype st = subtype("ti-IN", "tamil", "1", null);
    InputMethodInfo imi = imiForPkg();
    InputMethodManager imm = immWith(imi);
    when(imm.getEnabledInputMethodSubtypeList(imi, true))
        .thenReturn(Collections.singletonList(st));
    when(imm.getCurrentInputMethodSubtype()).thenReturn(st);
    DeviceLocales dl = DeviceLocales.load(contextWith(imm));
    assertEquals(1, dl.installed.size());
    DeviceLocales.Loc loc = dl.installed.get(0);
    assertEquals("ti-IN", loc.lang_tag);
    assertEquals("tamil", loc.script);
    assertNull(loc.default_layout);
    assertNull(loc.dictionary);
    assertFalse(loc.extra_keys == ExtraKeys.EMPTY);
    // current_locale: SDK_INT is 0 (<24) so it wraps the current subtype.
    assertNotNull(dl.default_);
    assertEquals("ti-IN", dl.default_.lang_tag);
  }

  @Test public void nullExtraKeysMeansEmpty()
  {
    InputMethodSubtype st = subtype("fr", null, null, "dict");
    InputMethodInfo imi = imiForPkg();
    InputMethodManager imm = immWith(imi);
    when(imm.getEnabledInputMethodSubtypeList(imi, true))
        .thenReturn(Collections.singletonList(st));
    when(imm.getCurrentInputMethodSubtype()).thenReturn(null);
    DeviceLocales dl = DeviceLocales.load(contextWith(imm));
    assertEquals(ExtraKeys.EMPTY, dl.installed.get(0).extra_keys);
    assertEquals("dict", dl.installed.get(0).dictionary);
  }

  @Test public void ignoresOtherPackagesAndMergesExtras()
  {
    InputMethodSubtype st1 = subtype("ti-IN", "tamil", "a", null);
    InputMethodSubtype st2 = subtype("hi-IN", "devanagari", "b", null);
    InputMethodInfo foreign = mock(InputMethodInfo.class);
    when(foreign.getPackageName()).thenReturn("other.keyboard");
    InputMethodInfo imi = imiForPkg();
    InputMethodManager imm = mock(InputMethodManager.class);
    when(imm.getEnabledInputMethodList())
        .thenReturn(Arrays.asList(imi, foreign));
    when(imm.getEnabledInputMethodSubtypeList(imi, true))
        .thenReturn(Arrays.asList(st1, st2));
    InputMethodSubtype foreign_subtype = subtype("en", null, "z", null);
    when(imm.getEnabledInputMethodSubtypeList(foreign, true))
        .thenReturn(Collections.singletonList(foreign_subtype));
    when(imm.getCurrentInputMethodSubtype()).thenReturn(null);
    DeviceLocales dl = DeviceLocales.load(contextWith(imm));
    assertEquals(2, dl.installed.size());
    ExtraKeys merged = dl.extra_keys();
    assertEquals(2, merged._ks.size());
  }
}