/*
 * XryptoMail, android mail client
 * Copyright 2011 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.xryptomail.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.KeyEvent;
import android.view.View;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <tt>DialogActivity</tt> can be used to display alerts without having parent <tt>Activity</tt>
 * (from services). <br/> Simple alerts can be displayed using static method <tt>showDialog(...)
 * </tt>.<br/> Optionally confirm button's text and the listener can be supplied. It allows to
 * react to users actions. For this purpose use method <tt>showConfirmDialog(...)</tt>.<br/>
 * For more sophisticated use cases content fragment class with it's arguments can be specified
 * in method <tt>showCustomDialog()</tt>. When they're present the alert message will be replaced
 * by the {@link Fragment}'s <tt>View</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class DialogActivity extends FragmentActivity
{
    /**
     * Dialog title extra.
     */
    public static final String EXTRA_TITLE = "title";

    /**
     * Dialog message extra.
     */
    public static final String EXTRA_MESSAGE = "message";

    /**
     * Optional confirm button label extra.
     */
    public static final String EXTRA_CONFIRM_TXT = "confirm_txt";

    /**
     * Dialog id extra used to listen for close dialog broadcast intents.
     */
    private static final String EXTRA_DIALOG_ID = "dialog_id";

    /**
     * Optional listener ID extra(can be supplied only using method static <tt>showConfirmDialog</tt>.
     */
    public static final String EXTRA_LISTENER_ID = "listener_id";

    /**
     * Optional content fragment's class name that will be used instead of text message.
     */
    public static final String EXTRA_CONTENT_FRAGMENT = "fragment_class";

    /**
     * Optional content fragment's argument <tt>Bundle</tt>.
     */
    public static final String EXTRA_CONTENT_ARGS = "fragment_args";

    /**
     * Prevents from closing this activity on outside touch events and blocks the back key if set to <tt>true</tt>.
     */
    public static final String EXTRA_CANCELABLE = "cancelable";

    /**
     * Hide all buttons.
     */
    public static final String EXTRA_REMOVE_BUTTONS = "remove_buttons";

    /**
     * Static map holds listeners for currently displayed dialogs.
     */
    private static Map<Long, DialogListener> listenersMap = new HashMap<>();

    /**
     * Static list holds existing dialog instances (since onCreate() until onDestroy()). Only
     * dialogs with valid id are listed here.
     */
    private final static List<Long> displayedDialogs = new ArrayList<>();

    /**
     * The dialog listener.
     */
    private DialogListener listener;

    /**
     * Dialog listener's id used to identify listener in {@link #listenersMap}.
     */
    private long listenerID;

    /**
     * Flag remembers if the dialog was confirmed.
     */
    private boolean confirmed;

    private static LocalBroadcastManager localBroadcastManager
            = LocalBroadcastManager.getInstance(XryptoMail.getGlobalContext());

    /**
     * <tt>BroadcastReceiver</tt> that listens for close dialog action.
     */
    private CommandDialogListener commandIntentListener;

    /**
     * Name of the action which can be used to close dialog with given id supplied in
     * {@link #EXTRA_DIALOG_ID}.
     */
    public static final String ACTION_CLOSE_DIALOG = "org.atalk.gui.close_dialog";

    /**
     * Name of the action which can be used to focus dialog with given id supplied in
     * {@link #EXTRA_DIALOG_ID}.
     */
    public static final String ACTION_FOCUS_DIALOG = "org.atalk.gui.focus_dialog";

    private boolean cancelable;
    private View mContent;

    /**
     * {@inheritDoc}
     * cmeng: the onCreate get retrigger by android when developer option on keep activity is enabled
     * then the new dialog is not disposed.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.alert_dialog);
        mContent = findViewById(android.R.id.content);
        setTitle(intent.getStringExtra(EXTRA_TITLE));

        // Message or custom content
        String contentFragment = intent.getStringExtra(EXTRA_CONTENT_FRAGMENT);
        if (contentFragment != null) {
            // Hide alert text
            ViewUtil.ensureVisible(mContent, R.id.alertText, false);

            // Display content fragment
            if (savedInstanceState == null) {
                try {
                    // Instantiate content fragment
                    Class contentClass = Class.forName(contentFragment);
                    Fragment fragment = (Fragment) contentClass.newInstance();

                    // Set fragment arguments
                    fragment.setArguments(intent.getBundleExtra(EXTRA_CONTENT_ARGS));

                    // Insert the fragment
                    getSupportFragmentManager().beginTransaction().replace(R.id.alertContent, fragment).commit();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else {
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.alertText,
                    intent.getStringExtra(EXTRA_MESSAGE));
        }

        // Confirm button text
        String confirmTxt = intent.getStringExtra(EXTRA_CONFIRM_TXT);
        if (confirmTxt != null) {
            ViewUtil.setTextViewValue(mContent, R.id.okButton, confirmTxt);
        }

        // Show cancel button if confirm label is not null
        ViewUtil.ensureVisible(mContent, R.id.cancelButton, confirmTxt != null);

        // Sets the listener
        this.listenerID = intent.getLongExtra(EXTRA_LISTENER_ID, -1);
        if (listenerID != -1) {
            this.listener = listenersMap.get(listenerID);
        }
        this.cancelable = intent.getBooleanExtra(EXTRA_CANCELABLE, false);

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(cancelable);

        // Removes the buttons
        if (intent.getBooleanExtra(EXTRA_REMOVE_BUTTONS, false)) {
            ViewUtil.ensureVisible(mContent, R.id.okButton, false);
            ViewUtil.ensureVisible(mContent, R.id.cancelButton, false);
        }

        // Close this dialog on ACTION_CLOSE_DIALOG broadcast
        long dialogId = intent.getLongExtra(EXTRA_DIALOG_ID, -1);
        if (dialogId != -1) {
            commandIntentListener = new CommandDialogListener(dialogId);
            IntentFilter intentFilter = new IntentFilter(ACTION_CLOSE_DIALOG);
            intentFilter.addAction(ACTION_FOCUS_DIALOG);
            localBroadcastManager.registerReceiver(commandIntentListener, intentFilter);

            // Adds this dialog to active dialogs list and notifies all waiting threads.
            synchronized (displayedDialogs) {
                displayedDialogs.add(dialogId);
                displayedDialogs.notifyAll();
            }
        }
    }

    /**
     * Returns the content fragment. It can contain alert message or be the custom fragment class instance.
     *
     * @return dialog content fragment.
     */
    public Fragment getContentFragment()
    {
        return getSupportFragmentManager().findFragmentById(R.id.alertContent);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (!cancelable && keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Fired when confirm button is clicked.
     *
     * @param v the confirm button view.
     */
    @SuppressWarnings("unused")
    public void onOkClicked(View v)
    {
        if (listener != null) {
            if (!listener.onConfirmClicked(this)) {
                return;
            }
        }
        confirmed = true;
        finish();
    }

    /**
     * Fired when cancel button is clicked.
     *
     * @param v the cancel button view.
     */
    @SuppressWarnings("unused")
    public void onCancelClicked(View v)
    {
        finish();
    }

    /**
     * Removes listener from the map.
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        /*
         * cmeng: cannot do here as this is triggered when dialog is obscured by other activity
         * when developer do keep activity is enable
        if (commandIntentListener != null) {
            localBroadcastManager.unregisterReceiver(commandIntentListener);

           // Notify about dialogs list change
           synchronized (displayedDialogs) {
                displayedDialogs.remove(listenerID);
                displayedDialogs.notifyAll();
            }
        }
        */
        // Notify that dialog was cancelled if confirmed == false
        if (listener != null && !confirmed) {
            listener.onDialogCancelled(this);
        }

        // Removes the listener from map
        if (listenerID != -1) {
            listenersMap.remove(listenerID);
        }
    }

    /**
     * Broadcast Receiver to act on command received in #intent.getExtra()
     */
    class CommandDialogListener extends BroadcastReceiver
    {
        private final long dialogId;

        CommandDialogListener(long dialogId)
        {
            this.dialogId = dialogId;
        }

        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getLongExtra(EXTRA_DIALOG_ID, -1) == dialogId) {
                if (ACTION_CLOSE_DIALOG.equals(intent.getAction())) {
                    // Unregistered listener and finish this activity with dialogId
                    if (commandIntentListener != null) {
                        localBroadcastManager.unregisterReceiver(commandIntentListener);

                        // Notify about dialogs list change
                        synchronized (displayedDialogs) {
                            displayedDialogs.remove(listenerID);
                            displayedDialogs.notifyAll();
                        }
                        commandIntentListener = null;
                    }
                    finish();
                }
                else if (ACTION_FOCUS_DIALOG.equals(intent.getAction())) {
                    mContent.bringToFront();
                }
            }
        }
    }

    /**
     * Fires {@link #ACTION_CLOSE_DIALOG} broadcast action in order to close the dialog identified
     * by given <tt>dialogId</tt>.
     *
     * @param dialogId dialog identifier returned when the dialog was created.
     */
    public static void closeDialog(long dialogId)
    {
        Intent intent = new Intent(ACTION_CLOSE_DIALOG);
        intent.putExtra(EXTRA_DIALOG_ID, dialogId);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Fires {@link #ACTION_FOCUS_DIALOG} broadcast action in order to focus the dialog identified
     * by given <tt>dialogId</tt>.
     *
     * @param dialogId dialog identifier returned when the dialog was created.
     */
    public static void focusDialog(long dialogId)
    {
        Intent intent = new Intent(ACTION_FOCUS_DIALOG);
        intent.putExtra(EXTRA_DIALOG_ID, dialogId);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Show simple alert that will be disposed when user presses OK button.
     *
     * @param ctx Android context.
     * @param title the dialog title that will be used.
     * @param message the dialog message that will be used.
     */
    public static void showDialog(Context ctx, String title, String message)
    {
        Intent alert = getDialogIntent(ctx, title, message);
        ctx.startActivity(alert);
    }

    /**
     * Creates an <tt>Intent</tt> that will display a dialog with given <tt>title</tt> and content <tt>message</tt>.
     *
     * @param ctx Android context.
     * @param title dialog title that will be used
     * @param message dialog message that wil be used.
     * @return an <tt>Intent</tt> that will display a dialog.
     */
    public static Intent getDialogIntent(Context ctx, String title, String message)
    {
        Intent alert = new Intent(ctx, DialogActivity.class);
        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_MESSAGE, message);
        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return alert;
    }

    /**
     * Shows confirm dialog allowing to handle confirm action using supplied <tt>listener</tt>.
     *
     * @param context Android context.
     * @param title dialog title that will be used
     * @param message dialog message that wil be used.
     * @param confirmTxt confirm button label.
     * @param listener the confirm action listener.
     */
    public static void showConfirmDialog(Context context, String title, String message,
            String confirmTxt, DialogListener listener)
    {
        Intent alert = new Intent(context, DialogActivity.class);

        if (listener != null) {
            long listenerID = System.currentTimeMillis();
            listenersMap.put(listenerID, listener);
            alert.putExtra(EXTRA_LISTENER_ID, listenerID);
        }

        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_MESSAGE, message);
        alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt);

        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alert);
    }

    /**
     * Show custom dialog. Alert text will be replaced by the {@link Fragment} created from
     * <tt>fragmentClass</tt> name. Optional <tt>fragmentArguments</tt> <tt>Bundle</tt> will be
     * supplied to created instance.
     *
     * @param context Android context.
     * @param title the title that will be used.
     * @param fragmentClass <tt>Fragment</tt>'s class name that will be used instead of text message.
     * @param fragmentArguments optional <tt>Fragment</tt> arguments <tt>Bundle</tt>.
     * @param confirmTxt the confirm button's label.
     * @param listener listener that will be notified on user actions.
     * @param extraArguments additional arguments with keys defined in {@link DialogActivity}.
     */
    public static long showCustomDialog(Context context, String title, String fragmentClass,
            Bundle fragmentArguments, String confirmTxt,
            DialogListener listener, Map<String, Serializable> extraArguments)
    {
        Intent alert = new Intent(context, DialogActivity.class);
        long dialogId = System.currentTimeMillis();

        alert.putExtra(EXTRA_DIALOG_ID, dialogId);

        if (listener != null) {
            listenersMap.put(dialogId, listener);
            alert.putExtra(EXTRA_LISTENER_ID, dialogId);
        }

        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt);

        alert.putExtra(EXTRA_CONTENT_FRAGMENT, fragmentClass);
        alert.putExtra(EXTRA_CONTENT_ARGS, fragmentArguments);

        if (extraArguments != null) {
            for (String key : extraArguments.keySet()) {
                alert.putExtra(key, extraArguments.get(key));
            }
        }
        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alert.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        context.startActivity(alert);
        return dialogId;
    }

    /**
     * Waits until the dialog with given <tt>dialogId</tt> is opened.
     *
     * @param dialogId the id of the dialog we want to wait for.
     * @return <tt>true</tt> if dialog has been opened or <tt>false</tt> if the dialog had not
     * been opened within 10 seconds after call to this method.
     */
    public static boolean waitForDialogOpened(long dialogId)
    {
        synchronized (displayedDialogs) {
            if (!displayedDialogs.contains(dialogId)) {
                try {
                    displayedDialogs.wait(10000);
                    return displayedDialogs.contains(dialogId);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                return true;
            }
        }
    }

    /**
     * The listener that will be notified when user clicks the confirm button or dismisses the dialog.
     */
    public interface DialogListener
    {
        /**
         * Fired when user clicks the dialog's confirm button.
         *
         * @param dialog source <tt>DialogActivity</tt>.
         */
        boolean onConfirmClicked(DialogActivity dialog);

        /**
         * Fired when user dismisses the dialog.
         *
         * @param dialog source <tt>DialogActivity</tt>
         */
        void onDialogCancelled(DialogActivity dialog);
    }
}
