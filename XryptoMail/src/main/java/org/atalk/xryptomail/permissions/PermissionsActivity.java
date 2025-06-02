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
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.LinkedList;
import java.util.List;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.CompositeMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.multi.SnackbarOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.single.CompositePermissionListener;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.Accounts;
import org.atalk.xryptomail.activity.Splash;
import org.atalk.xryptomail.databinding.PermissionsUiBinding;

import timber.log.Timber;

/**
 * Sample activity showing the permission request process with Dexter.
 */
public class PermissionsActivity extends FragmentActivity {
    private final static String permission_DELETE_MESSAGES = "org.atalk.xryptomail.permission.DELETE_MESSAGES";
    private final static String permission_READ_MESSAGES = "org.atalk.xryptomail.permission.READ_MESSAGES";
    private final static String permission_REMOTE_CONTROL = "org.atalk.xryptomail.permission.REMOTE_CONTROL";

    private PermissionsUiBinding mBinding;
    private MultiplePermissionsListener allPermissionsListener;
    private MultiplePermissionsListener dialogMultiplePermissionsListener;
    private PermissionListener contactsPermissionListener;
    private PermissionListener musicPermissionListener;
    private MultiplePermissionsListener videoPermissionListener;
    private PermissionListener notificationsPermissionListener;
    private PermissionListener storagePermissionListener;
    private PermissionListener deleteMailPermissionListener;
    private PermissionListener readMailPermissionListener;
    private PermissionListener remoteControlPermissionListener;
    private PermissionRequestErrorListener errorListener;

    private ActivityResultLauncher<Void> mBatteryOptimization;

    protected static List<PermissionGrantedResponse> grantedPermissionResponses = new LinkedList<>();
    protected static List<PermissionDeniedResponse> deniedPermissionResponses = new LinkedList<>();

    protected static List<String> permissionList = new LinkedList<>();

