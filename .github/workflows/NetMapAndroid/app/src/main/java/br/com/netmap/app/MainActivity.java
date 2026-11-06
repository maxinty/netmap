package br.com.netmap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * NetMap para Android: abre o NetMap do seu servidor numa tela cheia, sem barra
 * de endereço. O endereço do servidor é perguntado na primeira vez e fica salvo;
 * para trocar, use o botão "📱 App" da barra do NetMap (ou "Mudar servidor" na
 * tela de erro).
 */
public class MainActivity extends Activity {

    private static final String PREFS = "netmap";
    private static final String KEY_URL = "url";
    private static final String KEY_SSL = "ssl_autoassinado";
    private static final String KEY_AWAKE = "tela_ligada";
    private static final int REQ_ARQUIVO = 1001;

    private WebView web;
    private String urlServidor = "";
    private ValueCallback<Uri[]> callbackArquivo;
    private long ultimoVoltar = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0F1420"));
        setContentView(web);

        configurarWebView();
        aplicarTelaLigada();

        urlServidor = prefs().getString(KEY_URL, "");
        if (urlServidor.isEmpty()) {
            pedirServidor(true);
        } else {
            carregar();
        }
    }

    // ------------------------------------------------------------------
    // Configuração
    // ------------------------------------------------------------------
    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void aplicarTelaLigada() {
        if (prefs().getBoolean(KEY_AWAKE, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private String normalizar(String texto) {
        String u = texto == null ? "" : texto.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        return u;
    }

    private String hostDoServidor() {
        try {
            String h = Uri.parse(urlServidor).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    private void carregar() {
        web.loadUrl(urlServidor);
    }

    private void pedirServidor(final boolean obrigatorio) {
        float d = getResources().getDisplayMetrics().density;
        int p = (int) (20 * d);

        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        caixa.setPadding(p, p / 2, p, 0);

        final EditText campo = new EditText(this);
        campo.setHint("ex.: 192.168.0.10:8642");
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        campo.setText(urlServidor);
        campo.setSelection(campo.getText().length());
        caixa.addView(campo);

        final CheckBox ssl = new CheckBox(this);
        ssl.setText("Aceitar certificado HTTPS autoassinado deste servidor");
        ssl.setChecked(prefs().getBoolean(KEY_SSL, false));
        caixa.addView(ssl);

        final CheckBox acordado = new CheckBox(this);
        acordado.setText("Manter a tela ligada enquanto o NetMap estiver aberto");
        acordado.setChecked(prefs().getBoolean(KEY_AWAKE, false));
        caixa.addView(acordado);

        new AlertDialog.Builder(this)
                .setTitle("Servidor do NetMap")
                .setMessage("Digite o endereço do NetMap na sua rede, o mesmo que você abre no navegador.")
                .setView(caixa)
                .setCancelable(!obrigatorio)
                .setPositiveButton("Conectar", (dlg, w) -> {
                    String nova = normalizar(campo.getText().toString());
                    if (nova.isEmpty()) {
                        Toast.makeText(this, "Digite o endereço do servidor.", Toast.LENGTH_SHORT).show();
                        pedirServidor(obrigatorio);
                        return;
                    }
                    prefs().edit()
                            .putString(KEY_URL, nova)
                            .putBoolean(KEY_SSL, ssl.isChecked())
                            .putBoolean(KEY_AWAKE, acordado.isChecked())
                            .apply();
                    urlServidor = nova;
                    aplicarTelaLigada();
                    carregar();
                })
                .setNegativeButton(obrigatorio ? "Sair" : "Cancelar", (dlg, w) -> {
                    if (obrigatorio) finish();
                })
                .show();
    }

    /** Ponte JavaScript: o NetMap mostra o botão "📱 App" quando roda aqui dentro. */
    private class Ponte {
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> pedirServidor(false));
        }
    }

    private void configurarWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Ponte(), "NetMapApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return tratarLink(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mostrarErro(String.valueOf(error.getDescription()));
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String host = "";
                try {
                    host = Uri.parse(error.getUrl()).getHost();
                } catch (Exception ignorado) {
                    // sem host: não aceita
                }
                // só aceita certificado autoassinado do MESMO servidor configurado, e só se a pessoa marcou
                if (prefs().getBoolean(KEY_SSL, false) && host != null && host.equalsIgnoreCase(hostDoServidor())) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // O NetMap usa alert()/confirm() (apagar, importar...). Sem isto o WebView ignora os dois.
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Cancelar", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String padrao, final JsPromptResult result) {
                final EditText campo = new EditText(MainActivity.this);
                campo.setText(padrao);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(campo)
                        .setPositiveButton("OK", (d, w) -> result.confirm(campo.getText().toString()))
                        .setNegativeButton("Cancelar", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            // Escolher arquivo (importar mapa, ícones personalizados...)
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (callbackArquivo != null) callbackArquivo.onReceiveValue(null);
                callbackArquivo = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolher arquivo"), REQ_ARQUIVO);
                } catch (Exception e) {
                    callbackArquivo = null;
                    return false;
                }
                return true;
            }
        });

        // Backup e outros arquivos que o NetMap manda baixar
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, tamanho) -> {
            try {
                String nome = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                r.addRequestHeader("User-Agent", userAgent);
                if (mimeType != null) r.setMimeType(mimeType);
                r.setTitle(nome);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nome);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(r);
                Toast.makeText(this, "Baixando " + nome + " (pasta Downloads)", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível baixar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Links do próprio servidor ficam no app; os de fora abrem no navegador. */
    private boolean tratarLink(Uri u) {
        if ("netmap".equals(u.getScheme())) {
            String acao = u.getHost();
            if ("retry".equals(acao)) {
                carregar();
            } else if ("config".equals(acao)) {
                pedirServidor(false);
            }
            return true;
        }
        String host = u.getHost();
        if (host != null && host.equalsIgnoreCase(hostDoServidor())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignorado) {
            // nenhum app para abrir
        }
        return true;
    }

    private String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void mostrarErro(String motivo) {
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>"
                + "<body style='background:#0f1420;color:#e7ecf7;font-family:sans-serif;padding:28px;text-align:center'>"
                + "<h2>Sem conexão com o NetMap</h2>"
                + "<p style='color:#8a93ad'>" + escapar(motivo) + "</p>"
                + "<p style='color:#8a93ad'>Servidor: " + escapar(urlServidor) + "</p>"
                + "<p><a href='netmap://retry' style='display:inline-block;margin:8px;padding:12px 22px;"
                + "background:#6c8cff;color:#fff;border-radius:8px;text-decoration:none'>Tentar de novo</a>"
                + "<a href='netmap://config' style='display:inline-block;margin:8px;padding:12px 22px;"
                + "background:#1f2740;color:#e7ecf7;border:1px solid #2a3350;border-radius:8px;text-decoration:none'>Mudar servidor</a></p>"
                + "</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ARQUIVO) {
            if (callbackArquivo != null) {
                callbackArquivo.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                callbackArquivo = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        long agora = System.currentTimeMillis();
        if (agora - ultimoVoltar < 2000) {
            super.onBackPressed();
        } else {
            ultimoVoltar = agora;
            Toast.makeText(this, "Toque de novo em voltar para sair", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
