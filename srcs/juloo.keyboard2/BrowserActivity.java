package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

/** Full-screen web browser shown above the keyboard (the IME window stays
    visible below). Opened with ":browser <url>", closed with [esc] or the close
    button. The window is a normal activity, so text input goes to it through
    the standard Android IME flow instead of to the app below. */
public final class BrowserActivity extends Activity
{
  private static final String DEFAULT_URL = "https://www.google.com";

  private static BrowserActivity _instance;

  private WebView _webview;
  private EditText _url_input;

  public static boolean is_open()
  {
    return _instance != null;
  }

  public static void open(Context ctx, String url)
  {
    BrowserActivity a = _instance;
    if (a != null)
    {
      a.load(url);
      return;
    }
    Intent i = new Intent(ctx, BrowserActivity.class);
    i.putExtra("url", url);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    ctx.startActivity(i);
  }

  public static void close()
  {
    final BrowserActivity a = _instance;
    if (a != null)
      a.runOnUiThread(new Runnable() {
        @Override public void run() { a.finish(); }
      });
  }

  public static void open_help(Context ctx)
  {
    Intent i = new Intent(ctx, BrowserActivity.class);
    i.putExtra("mode", "help");
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    ctx.startActivity(i);
  }

  static String normalize_url(String url)
  {
    if (url == null || url.isEmpty())
      return DEFAULT_URL;
    url = url.trim();
    if (!url.contains("://"))
      url = "https://" + url;
    return url;
  }

  @Override
  protected void onCreate(Bundle saved)
  {
    super.onCreate(saved);
    _instance = this;
    setContentView(R.layout.browser_activity);
    _webview = findViewById(R.id.browser_webview);
    _url_input = findViewById(R.id.browser_url);
    configure_webview();
    wire_controls();
    if ("help".equals(getIntent().getStringExtra("mode")))
    {
      _url_input.setText("ajuda");
      _webview.loadDataWithBaseURL(null, KeyEventHandler.vim_help_html(),
          "text/html", "utf-8", null);
    }
    else
    {
      load(getIntent().getStringExtra("url"));
    }
    // Focus the URL field and make sure the keyboard stays visible.
    _url_input.post(new Runnable() {
      @Override public void run()
      {
        _url_input.requestFocus();
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
          imm.showSoftInput(_url_input, 0);
      }
    });
  }

  void load(String url)
  {
    _webview.loadUrl(normalize_url(url));
  }

  @SuppressLint("SetJavaScriptEnabled")
  void configure_webview()
  {
    _webview.getSettings().setJavaScriptEnabled(true);
    _webview.getSettings().setDomStorageEnabled(true);
    _webview.getSettings().setBuiltInZoomControls(true);
    _webview.getSettings().setDisplayZoomControls(false);
    _webview.getSettings().setLoadWithOverviewMode(true);
    _webview.getSettings().setUseWideViewPort(true);
    _webview.setBackgroundColor(0xFF1D2021);
    _webview.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String current_url)
      {
        _url_input.setText(current_url);
      }
    });
  }

  void wire_controls()
  {
    _url_input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override public boolean onEditorAction(TextView v, int action_id, KeyEvent ev)
      {
        if (action_id == EditorInfo.IME_ACTION_GO || action_id == EditorInfo.IME_ACTION_DONE)
        {
          load(_url_input.getText().toString());
          return true;
        }
        return false;
      }
    });
    _url_input.setOnKeyListener(new View.OnKeyListener() {
      @Override public boolean onKey(View v, int key_code, KeyEvent ev)
      {
        if (key_code == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_UP)
        {
          load(_url_input.getText().toString());
          return true;
        }
        return false;
      }
    });
    findViewById(R.id.browser_btn_back).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { _webview.goBack(); }
    });
    findViewById(R.id.browser_btn_fwd).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { _webview.goForward(); }
    });
    findViewById(R.id.browser_btn_reload).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { _webview.reload(); }
    });
    findViewById(R.id.browser_btn_close).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { finish(); }
    });
  }

  @Override
  public void onBackPressed()
  {
    if (_webview.canGoBack())
      _webview.goBack();
    else
      finish();
  }

  @Override
  protected void onDestroy()
  {
    if (_instance == this)
      _instance = null;
    super.onDestroy();
  }
}