package org.atalk.xryptomail.helper;

import android.os.AsyncTask;

import org.atalk.xryptomail.XryptoMail;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Utility use by online update to fetch server new apk
 */
public class DownloadFile extends AsyncTask<String, Integer, Integer> {	
	public Integer RESULT_OK = 0;
	public Integer RESULT_CANCELED = 10;

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected Integer doInBackground(String... sUrl) {
		try {
			String defaultDownloadPath = XryptoMail.getAttachmentDefaultPath();

			URL url = new URL(sUrl[0]);
			URLConnection connection = url.openConnection();
			connection.connect();
			// This will be useful so that you can show a typical 0-100% progress bar
			int fileLength = connection.getContentLength();

			// download the file
			InputStream input = new BufferedInputStream(url.openStream());
			String[] urlParts = url.toString().split(Pattern.quote("/"));
            XryptoMail.mUpdateApkPath = defaultDownloadPath + "/" + urlParts[urlParts.length - 1];
			OutputStream output = new FileOutputStream(XryptoMail.mUpdateApkPath);

			byte data[] = new byte[1024];
			long total = 0;
			int count;
			while ((count = input.read(data)) != -1) {
				total += count;
				// publishing the progress....
				publishProgress((int) (total * 100 / fileLength));
				output.write(data, 0, count);
			}

			output.flush();
			output.close();
			input.close();
		} catch (IOException e ) {
            Timber.e(e, "Dowloading file failed");
			return RESULT_CANCELED;
		} catch (Exception e) {
            Timber.e(e, "Dowloading file failed");
			return RESULT_CANCELED;
		}
		return RESULT_OK;
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		super.onProgressUpdate(progress);
		//mProgressDialog.setProgress(progress[0]);
	}

	@Override
	protected void onPostExecute(Integer Result) {
		super.onPostExecute(Result);
	}
}
