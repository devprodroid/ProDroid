/*
 * Copyright (C) 2013 Robert Bremer
 * 
 * Licensed under the GNU General Public License v3
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Robert Bremer

 */
package com.fhhst.prodroid.printer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.fhhst.prodroid.R;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fhhst.prodroid.gui.MainActivity;
import com.fhhst.prodroid.helper.GcodeLine;

/**
 * Printcore Implementation for Android Uses a Printerclass, which is used as
 * Universal interface to a PRotos Printer
 * 
 * Uses UsbSerialLibrary, an Android USB host serial driver library for CDC,
 * FTDI, Arduino and other devices written by mike wakerly (opensource@hoho.com)
 * 
 * @author Robert Bremer 2013
 */
@SuppressLint("DefaultLocale")
public class Printcore {

	ConcurrentLinkedQueue<String> Buffer = new ConcurrentLinkedQueue<String>();
	Iterator<String> itrBuffer = Buffer.iterator();

	public LinkedList<GcodeLine> sentlines;

	RandomAccessFile myFile = null; // printer
	private long lastReadByte = 0;

	String nextLine = null;

	private float temperature;
	private float temperature_target;

	public void setTemperature(float temperature) {
		this.temperature = temperature;
	}

	public void setBedtemperature(float bedtemperature) {
		this.bedtemperature = bedtemperature;
	}

	public float getTemperature() {
		return temperature;
	}

	public float getBedtemperature() {
		return bedtemperature;
	}

	private float bedtemperature;
	private float bedtemperature_target;

	private int selectedTemp;

	public int getSelectedTemp() {
		return selectedTemp;
	}

	public void setSelectedTemp(int selectedTemp) {
		this.selectedTemp = selectedTemp;
	}

	public int getSelectedBedTemp() {
		return selectedBedTemp;
	}

	public void setSelectedBedTemp(int selectedBedTemp) {
		this.selectedBedTemp = selectedBedTemp;
	}

	private int selectedBedTemp;

	/**
	 * current line number
	 */
	private int lineno;

	private long mfileLength;
	private int progress = 0;

	/**
	 * Linenumber to resend from in case of transmission error
	 */
	private int resendfrom;

	/**
	 * list of received lines
	 */
	private List<String> recvlines = new ArrayList<String>();
	/**
	 * Buffer of incomplete received lines
	 */
	private List<String> bufferedlines = new ArrayList<String>();

	private MainActivity lMainActivity;

	private Printer lPrinter;
	private Thread addThread = null;

	private boolean printing;
	private boolean pause;

	private NotificationManager mNotificationManager;

	/**
	 * Handler for establishing connection
	 */
	Handler connectHandler = null;
	/**
	 * Handler for temperature monitoring
	 */
	Handler monitorHandler = null;
	/**
	 * Count number of connection attempts (Send M105)
	 */
	private int setOnlineCounter;

	private boolean debugMode;

	/**
	 * 
	 * @param aMainActivity
	 *            Parent activity with GUI
	 */
	public Printcore(MainActivity aMainActivity) {
		lMainActivity = aMainActivity;

		debugMode = lMainActivity.sharedPref.getBoolean("pref_comm_debug",
				false);

		lineno = -1;

		resendfrom = -1;
		setOnlineCounter = -1;

		sentlines = new LinkedList<GcodeLine>();

		Buffer.clear();

		lPrinter = new Printer(this, lMainActivity);
		lPrinter.setOnline(false);
		mNotificationManager = (NotificationManager) lMainActivity
				.getSystemService(lMainActivity.NOTIFICATION_SERVICE);
	}

	public void connect() {

		setPrinterProperties();
		if (lPrinter.isConnected()) {
			disconnect();
		}
		lPrinter.reset();
		enableListen();

	}

	public void disconnect() {

	}

	public boolean isOnline() {
		return lPrinter.isOnline();
	}

	/*
	 * Pause from Application lifecycle
	 */
	public void pause() {
		lPrinter.pause();
		pausePrint();
	}

	/**
	 * resume connection to printer
	 */
	public void resume() {

		setPrinterProperties();

		lPrinter.resume();
	}

	/**
	 * Set communication properties
	 */
	private void setPrinterProperties() {
		lPrinter.setBaudrate(Integer.parseInt(lMainActivity.sharedPref
				.getString("pref_comm_baudrateselect", "115200")));
		lPrinter.setDatabit(Integer.parseInt(lMainActivity.sharedPref
				.getString("pref_comm_databitsselect", "8")));
		lPrinter.setStopbits(Integer.parseInt(lMainActivity.sharedPref
				.getString("pref_comm_stopbitsselect", "1")));
		lPrinter.setParitybits(Integer.parseInt(lMainActivity.sharedPref
				.getString("pref_comm_paritybitselect", "0")));
	}

