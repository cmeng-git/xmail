package org.atalk.xryptomail.helper;

import android.content.ContentValues;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

import timber.log.Timber;

public class MySqlConn implements MySqlConnCallBack {
	protected static String _url;
	protected static final String _dbUserId = "User";
	protected static final String _dbPassword = "pwd9881";

	// mySQL database table column variables - must have exact match/case with cryptomsgweb defined names
	private static final String _dbName_APP_NAME = "AppName";
	private static final String _dbName_APP_PATH = "Path";
	private static final String _dbName_APP_VERSION = "Version";

	protected boolean _expectResult = true;
	protected String _btalkUserName = "";
	private String _appName = "";

	protected String[] _fields = null;

	protected String[][] _outputData = null;
	private HttpURLConnection _httpConn = null;
	private OutputStream _out = null;

	private MySqlConnCallBack sqlConnListener = null;

	public MySqlConn(String ServerIP, String UsrName) {
		_btalkUserName = UsrName;
		_url = "http://" + ServerIP + "/cryptosql/?"; //why without ? can work in some cases?
	}

	public void registerListener(MySqlConnCallBack callback) {
		sqlConnListener = callback;
	}

	public void unRegisterListener() {
		sqlConnListener = null;
	}

    @Override
    public void onCompleted(Object data) {
        if (sqlConnListener != null) {
            sqlConnListener.onCompleted(data);
        }
    }

    @Override
    public void onFailed(Exception ex) {
        if (sqlConnListener != null) {
            sqlConnListener.onFailed(ex);
        }
    }

	public ContentValues newAppCheckup(String applicationName)
			throws Exception {
		ContentValues data = null;
		_fields = new String[] { _dbName_APP_NAME, _dbName_APP_PATH, _dbName_APP_VERSION };

		_appName = applicationName;
		_expectResult = true;

		QueryServer();
		if (_outputData != null) {
			data = new ContentValues();
			int i = _outputData.length;
			if (i > 0) {
				String appName = _outputData[0][0];
				String path = _outputData[0][1];
				String version = _outputData[0][2];
				data.put("AppName", appName);
				data.put("Path", path);
				data.put("Version", version);
			}
		}
		return data;
	}

	private void QueryServer() throws Exception {
		int maxRetry = 3;
		int retryCount = 0;
		boolean retry = true;
		while (retry) {
			try {
				retry = false;
				QueryServer1();
			} catch (EOFException ex1) {
                Timber.e(ex1,"Error while QueryServer1 ");
				if (_out != null) {
					_out.close();
					_out = null;
				}
				if (_httpConn != null) {
					_httpConn.disconnect();
					_httpConn = null;
				}
				break;
			} catch (IOException ex2) {
                Timber.e(ex2,"Error while QueryServer2 ");
				// check if it's eof, if yes retrieve code again
				if (_out != null) {
					_out.close();
					_out = null;
				}
				if (_httpConn != null) {
					_httpConn.disconnect();
					_httpConn = null;
				}

				if (-1 != ex2.getMessage().indexOf("EOF")) {
					retry = true;
				}

				if (retryCount < maxRetry) {
					retry = true;
				} else {
					retry = false;
					throw ex2;
				}
			} catch (Exception ex3) {
                Timber.e(ex3,"Error while QueryServer3 ");
				if (_out != null) {
					_out.close();
					_out = null;
				}
				if (_httpConn != null) {
					_httpConn.disconnect();
					_httpConn = null;
				}

				if (retryCount < maxRetry) {
					retry = true;
				} else {
					retry = false;
					throw ex3;
				}
			} finally {
				if (_out != null) {
					try {
						_out.close();
					} catch (IOException e) {

					}
					_out = null;
				}

				if (_httpConn != null) {
					_httpConn.disconnect();
					_httpConn = null;
				}
			}
			retryCount++;
		}
	}

