/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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
package org.atalk.xryptomail.permissions;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.*;
import com.karumi.dexter.listener.multi.*;
import com.karumi.dexter.listener.single.*;

import org.atalk.xryptomail.R;

import java.util.LinkedList;
import java.util.List;

import butterknife.*;

/**
 * Sample activity showing the permission request process with Dexter.
 */
public class PermissionsActivity extends Activity
{
    @BindView(R.id.contacts_permission_feedback)
    TextView contactsPermissionFeedbackView;
    @BindView(R.id.storage_permission_feedback)
    TextView storagePermissionFeedbackView;
    @BindView(R.id.delete_mail_permission_feedback)
    TextView deleteMailPermissionFeedbackView;
    @BindView(R.id.read_mail_permission_feedback)
    TextView readMailPermissionFeedbackView;
    @BindView(R.id.remote_control_permission_feedback)
    TextView remoteControlPermissionFeedbackView;
    @BindView(R.id.app_info_permissions_button)
    Button button_app_info;
    @BindView(android.R.id.content)
    View contentView;

    private MultiplePermissionsListener allPermissionsListener;
    private MultiplePermissionsListener dialogMultiplePermissionsListener;
    private PermissionListener contactsPermissionListener;
    private PermissionListener storagePermissionListener;
    private PermissionListener deleteMailPermissionListener;
    private PermissionListener readMailPermissionListener;
    private PermissionListener remoteControlPermissionListener;
    private PermissionRequestErrorListener errorListener;

    protected static List<PermissionGrantedResponse> grantedPermissionResponses = new LinkedList<>();
    protected static List<PermissionDeniedResponse> deniedPermissionResponses = new LinkedList<>();

    private final static String permission_DELETE_MESSAGES = "org.atalk.xryptomail.permission.DELETE_MESSAGES";
    private final static String permission_READ_MESSAGES = "org.atalk.xryptomail.permission.READ_MESSAGES";
    private final static String permission_REMOTE_CONTROL = "org.atalk.xryptomail.permission.REMOTE_CONTROL";