	/**
	 * Reset printer and prepare everything for restart
	 */
	public void reset() {
		lPrinter.setOnline(false);
		lPrinter.reset();

	}

	public void printtest() {

	}

	/**
	 * Enable the reading and decoding of messages from the printer
	 */
	public void enableListen() {

		if (lPrinter.isConnected()) {
			if (!lPrinter.isOnline()) {
				setOnlineCounter = 0;

				connectHandler = new Handler();
				connectHandler.postDelayed(connectRunnable, 0);
			}
		}
		// go to the main activity and send a M105
	}

	/**
	 * runnable for connection attemps
	 */
	private Runnable connectRunnable = new Runnable() {
		@Override
		public void run() {
			if ((!lPrinter.isOnline()) && (setOnlineCounter < 6)) {
				send("M105", 0, false);

				setOnlineCounter++;
				if (setOnlineCounter >= 6) {
					connectHandler.removeCallbacks(connectRunnable);
					lMainActivity.showMessage(lMainActivity
							.getString(R.string.connection_to_printer_failed_));
					lMainActivity
							.alertUser(
									lMainActivity
											.getString(R.string.connection_to_printer_failed_),
									lMainActivity
											.getString(R.string.please_check_the_connectionsettings_and_make_sure_baudrate_is_correct_));
				}
				// Start runnable again after wait time
				connectHandler.postDelayed(this, 1000);
			}
		}
	};

	public void enableMonitoring() {
		if (isOnline()) {
			monitorHandler = new Handler();
			monitorHandler.postDelayed(monitorRunnable, 0);
		}
	}

	public void disableMonitoring() {
		if (monitorHandler != null)
			monitorHandler.removeCallbacks(monitorRunnable);
	}

	/**
	 * runnable for connection attemps
	 */
	private Runnable monitorRunnable = new Runnable() {
		@Override
		public void run() {
			if (lPrinter.isOnline()) {
				send("M105", 0, false);
			}

			// Start runnable again after wait time
			connectHandler.postDelayed(this, 5000);

		}
	};

	/**
	 * Send Lines to the Printer
	 * 
	 * @param acommand
	 *            Command as String
	 * @param lineno
	 *            last Linenumber sent to the printer
	 * @param calcchecksum
	 *            Calc Checksum with checksum()
	 */
	private void send(String acommand, int lineno, boolean calcchecksum) {
		String lCommand = null;

		if ((acommand != null) && (acommand.isEmpty()))
			throw new IllegalArgumentException("Command cannot be empty");

		if (calcchecksum) { // looks like we are printing
			String sPrefix = "N" + lineno + " " + acommand;

			lCommand = sPrefix + "*" + checksum(sPrefix.toCharArray());

			sentlines.add(new GcodeLine(lineno, lCommand));

			lCommand = lCommand + "\n";

			lPrinter.send(lCommand.getBytes());
			// lMainActivity.showMessage("SENT: " + lCommand);

			// remove oldest element if capacity is reached
			if (sentlines.size() > 100)
				sentlines.remove();
		} else
			sendnow(acommand); // send without checksum or linenumber

	}

	/**
	 * Send Lines without linenumber and checksum to printer Command will not be
	 * logged in sentlines list
	 * 
	 * @param acommand
	 *            Command as String
	 */
	private void sendnow(String acommand) {
		String lCommand;
		lCommand = acommand + "\n";
		lMainActivity.showMessage("SENT: " + lCommand);
		lPrinter.send(lCommand.getBytes());
	}

