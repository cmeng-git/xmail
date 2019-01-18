package org.atalk.xryptomail.helper;

public interface MySqlConnCallBack {
	public static int RESULT_OK = 1;
	public static int RESULT_FAIL = -1;
	public static int RESULT_UNKNOWN= 0;

	void onCompleted(Object data);
	void onFailed(Exception ex);
}