	private void QueryServer1() throws Exception {
		String content = null;
		InputStream input = null;
		// post data to send
		StringBuffer dataOUT = new StringBuffer();
		// createPOST(dataOUT, "query", query);
		createPOST(dataOUT, "returnResult", "" + _expectResult);
		createPOST(dataOUT, "username", _dbUserId);
		createPOST(dataOUT, "password", _dbPassword);
		createPOST(dataOUT, "cmUsrName", _btalkUserName);

    	createPOST(dataOUT, "ReqAppVer", "true");
		createPOST(dataOUT, "appName", _appName);

		try {
			// System.out.println(dataOUT.toString());
			String charset = "ISO-8859-1"; // "UTF-8";

			// Create a URL for the desired page
			URL url = new URL(_url);

			// Read all the text returned by the server
			_httpConn = (HttpURLConnection) url.openConnection();
			if (_httpConn == null) {
				throw new Exception("No connection found.");
			} else {
				// send data by POST
				_httpConn.setDoOutput(true);
				if (_expectResult) {
					_httpConn.setDoInput(true);
				}
				_httpConn.setChunkedStreamingMode(0);
				_httpConn.setConnectTimeout(15000);
				_httpConn.setReadTimeout(15000);
				_httpConn.setRequestProperty("Accept-Charset", charset);
				_httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
				_httpConn.setReadTimeout(15000);
				
				_out = new BufferedOutputStream(_httpConn.getOutputStream());
				String s1 = dataOUT.toString();
				_out.write(s1.getBytes(charset));
				_out.flush();
				_out.close();
				_out = null;
				int status = -1;
				// read data
				// status = _httpConn.getResponseCode();

				try {
					status = _httpConn.getResponseCode();
				} catch (EOFException ex1) {
					try {
						status = _httpConn.getResponseCode();
					} catch (IOException ex2) {
						throw ex2;
					}
				} catch (IOException ex1) {
					// check if it's eof, if yes retrieve code again
					if (-1 != ex1.getMessage().indexOf("EOF")) {
						try {
							status = _httpConn.getResponseCode();
						} catch (IOException ex2) {
							throw ex2;
						}
					} else {
						throw ex1;
					}
				}

				if (status == HttpURLConnection.HTTP_OK) {
					if (_expectResult) {
						input = new BufferedInputStream(_httpConn.getInputStream());

						byte[] data = new byte[256];
						int len;

						StringBuffer raw = new StringBuffer();
						while (-1 != (len = input.read(data))) {
							raw.append(new String(data, 0, len));
						}
						content = raw.toString();
						input.close();
						input = null;
					}
				} else {
					if (_out != null) {
						_out.close();
						_out = null;
					}
					if (_httpConn != null) {
						_httpConn.disconnect();
						_httpConn = null;
					}
					// Dialog.alert("HTML Failure: Status=" + status);
					throw (new Exception("HTML Failure: Status=" + status));
				}
			}
			if (_out != null) {
				_out.close();
				_out = null;
			}
			if (_httpConn != null) {
				_httpConn.disconnect();
				_httpConn = null;
			}

			// Do stuff here with content
			if ((content != null) && (_fields != null)) {
				Vector<String[]> data = parseContent(content, _fields);
				if ((data.size() > 0) && (_fields.length > 0)) {
					_outputData = new String[data.size()][_fields.length];
					for (int i = 0; i < data.size(); i++) {
						String[] datapoint = (String[]) data.elementAt(i);

						for (int j = 0; j < _fields.length; j++) {
							_outputData[i][j] = datapoint[j];
						}
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (_out != null) {
				_out.close();
				_out = null;
			}
			if (input != null) {
				input.close();
				input = null;
			}
			if (_httpConn != null) {
				_httpConn.disconnect();
				_httpConn = null;
			}
		}
	}

	/**
	 * Fills the buffer for POST. Note: key and value cannot have = or & in them
	 * 
	 * @param buffer
	 * @param key
	 * @param value
	 */
	private void createPOST(StringBuffer buffer, String key, String value) {
		if (buffer.length() != 0)
			buffer.append("&");
		buffer.append(key);
		buffer.append("=");
		buffer.append(value);
	}

	/**
	 * Parses what is returned from the PHP call into a vector This function has
	 * very little error checking built in and will not work in some cases (e.g.
	 * multi line data)
	 * 
	 * @param data
	 *            from PHP call
	 * @param fields
	 *            - fields to put in vector THIS MUST BE IN THE ORDER OF THE
	 *            ARRAY RETURNED
	 * @return Vector of String[] where String[0] corresponds to fields[0]
	 */
	private Vector<String[]> parseContent(String data, String[] fields) {
		Vector<String[]> parsedData = new Vector<>();
		String[] row = new String[fields.length];
		int currPos = 0;
		int fieldPos = 0;

		currPos = data.indexOf("[" + fields[fieldPos] + "] => ", currPos);
		while (currPos != -1) {
			int endPos;
			// set currPos to where data is by skipping "[fieldname] => "
			currPos += fields[fieldPos].length() + 6;
			endPos = data.indexOf('\n', currPos);
			row[fieldPos] = data.substring(currPos, endPos);
			fieldPos++;

			if (fieldPos >= fields.length) {
				fieldPos = 0;
				parsedData.addElement(row);
				row = new String[fields.length];
			}
			currPos = data.indexOf("[" + fields[fieldPos] + "] => ", currPos);
		}
		return parsedData;
	}
}
