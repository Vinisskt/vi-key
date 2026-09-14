package com.vinisskt.vikey;

import org.junit.Test;
import static org.junit.Assert.*;

/** [BrowserActivity.normalize_url]: adds a scheme when missing and falls back
    to the default URL for empty inputs. */
public class BrowserActivityTest
{
  @Test
  public void normalizes_without_scheme()
  {
    assertEquals("https://example.com", BrowserActivity.normalize_url("example.com"));
    assertEquals("https://example.com/a?b=c", BrowserActivity.normalize_url("example.com/a?b=c"));
    assertEquals("https://localhost:8080", BrowserActivity.normalize_url("localhost:8080"));
  }

  @Test
  public void trims_whitespace()
  {
    assertEquals("https://example.com", BrowserActivity.normalize_url("  example.com  "));
  }

  @Test
  public void keeps_existing_schemes()
  {
    assertEquals("http://x.io", BrowserActivity.normalize_url("http://x.io"));
    assertEquals("https://x.io/a?b=c", BrowserActivity.normalize_url("https://x.io/a?b=c"));
  }

  @Test
  public void empty_input_returns_the_default_url()
  {
    assertEquals("https://www.google.com", BrowserActivity.normalize_url(null));
    assertEquals("https://www.google.com", BrowserActivity.normalize_url(""));
    assertEquals("https://www.google.com", BrowserActivity.normalize_url("   "));
  }
}