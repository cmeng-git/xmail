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
package org.atalk.xryptomail.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;

import org.atalk.xryptomail.helper.MySqlConn;
import org.atalk.xryptomail.BuildConfig;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.helper.DownloadFile;
import org.atalk.xryptomail.service.OnlineUpdateService;

import java.io.File;

/**
 * OnlineUpdate activity
 **/
public class OnlineUpdate extends Activity implements OnClickListener {
	private boolean mSkipCheckVersion = false;

	public enum OnlineUpdateStatus {
		NEW_VERSION_FOUND(0), 
		NO_NEW_VERSION_FOUND(10), 
		FETCH_VERSION_ERROR(20), 
		DOWNLOAD_APK_ERROR(30), 
		DOWNLOAD_APK_SUCCESSFUL(40);

		private final int value;

		private OnlineUpdateStatus(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		mSkipCheckVersion = intent.getBooleanExtra(OnlineUpdateService.SKIP_VERSION_CHECK, false);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.online_update);
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,R.drawable.ic_icon);

		if (mSkipCheckVersion) {
			String msgstr = String.format(getString(R.string.new_version_found), XryptoMail.mVersionOnServer);
			((TextView) findViewById(R.id.textViewVersion)).setText(msgstr);

			findViewById(R.id.ok_button).setOnClickListener(this);
			findViewById(R.id.cancel_button).setOnClickListener(this);

			findViewById(R.id.ok_button).setVisibility(View.VISIBLE);
			findViewById(R.id.cancel_button).setVisibility(View.VISIBLE);
			findViewById(R.id.textViewVersion).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.ok_button).setVisibility(View.GONE);
			findViewById(R.id.cancel_button).setVisibility(View.GONE);

			((TextView) findViewById(R.id.textViewVersion)).setText(getString(R.string.checking_update));
			findViewById(R.id.textViewVersion).setVisibility(View.VISIBLE);

			QueryVersion queryVersion = new QueryVersion();
			queryVersion.execute();
		}
	}

	private class QueryVersion extends AsyncTask<Void, Void, Integer> {
		@Override
		protected Integer doInBackground(Void... params) {
			int iResult = RESULT_OK;

			ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
			if (null == activeNetworkInfo) {
				// No network connection found.
				return RESULT_CANCELED;
			}

            // Default mVersionOnServer same as installed version in cass sql access failed
            XryptoMail.mVersionOnServer = XryptoMail.mVersion;
            XryptoMail.mNewApkPathOnServer = "";

            MySqlConn sqlconn = new MySqlConn(XryptoMail.CRYPTO_SERVER, XryptoMail.mCurUserID);
			try {
				ContentValues contentValues;
				if (BuildConfig.DEBUG) {
					// Support debug version update via network
					contentValues = sqlconn.newAppCheckup(OnlineUpdateService.KEY_APP_NAME_DEBUG);
				} else {
					contentValues = sqlconn.newAppCheckup(OnlineUpdateService.KEY_APP_NAME);
				}

                // proceed only if server access is successful with new contentValues
                if (contentValues != null) {
                    XryptoMail.mNewApkPathOnServer = contentValues.getAsString("Path");
                    XryptoMail.mVersionOnServer = contentValues.getAsString("Version");
                }

			} catch (Exception e) {
				e.printStackTrace();
				iResult = RESULT_CANCELED;
			}
			return iResult;
		}

		@Override
		protected void onPostExecute(Integer Result) {
			super.onPostExecute(Result);
			OnlineUpdate.this.onVersionReady(Result);
		}
	}

	public class DownloadWithConfirm extends DownloadFile {
		private ProgressDialog mProgressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			mProgressDialog = new ProgressDialog(OnlineUpdate.this , R.style.Theme_XMail_Dialog_Dark);
			mProgressDialog.setMessage("Downloading");
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setMax(100);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			super.onProgressUpdate(progress);
			mProgressDialog.setProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(Integer Result) {
			super.onPostExecute(Result);
			
			if (RESULT_CANCELED.equals(Result)) {
				setResult(OnlineUpdateStatus.DOWNLOAD_APK_ERROR.getValue());
			} else {
				if (mSkipCheckVersion) {
					if (!TextUtils.isEmpty(XryptoMail.mUpdateApkPath)) {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.fromFile(new File(XryptoMail.mUpdateApkPath)),"application/vnd.android.package-archive");
						startActivity(intent);
					}
				} else {
					setResult(OnlineUpdateStatus.DOWNLOAD_APK_SUCCESSFUL.getValue());
				}
			}
			finish();
		}
	}

	private void onVersionReady(Integer result) {
		if (RESULT_OK != result) {
			setResult(OnlineUpdateStatus.FETCH_VERSION_ERROR.getValue());
			finish();
			return;
		}
		if ((XryptoMail.mVersionOnServer == null)
				|| (OnlineUpdateService.versionCompare(XryptoMail.mVersion, XryptoMail.mVersionOnServer) != -1)
				|| (XryptoMail.mNewApkPathOnServer == null)
				|| (XryptoMail.mNewApkPathOnServer.isEmpty())) {
			setResult(OnlineUpdateStatus.NO_NEW_VERSION_FOUND.getValue());
			finish();
			return;
		}

		String msgstr = String.format(getString(R.string.new_version_found), XryptoMail.mVersionOnServer);
		((TextView) findViewById(R.id.textViewVersion)).setText(msgstr);
		findViewById(R.id.ok_button).setOnClickListener(this);
		findViewById(R.id.cancel_button).setOnClickListener(this);
		findViewById(R.id.ok_button).setVisibility(View.VISIBLE);
		findViewById(R.id.cancel_button).setVisibility(View.VISIBLE);
	}

	@Override
	public void onClick(View view) {
		boolean cancelUpdate = false;

		if (view.getId() == R.id.ok_button) {
			DownloadWithConfirm downloadFile = new DownloadWithConfirm();
			downloadFile.execute(XryptoMail.mNewApkPathOnServer);

		} else if (view.getId() == R.id.cancel_button) {
			cancelUpdate = true;
		}

		if (cancelUpdate) {
			setResult(RESULT_CANCELED);
			finish();
		} else {
			setResult(RESULT_OK);
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}
}