	/**
	 * @param data
	 *            array of received bytes
	 */
	/**
	 * Parse and format data from the printer
	 * 
	 * @param data
	 */
	void updateReceivedData(byte[] data) {
		long startTime = 0;
		if (debugMode)
			startTime = System.nanoTime();

		String message = null;
		try {
			message = new String(data, "UTF8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // for UTF8 encoding

		// Split lines by lineBreak
		// if no linebreak at the end of the message is found, put it on a stack
		// and read the next message
		// TODO: Timeout for the following message
		if (message.charAt(message.length() - 1) == '\n') {
			if (bufferedlines.isEmpty()) {
				String[] splitMessage = message.split("[\\r\\n]+");
				for (int i = 0; i < splitMessage.length; i++) {
					recvlines.add(splitMessage[i]);
				}
				try {
					readlines();
				} catch (IllegalArgumentException e) {
					;
				}
			} else {
				bufferedlines.add(message);

				StringBuilder builder = new StringBuilder();
				for (String value : bufferedlines) {
					builder.append(value);
				}
				String newmessage = builder.toString();

				String[] splitMessage = newmessage.split("[\\r\\n]+");
				for (int i = 0; i < splitMessage.length; i++) {
					recvlines.add(splitMessage[i]);
				}
				bufferedlines.clear();
				if (debugMode) {
					lMainActivity.showMessage(recvlines.toString());
				}
				readlines();
			}
		} else { // message has no line break
			bufferedlines.add(message);
		}
		if (debugMode) {
			long stopTime = System.nanoTime();
			long elapsedTime = (stopTime - startTime) / 1000000;

			if (elapsedTime > 10)
				Log.d("PERFORMANCE", message + " took " + elapsedTime
						+ " ms to parse");
		}
	}

	/**
	 * Read formated lines from printer
	 * 
	 */
	private void readlines() {

		if (recvlines.isEmpty()) {
			throw new IllegalArgumentException(
					lMainActivity.getString(R.string.input_cannot_be_empty));
		}

		if (lPrinter.isConnected()) {
			if (!lPrinter.isOnline()) { // printer offline
				checkAndSetOnline();

			} else { // Online
				if (isPrinting()) {
					printListener();
				} else {
					String lLine;

					for (int i = 0; i < recvlines.size(); i++) {
						lLine = recvlines.get(i) + "\n";

						if (lLine.length() <= 2)
							continue; // Single characters will be ignored here

						if (debugMode)
							Log.d("PERFORMANCE", "RECV: " + lLine);
						// Grab Temperatures
						parseTemperatures(lLine);

					}
				}
			}

		}

		recvlines.clear();
	}

	/**
	 * Set all parameters for print and start Addthread
	 * 
	 * @param aFileName
	 *            path to the gcode file
	 * @param afileLength
	 *            Size of the file for progress calculations
	 * @param startpos
	 *            startposition for resuming
	 * @return true if start was successful
	 */
	public boolean startPrint(String aFileName, long afileLength, long startpos) {

		if (printerReadyForCommand() && (!isPrinting())) {
			mfileLength = afileLength;
			openInputFile(aFileName);

			addThread = new Thread(new AddThread());
			addThread.start();

			send("M110", -1, true); // Set Current Linenumber
			setPrinting(true);
			setPause(false);

			sentlines.clear();
			lineno = -1;
			resendfrom = -1;

			return true;
		} else
			return false;
	}

	/**
	 * Opens a File from the local filesystem
	 * 
	 * @param aFileName
	 *            Path to the local file
	 */
	private void openInputFile(String aFileName) {

		try {
			myFile = new RandomAccessFile(aFileName, "r");

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// int readLines = -1;

	}

	/**
	 * react on answers from the printer while printing
	 */
	@SuppressLint("DefaultLocale")
	private void printListener() {
		String lLine;
		for (int i = 0; i < recvlines.size(); i++) {
			lLine = recvlines.get(i) + "\n";

			if (lLine.length() <= 2)
				continue; // Single characters will be ignored here

			// lMainActivity.showMessage("RECV: " + lLine);
			// Grab Temperatures
			parseTemperatures(lLine);
			if (lLine.startsWith("ok")) {

				sendnext();
				lMainActivity.showProgress(progress);
				break;

			} else if (lLine.startsWith("Error:")) {
				// Panic, next line should be resend or rs
			}

			else if ((lLine.toLowerCase().startsWith("resend:"))
					|| (lLine.startsWith("rs:"))) {

				resendfrom = parseResendLine(lLine);
				sendnext();
				break;

			}
		}
	}

	/**
	 * 
	 * @param lLine
	 *            A line starting with resend: or rs: containing the line number
	 *            to start resend
	 */
	private int parseResendLine(String lLine) {
		int lresendfrom = -1;
		lLine = lLine.replace("N:", " ").replace("N", " ").replace(":", " ");

		String[] linewords = lLine.split(" ");
		for (int i1 = 0; (i1 < linewords.length); i1++) {
			try {
				lresendfrom = Integer.parseInt(linewords[i1].trim());
			} catch (NumberFormatException e) {
				//
			}

		}
		return lresendfrom;
	}

	private void sendnext() {
		String tmpLine;
		// TODO Auto-generated method stub
		if (resendfrom == -1) {
			lineno++;
			tmpLine = Buffer.poll();
			if (tmpLine != null)
				send(tmpLine, lineno, true);

			else {

				stopPrint();
			}
		}

		if ((resendfrom > -1) && (resendfrom <= lineno)) {
			tmpLine = findLineToResend(resendfrom).command;

			if (tmpLine != null)
				sendnow(tmpLine);
			resendfrom++;
		}
	}

	private boolean printerReadyForCommand() {
		if (lPrinter.isOnline()) {
			return true;
		} else {
			lMainActivity.showMessage(lMainActivity
					.getString(R.string.cant_send_printer_not_online));
			return false;
		}
	}

	/**
	 * Check if printer is online and search for ok pattern in recv data
	 */
	private void checkAndSetOnline() {

		for (int i = 0; i < recvlines.size(); i++) {
			String tmp = recvlines.get(i) + "\n";
			lMainActivity.showMessage(tmp);
			parseTemperatures(tmp);
			if ((recvlines.get(i).startsWith("ok"))
					|| (recvlines.get(i).startsWith("start") || (recvlines
							.get(i).startsWith("Grbl")))) {
				lPrinter.setOnline(true);
				lMainActivity.updateTitleView(lMainActivity
						.getString(R.string.printer_is_online_)
						+ recvlines.get(i) + "\n");

				break;

			}
		}
	}

	/**
	 * Parse a recieved line for Heater and Bed Temperatures. This is critical
	 * to protocol changes, but should be robust in this form
	 * 
	 * @param aLine
	 */
	private void parseTemperatures(String aLine) {
		String[] splitLine = aLine.split(" ");
		try {
			if ((aLine.startsWith("ok"))
					&& ((aLine.contains("T:") || (aLine.contains("B:"))))) {

				for (int i = 0; i < splitLine.length; i++) {
					if (splitLine[i].startsWith("T:")) {
						setTemperature(Float.parseFloat(splitLine[i]
								.substring(2)));
						if (splitLine[i + 1].startsWith("/"))
							setTemperature_target(Float
									.parseFloat(splitLine[i + 1].substring(1)));

					}

					if (splitLine[i].startsWith("B:")) {
						setBedtemperature(Float.parseFloat(splitLine[i]
								.substring(2)));
						if (splitLine[i + 1].startsWith("/"))
							setBedtemperature_target(Float
									.parseFloat(splitLine[i + 1].substring(1)));
					}
				}
			}
		} catch (Exception e) {
			Log.e("Parse Error", "Parser Error in Line: " + aLine + ".");
		}

		lMainActivity.notifyNewTemperatures();

	}

	/**
	 * @param aTemp
	 *            Temperature of the Hotend, 0 for off
	 */
	public boolean changeHeatTemp(int aTemp) { // do_settemp

		if ((aTemp >= 0) && printerReadyForCommand()) {
			lMainActivity.showMessage(lMainActivity
					.getString(R.string.setting_hotend_temperature_to_)
					+ aTemp
					+ lMainActivity.getString(R.string._degree_celsius_));

			send("M104 S" + aTemp, 0, false);
			return true;
		} else
			return false;

	}

	/**
	 * @param aTemp
	 *            Temperature of the Bed, 0 for off
	 */
	public boolean changeBedTemp(int aTemp) { // do_bedtemp
		if ((aTemp >= 0) && printerReadyForCommand()) {
			lMainActivity.showMessage(lMainActivity
					.getString(R.string.setting_bed_temperature_to_)
					+ aTemp
					+ " degrees Celsius.\n");

			send("M140 S" + aTemp, 0, false);
			return true;
		} else
			return false;
	}

	public boolean extrude() {
		if (printerReadyForCommand()) {

		}
		return true;

	}

	/**
	 * @param cmd
	 *            a N + Line Number
	 * @return checksum of a command according to http://reprap.org/wiki/G-code
	 */
	public int checksum(char[] cmd) {
		int cs = 0;
		for (int i = 0; ((i < cmd.length) && (cmd[i] != '*')); i++)
			cs = cs ^ cmd[i];
		cs &= 0xff;
		return cs;
	}

	/**
	 * finds the GCode Line with the given linenumber in Sentlines
	 * 
	 * @param aLineNumber
	 * @return GcodeLine with the given index
	 */
	public GcodeLine findLineToResend(long aLineNumber) {
		for (GcodeLine e : sentlines)
			if (e.linenumber == aLineNumber)
				return e;
		return null;
	}

	/**
	 * In pauseprint we close and pause everything including: printing false
	 * save file pointer and current buffercontent * close inputfile cancel
	 * thread and pause printer
	 */
	public void pausePrint() {
		// TODO Auto-generated method stub

		// Alles pausieren:

		setPrinting(false);
		setPause(true);

		// stopPrint();

	}

	/**
	 * @return the printing
	 */
	public boolean isPrinting() {
		return printing;
	}

	/**
	 * @param printing
	 *            the printing to set
	 */
	private void setPrinting(boolean printing) {
		this.printing = printing;
		if (!printing) {

			// stopPrint();

		}
	}

	/**
	 * 
	 */

	private void stopPrint() {

		showFinishNotification();

		sentlines.clear();
		lineno = -1;
		resendfrom = -1;
		setPrinting(false);
		homeAxis('X');
		homeAxis('Y');

		addThread = null;

		lMainActivity.showProgress(R.string.print_finished_);
		lMainActivity.updateInterface();
	}

	/**
	 * notifies the the User of the Printing Success
	 */
	@SuppressWarnings("deprecation")
	private void showFinishNotification() {
		Intent i = new Intent(lMainActivity, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(lMainActivity,
				0, i, 0);

		Notification notif = new Notification(R.drawable.icon,
				"Print Finished", System.currentTimeMillis());
		notif.setLatestEventInfo(lMainActivity, "Printerinfo",
				"Print Finished", contentIntent);
		notif.flags = Notification.FLAG_AUTO_CANCEL
				| Notification.FLAG_SHOW_LIGHTS;

		mNotificationManager.notify(1234, notif);

	}

	public boolean executeMove(char aAxis, double aDistance, double feedrate) {

		if (isPrinterReadyforManualCommands()) {

			aAxis = Character.toUpperCase(aAxis);

			if ((aAxis == 'X') || (aAxis == 'Y') || (aAxis == 'Z')
					|| (aAxis == 'E')) {
				sendnow("G91");
				sendnow("G1 " + aAxis + aDistance + " F" + (int) feedrate);
				sendnow("G90");
				sendnow("M114");
			}

			return true;
		} else
			return false;
	}

	public boolean homeAxis(char aAxis) {
		if (isPrinterReadyforManualCommands()) {

			aAxis = Character.toUpperCase(aAxis);

			switch (aAxis) {
			case 'X':
				sendnow("G28 X0");
				break;
			case 'Y':
				sendnow("G28 Y0");
				break;
			case 'Z':
				sendnow("G28 Z0");
				break;
			case 'E':
				sendnow("G92 E0");
				break;

			default:
				sendnow("G28");
				sendnow("G92 E0");
				break;
			}

			return true;

		} else
			return false;
	}

	/**
	 * @return printer is online and not printing
	 * 
	 */
	private boolean isPrinterReadyforManualCommands() {
		if (isPrinting()) {
			lMainActivity
					.showMessage(lMainActivity
							.getString(R.string.printer_is_currently_printing_please_pause_the_print_before_you_issue_manual_commands_));
			return false;
		}
		if (!isOnline()) {
			lMainActivity.showMessage(lMainActivity
					.getString(R.string.printer_is_offline_unable_to_move_));
			return false;
		}
		return true;
	}

	/**
	 * @return the pause
	 */
	public boolean isPause() {
		return pause;
	}

	/**
	 * @param pause
	 *            the pause to set
	 */
	public void setPause(boolean pause) {
		this.pause = pause;
	}

	public long getLastReadByte() {
		// TODO Auto-generated method stub
		return lastReadByte;
	}

	public void setLastReadByte(long alastReadByte) {
		// TODO Auto-generated method stub
		lastReadByte = alastReadByte;
	}

	public float getTemperature_target() {
		return temperature_target;
	}

	public void setTemperature_target(float temperature_target) {
		this.temperature_target = temperature_target;
	}

	public float getBedtemperature_target() {
		return bedtemperature_target;
	}

	public void setBedtemperature_target(float bedtemperature_target) {
		this.bedtemperature_target = bedtemperature_target;
	}

	class AddThread implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			String aDataRow;
			String[] tmpDataRow;
			Buffer.clear();

			boolean goAhead = true;

			if (myFile != null)
				try {
					while (goAhead) {
						if (Buffer.size() < 200) {
							if ((aDataRow = myFile.readLine()) == null) {
								goAhead = false;
								// stopPrint();

								break;
							}
							tmpDataRow = aDataRow.split(";");
							if (!(tmpDataRow[0].trim().isEmpty())) {
								Buffer.add(tmpDataRow[0]);

							}
							lastReadByte = myFile.getFilePointer();
							progress = (int) ((lastReadByte * 100) / mfileLength);
							// Log.e("PROGRESS", "READ " + progress + "%");

						}

					}

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

		}
	}
}
