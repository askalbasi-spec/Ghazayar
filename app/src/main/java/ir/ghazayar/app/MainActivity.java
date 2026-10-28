package ir.ghazayar.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.content.*;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.graphics.Color;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void shareText(String text) {
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "اشتراک‌گذاری گزارش غذایار"));
                });
            }
            @JavascriptInterface public void copyText(String text) {
                runOnUiThread(() -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Ghazayar", text));
                    Toast.makeText(MainActivity.this, "کپی شد", Toast.LENGTH_SHORT).show();
                });
            }
            @JavascriptInterface public void printReport(String title, String body) {
                runOnUiThread(() -> printReportNative(title, body));
            }
        }, "GhazayarNative");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (uploadMessage != null) uploadMessage.onReceiveValue(null);
                uploadMessage = cb;
                try { startActivityForResult(p.createIntent(), FILE_CHOOSER_REQUEST); }
                catch (Exception e) { uploadMessage = null; Toast.makeText(MainActivity.this, "انتخاب فایل ممکن نشد", Toast.LENGTH_SHORT).show(); return false; }
                return true;
            }
        });
        if (b == null) webView.loadUrl("file:///android_asset/www/index.html"); else webView.restoreState(b);
    }

    private void printReportNative(String title, String body) {
        WebView printView = new WebView(this);
        String html = "<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><style>" +
            "*{box-sizing:border-box}body{font-family:sans-serif;color:#20382e;line-height:2;padding:22px}" +
            ".head{background:#176b55;color:white;padding:22px;border-radius:12px;margin-bottom:20px}" +
            "h1{font-size:24px;margin:0;color:white}h2{font-size:17px;color:#eafff0;margin:4px 0}" +
            "table{width:100%;border-collapse:collapse;margin:14px 0}th,td{border:1px solid #cfdcd3;padding:8px;text-align:right}" +
            "th{background:#eaf4ed;color:#176b55}.weekly-print-table{table-layout:fixed}.weekly-print-table td:first-child{width:28%;font-weight:bold;color:#176b55}" +
            "@page{size:A4;margin:14mm}</style></head><body><div class='head'><h1>غذایار</h1><h2>" + escapeHtml(title) +
            "</h2><small>گزارش قابل چاپ / ذخیره به صورت PDF</small></div>" + body + "<hr><small>پشتیبانی: esmlotfi@gmail.com</small></body></html>";
        printView.setBackgroundColor(Color.WHITE);
        printView.getSettings().setJavaScriptEnabled(false);
        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                PrintManager manager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                if (manager != null) {
                    String jobName = (title == null || title.trim().isEmpty()) ? "Ghazayar-Report" : title;
                    PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                    manager.print(jobName, adapter, new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("ghazayar", "Ghazayar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
                }
            }
        });
        printView.loadDataWithBaseURL("https://ghazayar.local/", html, "text/html", "UTF-8", null);
    }
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == FILE_CHOOSER_REQUEST && uploadMessage != null) {
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d));
            uploadMessage = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle b) { webView.saveState(b); super.onSaveInstanceState(b); }
    @Override public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("(function(){return window.GhazayarHandleBack ? window.GhazayarHandleBack() : false})()", value -> {
                if ("false".equals(value) || value == null) finish();
            });
        } else super.onBackPressed();
    }
}