    static {
        permissionList.add(Manifest.permission.READ_CONTACTS);
        permissionList.add(permission_DELETE_MESSAGES);
        permissionList.add(permission_READ_MESSAGES);
        permissionList.add(permission_REMOTE_CONTROL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionList.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS);
            permissionList.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            else {
                permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    // Flag indicates this the xMail first launch
    private static boolean permissionFirstRequest = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Always request permission on first apk launch for android.M
        if (permissionFirstRequest) {
            // see if we should show the splash screen and wait for it to complete before continue
            if (Splash.isFirstRun()) {
                Intent intent = new Intent(this, Splash.class);
                startActivity(intent);
            }

            mBinding = PermissionsUiBinding.inflate(getLayoutInflater());
            View view = mBinding.getRoot();
            setContentView(view);
            initView();

            Timber.i("Launching dynamic permission request for XryptoMail.");
            permissionFirstRequest = false;

            // Request user to add XryptoMail to BatteryOptimization whitelist
            // Otherwise XryptoMail will be put to sleep on system doze-standby
            mBatteryOptimization = requestBatteryOptimization();
            boolean showBatteryOptimizationDialog = openBatteryOptimizationDialogIfNeeded();

            createPermissionListeners();
            boolean permissionRequest = getPackagePermissionsStatus();
            permissionsStatusUpdate();

            if ((!permissionRequest) && !showBatteryOptimizationDialog) {
                startLauncher();
            }
        }
        else {
            startLauncher();
        }
    }

    private void startLauncher() {
        Intent i = new Intent(this, Accounts.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // i.putExtra(SystemEventReceiver.AUTO_START_ONBOOT, false);
        startActivity(i);
        finish();
    }

    private void initView() {
        mBinding.contactsPermissionButton.setOnClickListener(v -> onContactsPermissionButtonClicked());
        mBinding.deleteMailPermissionButton.setOnClickListener(v -> onDeleteMailPermissionButtonClicked());
        mBinding.readMailPermissionButton.setOnClickListener(v -> onReadMailPermissionButtonClicked());
        mBinding.remoteControlPermissionButton.setOnClickListener(v -> onRemoteControlPermissionButtonClicked());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mBinding.notificationsPermissionButton.setOnClickListener(v -> onNotificationsPermissionButtonClicked());
            mBinding.musicPermissionButton.setOnClickListener(v -> onMusicPermissionButtonClicked());
            mBinding.storageView.setVisibility(View.GONE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mBinding.videoPermissionButton.setOnClickListener(v -> onMediaUserSelectedPermissionButtonClicked());
            }
        }
        else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                mBinding.storagePermissionButton.setOnClickListener(v -> onWriteStoragePermissionButtonClicked());
            }
            else {
                mBinding.storagePermissionButton.setOnClickListener(v -> onReadStoragePermissionButtonClicked());
            }
            mBinding.notificationsView.setVisibility(View.GONE);
            mBinding.musicView.setVisibility(View.GONE);
            mBinding.videoView.setVisibility(View.GONE);
        }
        mBinding.allPermissionsButton.setOnClickListener(v -> onAllPermissionsButtonClicked());
        mBinding.appInfoPermissionsButton.setOnClickListener(v -> onInfoButtonClicked(this));
        mBinding.buttonDone.setOnClickListener(v -> onDoneButtonClicked());
    }

    public void onDoneButtonClicked() {
        startLauncher();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startLauncher();
    }

    public void onAllPermissionsCheck() {
        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(allPermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onAllPermissionsButtonClicked() {
        grantedPermissionResponses.clear();
        deniedPermissionResponses.clear();

        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(dialogMultiplePermissionsListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onContactsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_CONTACTS)
                .withListener(contactsPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void onNotificationsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.POST_NOTIFICATIONS)
                .withListener(notificationsPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void onMusicPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_MEDIA_AUDIO)
                .withListener(musicPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onMediaUserSelectedPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                .withListener(videoPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onReadStoragePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(storagePermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onWriteStoragePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(storagePermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onDeleteMailPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(permission_DELETE_MESSAGES)
                .withListener(deleteMailPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onReadMailPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(permission_READ_MESSAGES)
                .withListener(readMailPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public void onRemoteControlPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(permission_REMOTE_CONTROL)
                .withListener(remoteControlPermissionListener)
                .withErrorListener(errorListener)
                .check();
    }

    public static void onInfoButtonClicked(Context context) {
        Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName()));
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
        myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(myAppSettings);
    }

    public void showPermissionRationale(final PermissionToken token) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    token.cancelPermissionRequest();
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    token.continuePermissionRequest();
                })
                .setOnDismissListener(dialog -> token.cancelPermissionRequest())
                .show();
    }

    /**
     * Retrieve the package current default permissions status on create;
     * only if both the arrays are empty. Non-empty -> orientation change
     */
    private boolean getPackagePermissionsStatus() {
        if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses.isEmpty()) {
            PackageManager pm = getPackageManager();
            try {
                PackageInfo packageInfo = pm.getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);

                // Get Permissions
                String[] requestedPermissions = packageInfo.requestedPermissions;
                if (requestedPermissions != null) {
                    for (String requestedPermission : requestedPermissions) {
                        if (getFeedbackViewForPermission(requestedPermission) == null)
                            continue;

                        PermissionRequest pr = new PermissionRequest(requestedPermission);
                        // denied
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestedPermission)) {
                            deniedPermissionResponses.add(new PermissionDeniedResponse(pr, false));
                        }
                        else {
                            // allowed
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
                Timber.e("NameNotFoundException: %s", e.getMessage());
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
        // Do not disturb user, if he has chosen partially granted the permissions.
        return grantedPermissionResponses.size() < 5;
    }

    /**
     * Update the permissions status with the default application permissions on entry
     */
    private void permissionsStatusUpdate() {
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
    public void showPermissionGranted(String permission) {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(R.string.permission_granted);
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_granted));
        }
    }

    /**
     * Update the denied permissions for the package
     *
     * @param permission permission view to be updated
     */
    public void showPermissionDenied(String permission, boolean isPermanentlyDenied) {
        TextView feedbackView = getFeedbackViewForPermission(permission);
        if (feedbackView != null) {
            feedbackView.setText(isPermanentlyDenied
                    ? R.string.permission_denied_permanently : R.string.permission_denied);
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_denied));
        }
    }

    /**
     * Initialize all the permission listener required actions
     */
    private void createPermissionListeners() {
        PermissionListener deniedPermissionListener;
        PermissionListener fbPermissionListener = new AppPermissionListener(this);

        DialogOnAnyDeniedMultiplePermissionsListener anyDeniedPermissionListener;
        MultiplePermissionsListener fbMultiplePermissionListener = new MultiplePermissionListener(this);

        View contentView = findViewById(android.R.id.content);
        allPermissionsListener = new CompositeMultiplePermissionsListener(fbMultiplePermissionListener,
                SnackbarOnAnyDeniedMultiplePermissionsListener.Builder
                        .with(contentView, R.string.all_permissions_denied_feedback)
                        .withOpenSettingsButton(R.string.permission_rationale_settings_button_text)
                        .build());

        anyDeniedPermissionListener = DialogOnAnyDeniedMultiplePermissionsListener.Builder
                .withContext(this)
                .withTitle(R.string.all_permission_denied_dialog_title)
                .withMessage(R.string.all_permissions_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        dialogMultiplePermissionsListener = new CompositeMultiplePermissionsListener(fbMultiplePermissionListener, anyDeniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.contacts_permission_denied_dialog_title)
                .withMessage(R.string.contacts_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        contactsPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.notifications_permission_denied_dialog_title)
                .withMessage(R.string.notifications_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        notificationsPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.music_permission_denied_dialog_title)
                .withMessage(R.string.music_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        musicPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        anyDeniedPermissionListener = DialogOnAnyDeniedMultiplePermissionsListener.Builder
                .withContext(this)
                .withTitle(R.string.video_permission_denied_dialog_title)
                .withMessage(R.string.video_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        videoPermissionListener = new CompositeMultiplePermissionsListener(fbMultiplePermissionListener, anyDeniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.storage_permission_denied_dialog_title)
                .withMessage(R.string.storage_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        storagePermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.delete_mail_permission_denied_dialog_title)
                .withMessage(R.string.delete_mail_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        deleteMailPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.read_mail_permission_denied_dialog_title)
                .withMessage(R.string.read_mail_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        readMailPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        deniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.remote_control_permission_denied_dialog_title)
                .withMessage(R.string.remote_control_permission_denied_dialog_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build();
        remoteControlPermissionListener = new CompositePermissionListener(fbPermissionListener, deniedPermissionListener);

        errorListener = new PermissionsErrorListener();
    }

    /**
     * Get the view of the permission for update, null if view does not exist
     *
     * @param name permission name
     *
     * @return the textView for the request permission
     */
    private TextView getFeedbackViewForPermission(String name) {
        TextView feedbackView;
        switch (name) {
            case Manifest.permission.READ_CONTACTS:
                feedbackView = mBinding.contactsPermissionFeedback;
                break;

            case Manifest.permission.POST_NOTIFICATIONS:
                feedbackView = mBinding.notificationsPermissionFeedback;
                break;

            case Manifest.permission.READ_MEDIA_AUDIO:
                feedbackView = mBinding.musicPermissionFeedback;
                break;

            case Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED:
                feedbackView = mBinding.videoPermissionFeedback;
                break;

            case Manifest.permission.READ_EXTERNAL_STORAGE:
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                feedbackView = mBinding.storagePermissionFeedback;
                break;

            case permission_DELETE_MESSAGES:
                feedbackView = mBinding.deleteMailPermissionFeedback;
                break;

            case permission_READ_MESSAGES:
                feedbackView = mBinding.readMailPermissionFeedback;
                break;

            case permission_REMOTE_CONTROL:
                feedbackView = mBinding.remoteControlPermissionFeedback;
                break;

            default:
                feedbackView = null;
        }
        return feedbackView;
    }

    /* **********************************************
     * Android Battery Usage Optimization Request
     ************************************************/
    private boolean openBatteryOptimizationDialogIfNeeded() {
        // Will always request for battery optimization disable for XryptoMail if not so on XryptoMail new launch
        if (isOptimizingBattery()) {
            // Do not launch this within a dialog, else result return on dialog user click
            mBatteryOptimization.launch(null);

//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle(R.string.battery_optimizations);
//            builder.setMessage(R.string.battery_optimizations_dialog);
//
//            builder.setPositiveButton(R.string.next, (dialog, which) -> {
//                dialog.dismiss();
//                mBatteryOptimization.launch(null);
//            });
//
//            AlertDialog dialog = builder.create();
//            dialog.setCanceledOnTouchOutside(false);
//            dialog.show();
            return true;
        }
        else {
            return false;
        }
    }

    protected boolean isOptimizingBattery() {
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return (pm != null) && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * GetBatteryOptimization class ActivityResultContract implementation.
     */
    @SuppressLint("BatteryLife")
    public class GetBatteryOptimization extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Void input) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.parse("package:" + getPackageName());
            intent.setData(uri);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent result) {
            return isOptimizingBattery();
        }
    }

    /**
     * Return success == true if disable battery optimization for aTalk is allowed
     */
    private ActivityResultLauncher<Void> requestBatteryOptimization() {
        return registerForActivityResult(new GetBatteryOptimization(), isOptimizingBattery -> {
            if (isOptimizingBattery) {
                XryptoMail.showToastMessage(R.string.battery_optimization_on);
            }
        });
    }
}
