package org.atalk.xryptomail.view;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.TrafficStats;
import android.net.Uri;
import android.provider.Browser;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.mailstore.AttachmentResolver;
import org.atalk.xryptomail.view.MessageWebView.OnPageFinishedListener;

import timber.log.Timber;

/**
 * {@link WebViewClient} that intercepts requests for {@code cid:} URIs to load the respective body part.
 */
public class XMWebViewClient extends WebViewClient {
    private static final String CID_SCHEME = "cid";
    private static final WebResourceResponse RESULT_DO_NOT_INTERCEPT = null;
    private static final WebResourceResponse RESULT_DUMMY_RESPONSE = new WebResourceResponse(null, null, null);
    private OnPageFinishedListener onPageFinishedListener;

    @Nullable
    private final AttachmentResolver attachmentResolver;

    public static XMWebViewClient newInstance(@Nullable AttachmentResolver attachmentResolver) {
        return new XMWebViewClient(attachmentResolver);
    }

    private XMWebViewClient(@Nullable AttachmentResolver attachmentResolver) {
        this.attachmentResolver = attachmentResolver;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
        return shouldOverrideUrlLoading(webView, request.getUrl());
    }

    private boolean shouldOverrideUrlLoading(WebView webView, Uri uri) {
        if (CID_SCHEME.equals(uri.getScheme())) {
            return false;
        }

        Context context = webView.getContext();
        Intent intent = createBrowserViewIntent(uri, context);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(context, R.string.error_activity_not_found, Toast.LENGTH_LONG).show();
        }

        return true;
    }

    private Intent createBrowserViewIntent(Uri uri, Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return intent;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
        Uri uri = request.getUrl();
        String url = uri.toString();
        // Intercept WebView Requests with cleartextTraffic content loading
        if (url.startsWith("http://")) {
            try {
                url = url.replace("http://", "https://");
                URL httpsUrl = new URL(url);
                URLConnection connection = httpsUrl.openConnection();
                TrafficStats.setThreadStatsTag(XryptoMail.THREAD_ID);
                return new WebResourceResponse(connection.getContentType(), connection.getContentEncoding(), connection.getInputStream());
            } catch (Exception e) {
                Timber.e("http to https exception: %s", e.getMessage());
            }
        }

        if (!CID_SCHEME.equals(uri.getScheme())) {
            return RESULT_DO_NOT_INTERCEPT;
        }

        if (attachmentResolver == null) {
            return RESULT_DUMMY_RESPONSE;
        }

        String cid = uri.getSchemeSpecificPart();
        if (TextUtils.isEmpty(cid)) {
            return RESULT_DUMMY_RESPONSE;
        }

        Uri attachmentUri = attachmentResolver.getAttachmentUriForContentId(cid);
        if (attachmentUri == null) {
            return RESULT_DUMMY_RESPONSE;
        }

        Context context = webView.getContext();
        ContentResolver contentResolver = context.getContentResolver();
        try {
            String mimeType = contentResolver.getType(attachmentUri);
            InputStream inputStream = contentResolver.openInputStream(attachmentUri);

            WebResourceResponse webResourceResponse = new WebResourceResponse(mimeType, null, inputStream);
            addCacheControlHeader(webResourceResponse);
            return webResourceResponse;
        } catch (
                Exception e) {
            Timber.e(e, "Error while intercepting URI: %s", uri);
            return RESULT_DUMMY_RESPONSE;
        }
    }

    private void addCacheControlHeader(WebResourceResponse response) {
        Map<String, String> headers = Collections.singletonMap("Cache-Control", "no-store");
        response.setResponseHeaders(headers);
    }

    public void setOnPageFinishedListener(OnPageFinishedListener onPageFinishedListener) {
        this.onPageFinishedListener = onPageFinishedListener;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (onPageFinishedListener != null) {
            onPageFinishedListener.onPageFinished();
        }
    }
}
