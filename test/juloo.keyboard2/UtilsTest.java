package com.vinisskt.vikey;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsTest
{
  @Test
  public void capitalize_string()
  {
    assertEquals("", Utils.capitalize_string(""));
    assertEquals("Abc", Utils.capitalize_string("abc"));
    assertEquals("ABC", Utils.capitalize_string("ABC"));
    assertEquals("ABc", Utils.capitalize_string("aBc"));
    assertEquals("A", Utils.capitalize_string("a"));
    // Code points are not cut in half.
    assertEquals("\uD801\uDC00bc", Utils.capitalize_string("\uD801\uDC00bc"));
    assertEquals("\uD801\uDC00bC", Utils.capitalize_string("\uD801\uDC00bC"));
  }

  @Test
  public void read_all_utf8() throws Exception
  {
    InputStream in = new ByteArrayInputStream(
        "h\u00E9llo \uD83D\uDE00".getBytes(StandardCharsets.UTF_8));
    assertEquals("h\u00E9llo \uD83D\uDE00", Utils.read_all_utf8(in));
    assertEquals("", Utils.read_all_utf8(new ByteArrayInputStream(new byte[0])));
  }

  @Test
  public void read_all_bytes() throws Exception
  {
    byte[] data = new byte[] { 0, 1, 2, -1, -128, 127 };
    assertArrayEquals(data, Utils.read_all_bytes(new ByteArrayInputStream(data)));
    assertArrayEquals(new byte[0],
        Utils.read_all_bytes(new ByteArrayInputStream(new byte[0])));
  }
}