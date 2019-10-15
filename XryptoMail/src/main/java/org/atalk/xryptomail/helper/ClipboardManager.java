package org.atalk.xryptomail.helper;

import android.content.ClipData;
import android.content.Context;


/**
 * Access the system clipboard using the new {@link ClipboardManager} introduced with API 11
 */
public class ClipboardManager {
    /**
     * Get API-specific instance of the {@code ClipboardManager} class
     *
     * @param context
     *         A {@link Context} instance.
     *
     * @return Appropriate {@link ClipboardManager} instance for this device.
     */
    public static ClipboardManager getInstance(Context context) {
        Context appContext = context.getApplicationContext();
        return new ClipboardManager(appContext);
    }

    private Context mContext;

    private ClipboardManager(Context context) {
       mContext = context;
    }

    /**
     * Copy a text string to the system clipboard
     *
     * @param label
     *         User-visible label for the content.
     * @param text
     *         The actual text to be copied to the clipboard.
     */
    public void setText(String label, String text) {
        android.content.ClipboardManager clipboardManager =
                (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboardManager.setPrimaryClip(clip);
    }
}
