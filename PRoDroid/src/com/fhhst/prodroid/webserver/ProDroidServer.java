package com.fhhst.prodroid.webserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import android.annotation.SuppressLint;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.fhhst.prodroid.gui.MainActivity;

public class ProDroidServer {

	private MainActivity lMainActivity;
	private Server server;

	String filesDir;

	@SuppressLint("HandlerLeak")
	final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			log(b.getString("msg"));
		}
	};
	public int printProgress;

	public ProDroidServer(MainActivity aMainActivity) {
		lMainActivity = aMainActivity;
		filesDir = lMainActivity.getFilesDir().getPath().toString();

		try {

			(new File(filesDir)).mkdir();
			BufferedWriter bout = new BufferedWriter(new FileWriter(filesDir
					+ "/index.html"));
			bout.write("<html><head><title>Android Webserver powered by bolutions.com</title>");
			bout.write("</head>");
			bout.write("<body>Willkommen auf dem Android Webserver powered by <a href=\"http://www.bolutions.com\">bolutions.com</a>.");
			bout.write("<br><br>Die HTML-Dateien liegen in /sdcard/com.bolutions.webserver/</body></html>");
			bout.flush();
			bout.close();
			bout = new BufferedWriter(new FileWriter(filesDir + "/403.html"));
			bout.write("<html><head><title>Error 403 powered by bolutions.com</title>");
			bout.write("</head>");
			bout.write("<body>403 - Forbidden</body></html>");
			bout.flush();
			bout.close();
			bout = new BufferedWriter(new FileWriter(filesDir + "/404.html"));
			bout.write("<html><head><title>Error 404 powered by bolutions.com</title>");
			bout.write("</head>");
			bout.write("<body>404 - File not found</body></html>");
			bout.flush();
			bout.close();

		} catch (Exception e) {
			Log.v("ERROR", e.getMessage());
		}

	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
			server.interrupt();
			log("Server was killed.");

		} else {
			log("Cannot kill server!? Please restart your phone.");
		}

	}

	public static void log(String s) {
		Log.i("Webserver", s);

	}

	public static String intToIp(int i) {
		return ((i) & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
				+ ((i >> 16) & 0xFF) + "." + (i >> 24 & 0xFF);
	}

	public void startServer(int port) {
		try {
			WifiManager wifiManager = (WifiManager) lMainActivity
					.getSystemService(lMainActivity.WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();

			String ipAddress = intToIp(wifiInfo.getIpAddress());

			if ((wifiInfo.getSupplicantState() != SupplicantState.COMPLETED)
					|| ipAddress.contentEquals("0.0.0.0")) {
				// new AlertDialog.Builder(lMainActivity)
				// .setTitle("Error")
				// .setMessage(
				// "Please connect to a WIFI-network for starting the webserver.")
				// .setPositiveButton("OK", null).show();
				throw new Exception(
						"Please connect to a WIFI-network. WebServer not started.");
			}

			server = new Server(ipAddress, port, mHandler, filesDir, this);
			server.start();
			lMainActivity.updateServerStatus(true, ipAddress + ":" + port);

		} catch (Exception e) {
			log(e.getMessage());
			lMainActivity.updateServerStatus(false, "");
			lMainActivity.showMessage(e.getLocalizedMessage());

		}

	}

	public void notifyProgress(int aProgress) {
		this.printProgress = aProgress;
		// TODO Auto-generated method stub

	}
}