    AlertDialog.Builder alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permissions_activity);
        ButterKnife.bind(this);
        createPermissionListeners();
        if (!getPackagePermissionsStatus())
            finish();
        permissionsStatusUpdate();
    }

    public void onAllPermissionsCheck()
    {
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        permission_DELETE_MESSAGES,
                        permission_READ_MESSAGES,
                        permission_REMOTE_CONTROL
                )
                .withListener(allPermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.all_permissions_button)
    public void onAllPermissionsButtonClicked()
    {
        button_app_info.setVisibility(View.INVISIBLE);
        grantedPermissionResponses.clear();
        deniedPermissionResponses.clear();

        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        permission_DELETE_MESSAGES,
                        permission_READ_MESSAGES,
                        permission_REMOTE_CONTROL
                )
                .withListener(dialogMultiplePermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.contacts_permission_button)
    public void onContactsPermissionButtonClicked()
    {
        // Must request for both for API-28
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.READ_CONTACTS)
                .withListener(contactsPermissionListener)
                .withErrorListener(errorListener)
                .check();

        Dexter.withActivity(this)
                .withPermission(Manifest.permission.WRITE_CONTACTS)
                .withListener(contactsPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.storage_permission_button)
    public void onStoragePermissionButtonClicked()
    {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(storagePermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.delete_mail_permission_button)
    public void onPhonePermissionButtonClicked()
    {
        Dexter.withActivity(this)
                .withPermission(permission_DELETE_MESSAGES)
                .withListener(deleteMailPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.read_mail_permission_button)
    public void onAudioPermissionButtonClicked()
    {
        Dexter.withActivity(this)
                .withPermission(permission_READ_MESSAGES)
                .withListener(readMailPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.remote_control_permission_button)
    public void onLocationPermissionButtonClicked()
    {
        Dexter.withActivity(this)
                .withPermission(permission_REMOTE_CONTROL)
                .withListener(remoteControlPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @OnClick(R.id.app_info_permissions_button)
    public void onInfoButtonClicked()
    {
        Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + this.getPackageName()));
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
        myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(myAppSettings);
    }

    @OnClick(R.id.button_done)
    public void onDoneButtonClicked()
    {
        finish();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void showPermissionRationale(final PermissionToken token)
    {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        token.cancelPermissionRequest();
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        token.continuePermissionRequest();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    @Override
                    public void onDismiss(DialogInterface dialog)
                    {
                        token.cancelPermissionRequest();
                    }
                })
                .show();
    }

    /**
     * Retrieve the package current default permissions status on create;
     * only if both the arrays are empty. Non-empty -> orientation change
     */
    @TargetApi(23)
    private boolean getPackagePermissionsStatus()
    {
        if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses.isEmpty()) {
            PackageManager pm = getPackageManager();
            try {
                PackageInfo packageInfo = pm.getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);

                //Get Permissions
                String[] requestedPermissions = packageInfo.requestedPermissions;
                if (requestedPermissions != null) {
                    for (String requestedPermission : requestedPermissions) {
                        if (getFeedbackViewForPermission(requestedPermission) == null)
                            continue;

                        PermissionRequest pr = new PermissionRequest(requestedPermission);
                        //denied
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestedPermission)) {
                            deniedPermissionResponses.add(new PermissionDeniedResponse(pr, false));
                        }
                        else {
                            //allowed
                            if (ActivityCompat.checkSelfPermission(this,
                                    requestedPermission) == PackageManager.PERMISSION_GRANTED) {
                                grantedPermissionResponses.add(new PermissionGrantedResponse(pr));
                            }
                            // set to never ask again
                            else {
                                deniedPermissionResponses.add(new PermissionDeniedResponse(pr, true));
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        // Proceed to request user for permissions if not all are permanently denied
        for (PermissionDeniedResponse response : deniedPermissionResponses) {
            if (!response.isPermanentlyDenied())
                return true;
        }
        /*
         * It seems that some android devices have init all requested permissions to permanently denied states
         * i.e. incorrect return value for: ActivityCompat.shouldShowRequestPermissionRationale == false
         * All permission must be granted to XryptoMail - otherwise will not work in almost cases;
         * Bug user and request for it anyway, otherwise XryptoMail will not work properly.
         */
        if (deniedPermissionResponses.size() != 0) {
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.service_gui_WARNING)
                    .setMessage(getString(R.string.all_permissions_denied_feedback))
                    .setPositiveButton(R.string.okay_action,
                            new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface d, int c)
                                {
                                    d.dismiss();
                                }
                            });
            alertDialog.show();
            return true;
        }
        // Do not disturb user, if he has chosen partially granted the permissions.
        return false;
    }

    /**
     * Update the permissions status with the default application permissions on entry
     */
    private void permissionsStatusUpdate()
    {
        // if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses
        for (PermissionGrantedResponse response : grantedPermissionResponses) {
            showPermissionGranted(response.getPermissionName());
        }

        for (PermissionDeniedResponse response : deniedPermissionResponses) {
            showPermissionDenied(response.getPermissionName(), response.isPermanentlyDenied());
        }
    }

    /**
     * Update the granted permissions for the package
     *
     * @param permission permission view to be updated
     */
    public void showPermissionGranted(String permission)
    {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(R.string.permission_granted_feedback);
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_granted));
        }
    }

    /**
     * Update the denied permissions for the package
     *
     * @param permission permission view to be updated
     */
    public void showPermissionDenied(String permission, boolean isPermanentlyDenied)
    {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(isPermanentlyDenied
                    ? R.string.permission_permanently_denied_feedback : R.string.permission_denied_feedback);
            if (isPermanentlyDenied) {
                button_app_info.setVisibility(View.VISIBLE);
            }
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_denied));
        }
    }

    /**
     * Initialize all the permission listener required actions
     */
    private void createPermissionListeners()
    {
        PermissionListener dialogOnDeniedPermissionListener;
        PermissionListener feedbackViewPermissionListener = new AppPermissionListener(this);
        MultiplePermissionsListener feedbackViewMultiplePermissionListener = new MultiplePermissionListener(this);

        allPermissionsListener = new CompositeMultiplePermissionsListener(feedbackViewMultiplePermissionListener,
                SnackbarOnAnyDeniedMultiplePermissionsListener.Builder
                        .with(contentView, R.string.all_permissions_denied_feedback)
                        .withOpenSettingsButton(R.string.permission_rationale_settings_button_text)
                        .build());

        DialogOnAnyDeniedMultiplePermissionsListener dialogOnAnyDeniedPermissionListener
                = DialogOnAnyDeniedMultiplePermissionsListener.Builder
                .withContext(this)
                .withTitle(R.string.all_permission_denied_dialog_title)
                .withMessage(R.string.all_permissions_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        dialogMultiplePermissionsListener = new CompositeMultiplePermissionsListener(
                feedbackViewMultiplePermissionListener, dialogOnAnyDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.contacts_permission_denied_dialog_title)
                .withMessage(R.string.contacts_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        contactsPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.storage_permission_denied_dialog_title)
                .withMessage(R.string.storage_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        storagePermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.delete_mail_permission_denied_dialog_title)
                .withMessage(R.string.delete_mail_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        deleteMailPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.read_mail_permission_denied_dialog_title)
                .withMessage(R.string.read_mail_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        readMailPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.remote_control_permission_denied_dialog_title)
                .withMessage(R.string.remote_control_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        remoteControlPermissionListener = new CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener);

        errorListener = new PermissionsErrorListener();
    }

    /**
     * Get the view of the permission for update, null if view does not exist
     *
     * @param name permission name
     * @return the textView for the request permission
     */
    private TextView getFeedbackViewForPermission(String name)
    {
        TextView feedbackView;
        switch (name) {
            case Manifest.permission.READ_CONTACTS:
            case Manifest.permission.WRITE_CONTACTS:
                feedbackView = contactsPermissionFeedbackView;
                break;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                feedbackView = storagePermissionFeedbackView;
                break;
            case permission_DELETE_MESSAGES:
                feedbackView = deleteMailPermissionFeedbackView;
                break;
            case permission_READ_MESSAGES:
                feedbackView = readMailPermissionFeedbackView;
                break;
            case permission_REMOTE_CONTROL:
                feedbackView = remoteControlPermissionFeedbackView;
                break;
            default:
                feedbackView = null;
        }
        return feedbackView;
    }
}
