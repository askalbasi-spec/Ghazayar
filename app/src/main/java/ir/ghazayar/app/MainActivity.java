package ir.ghazayar.app;
import android.app.Activity; import android.os.Bundle; import android.webkit.*; import android.content.*; import android.net.Uri; import android.view.View; import android.widget.Toast;
public class MainActivity extends Activity {
 private WebView webView; private ValueCallback<Uri[]> uploadMessage; private static final int FILE_CHOOSER_REQUEST=1001;
 @Override public void onCreate(Bundle b){ super.onCreate(b); webView=new WebView(this); setContentView(webView); WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); webView.setWebViewClient(new WebViewClient()); webView.setWebChromeClient(new WebChromeClient(){ @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){ if(uploadMessage!=null)uploadMessage.onReceiveValue(null); uploadMessage=cb; try{startActivityForResult(p.createIntent(),FILE_CHOOSER_REQUEST);}catch(Exception e){uploadMessage=null;Toast.makeText(MainActivity.this,"انتخاب فایل ممکن نشد",Toast.LENGTH_SHORT).show();return false;}return true; }}); if(b==null)webView.loadUrl("file:///android_asset/www/index.html"); else webView.restoreState(b); }
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==FILE_CHOOSER_REQUEST&&uploadMessage!=null){uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c,d));uploadMessage=null;}}
 @Override protected void onSaveInstanceState(Bundle b){webView.saveState(b);super.onSaveInstanceState(b);}
 @Override public void onBackPressed(){ if(webView!=null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
