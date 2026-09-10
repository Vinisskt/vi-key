package com.vinisskt.vikey;

import android.content.res.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Emoji caches everything in static fields, so the tests have to run in a
    deterministic order: the failure case first, then the parsing case. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EmojiTest
{
  static Resources resourceWith(String data)
  {
    Resources res = mock(Resources.class);
    when(res.openRawResource(anyInt()))
        .thenReturn(new ByteArrayInputStream(data.getBytes()));
    return res;
  }

  /** An input stream that fails as soon as the reader reads from it. */
  static Resources failingResource()
  {
    Resources res = mock(Resources.class);
    when(res.openRawResource(anyInt())).thenReturn(new java.io.InputStream() {
      public int read() throws IOException { throw new IOException("boom"); }
    });
    return res;
  }

  @Test public void a_initFailureIsLogged()
  {
    Emoji.init(failingResource()); // Must not throw (the error goes to the logs).
    assertEquals(0, Emoji.getNumGroups());
  }

  @Test public void b_initParsesEmojisAndGroupIndices()
  {
    Emoji.init(resourceWith("aa\nbb\ncc\n\n3 1 3\n"));
    assertEquals(3, Emoji.getNumGroups());
    assertEquals(1, Emoji.getEmojisByGroup(0).size());
    assertEquals(2, Emoji.getEmojisByGroup(1).size());
    assertEquals(0, Emoji.getEmojisByGroup(2).size());
    Emoji e = Emoji.getEmojiByString("bb");
    assertNotNull(e);
    assertEquals("bb", e.kv().getString());
  }

  @Test public void c_initIsIdempotent()
  {
    Emoji.init(resourceWith("zz\n\n1 1\n"));
    assertEquals(3, Emoji.getNumGroups()); // Same cache as b_.
  }
}