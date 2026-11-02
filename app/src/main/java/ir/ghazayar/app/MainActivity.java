package ir.ghazayar.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private WebView activePrintWebView;
    private boolean printPreviewLaunched = false;
    private long printPreviewReturnAt = 0L;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int BACKUP_SAVE_REQUEST = 1002;
    private static final int BACKUP_OPEN_REQUEST = 1003;
    private String pendingBackupContent = null;

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
            @JavascriptInterface public void saveBackup(String filename, String content) {
                runOnUiThread(() -> {
                    pendingBackupContent = content;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    try { startActivityForResult(intent, BACKUP_SAVE_REQUEST); }
                    catch (Exception e) { pendingBackupContent = null; webView.evaluateJavascript("window.GhazayarBackupResult && window.GhazayarBackupResult(false)", null); }
                });
            }
            @JavascriptInterface public void openBackup() {
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(intent, BACKUP_OPEN_REQUEST); }
                    catch (Exception e) {
                        Toast.makeText(MainActivity.this, "انتخاب فایل پشتیبان ممکن نشد", Toast.LENGTH_SHORT).show();
                    }
                });
            }
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
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String detail = message.message() + " ("
                    + message.sourceId() + ":" + message.lineNumber() + ") [" + message.messageLevel() + "]";
                android.util.Log.e("GhazayarWebView", detail);
                webView.evaluateJavascript("window.GhazayarConsoleDiagnostic && window.GhazayarConsoleDiagnostic("
                    + org.json.JSONObject.quote(detail) + ")", null);
                return true;
            }
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
        cleanupPrintWebView();
        printPreviewLaunched = false;
        WebView printView = new WebView(this);
        activePrintWebView = printView;
        String html = "<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><style>" +
            "*{box-sizing:border-box}body{font-family:sans-serif;color:#20382e;line-height:2;padding:22px}" +
            ".head{background:#176b55;color:white;padding:22px;border-radius:12px;margin-bottom:20px}" +
            "h1{font-size:24px;margin:0;color:white}h2{font-size:17px;color:#176b55;margin:12px 0 4px}" +
            "table{width:100%;border-collapse:collapse;margin:14px 0}th,td{border:1px solid #cfdcd3;padding:8px;text-align:right}" +
            "th{background:#eaf4ed;color:#176b55}.weekly-print-table{table-layout:fixed}.weekly-print-table td:first-child{width:28%;font-weight:bold;color:#176b55}" +
            "@page{size:A4;margin:14mm}</style></head><body><div class='head'><h1>غذایار</h1><h2>" + escapeHtml(title) +
            "</h2><small>گزارش قابل چاپ / ذخیره به صورت PDF</small></div>" + body + "<hr><small>پشتیبانی: esmlotfi@gmail.com</small></body></html>";
        printView.setBackgroundColor(Color.WHITE);
        printView.getSettings().setJavaScriptEnabled(false);
        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (printPreviewLaunched || activePrintWebView != view) return;
                PrintManager manager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                if (manager == null) {
                    Toast.makeText(MainActivity.this, "سرویس چاپ در دسترس نیست", Toast.LENGTH_LONG).show();
                    cleanupPrintWebView();
                    return;
                }
                printPreviewLaunched = true;
                String jobName = (title == null || title.trim().isEmpty()) ? "Ghazayar-Report" : title;
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                try {
                    manager.print(jobName, adapter, new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new PrintAttributes.Resolution("ghazayar", "Ghazayar", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
                } catch (Exception e) {
                    android.util.Log.e("GhazayarPrint", "Unable to open print preview", e);
                    Toast.makeText(MainActivity.this, "بازکردن پیش‌نمایش PDF ممکن نشد", Toast.LENGTH_LONG).show();
                    cleanupPrintWebView();
                }
            }
        });
        printView.loadDataWithBaseURL("https://ghazayar.local/", html, "text/html", "UTF-8", null);
    }
    private void cleanupPrintWebView() {
        WebView finished = activePrintWebView;
        activePrintWebView = null;
        printPreviewLaunched = false;
        if (finished != null) {
            finished.stopLoading();
            finished.setWebViewClient(null);
            finished.destroy();
        }
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
        } else if (r == BACKUP_OPEN_REQUEST) {
            if (c == RESULT_OK && d != null && d.getData() != null) {
                try (InputStream in = getContentResolver().openInputStream(d.getData());
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    if (in == null) throw new Exception("No input stream");
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
                    String content = buffer.toString("UTF-8");
                    String script = "window.GhazayarRestoreFromNative && window.GhazayarRestoreFromNative("
                        + org.json.JSONObject.quote(content) + ")";
                    if (webView != null) webView.post(() -> webView.evaluateJavascript(script, null));
                } catch (Exception e) {
                    android.util.Log.e("GhazayarBackup", "Backup read failed", e);
                    if (webView != null) webView.post(() ->
                        webView.evaluateJavascript("window.GhazayarRestoreFromNative && window.GhazayarRestoreFromNative('')", null));
                }
            }
        } else if (r == BACKUP_SAVE_REQUEST) {
            boolean saved = false;
            if (c == RESULT_OK && d != null && d.getData() != null && pendingBackupContent != null) {
                try (OutputStream out = getContentResolver().openOutputStream(d.getData(), "wt")) {
                    if (out == null) throw new Exception("No output stream");
                    out.write(pendingBackupContent.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    saved = true;
                } catch (Exception e) {
                    android.util.Log.e("GhazayarBackup", "Backup write failed", e);
                }
            }
            pendingBackupContent = null;
            final boolean result = saved;
            if (webView != null) webView.post(() ->
                webView.evaluateJavascript("window.GhazayarBackupResult && window.GhazayarBackupResult(" + result + ")", null));
        }
    }
    @Override protected void onResume() {
        super.onResume();
        // Android print preview is a separate system activity. When it closes,
        // this Activity resumes; release the temporary print WebView here and
        // keep the original application WebView alive and visible.
        if (printPreviewLaunched && activePrintWebView != null) {
            cleanupPrintWebView();
        }
        if (webView != null) { webView.onResume(); webView.resumeTimers(); webView.requestFocus(); }
    }
    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        cleanupPrintWebView();
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle b) { if (webView != null) webView.saveState(b); super.onSaveInstanceState(b); }
    @Override public void onBackPressed() {
        if (printPreviewLaunched && activePrintWebView != null) {
            cleanupPrintWebView();
            if (webView != null) { webView.onResume(); webView.resumeTimers(); }
            return;
        }
        if (webView != null) {
            webView.evaluateJavascript("(function(){return window.GhazayarHandleBack ? window.GhazayarHandleBack() : false})()", value -> {
                if ("false".equals(value) || value == null) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("خروج از غذایار")
                        .setMessage("آیا مطمئن هستید که می‌خواهید از برنامه خارج شوید؟")
                        .setNegativeButton("ادامه برنامه", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("خروج", (dialog, which) -> finish())
                        .show();
                }
            });
        } else super.onBackPressed();
    }
}
