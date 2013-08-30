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

import com.fhhst.prodroid.gui.MainActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class Printer {
	private final String TAG = "prodroidPrinter";
	private MainActivity lMainActivity;

	private Printcore lPrintcore;

	private boolean connected;
	private boolean online;

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	private int mbaudrate;
	private int mdatabits;
	private int mstopbits;
	private int mparitybits;

	/**
	 * The device currently in use, or {@code null}.
	 */
	private UsbSerialDriver mSerialDevice;

	/**
	 * The system's USB service.
	 */
	private UsbManager lUsbManager;

	private final ExecutorService mExecutor = Executors
			.newSingleThreadExecutor();

	private SerialInputOutputManager mSerialIoManager;

	private final SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {

		@Override
		public void onRunError(Exception e) {
			Log.d(TAG, "Runner stopped.");
		}

		@Override
		public void onNewData(final byte[] data) {
			lMainActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					lPrintcore.updateReceivedData(data);

				}
			});
		}
	};

	public Printer(Printcore aPrintcore, MainActivity aMainActivity) {
		lPrintcore = aPrintcore;
		lMainActivity = aMainActivity;
		initUSBInterface();

		// if (mSerialDevice == null) {
		// lMainActivity.alertUser("No Printer found",
		// "Please connect a compatible serial device.");
		// lMainActivity.updateTitleView("No serial device.");}
		// }
	}

	public void initUSBInterface() {
		lUsbManager = (UsbManager) lMainActivity
				.getSystemService(Context.USB_SERVICE);
	}

	public void reset() {

		if (mSerialDevice != null) {
			try {
				mSerialDevice.setDTR(true);

				mSerialDevice.setDTR(false);
			} catch (IOException e) {

				e.printStackTrace();
			}
		}

	}

	public void pause() { // maybe we dont pause here. a service in the
							// background is preferred
		stopIoManager();
		if (mSerialDevice != null) {
			try {
				mSerialDevice.close();

				mSerialDevice = null;

			} catch (IOException e) {

				e.printStackTrace();
			}
		}
	}

	public void resume() {
		lUsbManager = (UsbManager) lMainActivity
				.getSystemService(Context.USB_SERVICE);
		mSerialDevice = UsbSerialProber.findFirstDevice(lUsbManager); // Acquire
																		// TODO:Test
																		// connection

		Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
		if (mSerialDevice == null) {
			// lMainActivity.alertUser("No Printer found",
			// "Please connect a compatible serial device.");
			lMainActivity.showMessage("No serial device.");
			lMainActivity.updateDriverInfo("No serial device.");
		} else {
			try {

				mSerialDevice.setParameters(mbaudrate, mdatabits, mstopbits,
						mparitybits);// parameters for serial communication
				mSerialDevice.open();

			} catch (IOException e) {
				Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
				lMainActivity.showMessage("Error opening device: "
						+ e.getMessage());
				try {
					mSerialDevice.close();
				} catch (IOException e2) {
					// Ignore.
				}
				mSerialDevice = null;
				return;
			}

			lMainActivity.updateDriverInfo(mSerialDevice + "");
			connected = true;
		}

		onDeviceStateChange();
	}

	private void onDeviceStateChange() {

		stopIoManager();
		startIoManager();
	}

	private void stopIoManager() {
		if (mSerialIoManager != null) {
			Log.i(TAG, "Stopping io manager ..");
			mSerialIoManager.stop();
			mSerialIoManager = null;
		}
	}

	private void startIoManager() {
		if (mSerialDevice != null) {
			Log.i(TAG, "Starting io manager ..");
			mSerialIoManager = new SerialInputOutputManager(mSerialDevice,
					mListener);
			mExecutor.submit(mSerialIoManager);
		}
	}

	public boolean send(byte[] src) {
		boolean rcv = false;
		int sentBytes = 0;

		if (mSerialDevice != null) {
			try {
				sentBytes = mSerialDevice.write(src, 20);

				if (sentBytes == src.length) {
					rcv = true;
				}
			} catch (IOException e) {
				Log.d(TAG, e.getMessage());
				e.printStackTrace();
			}
		}
		return rcv;
	}

	public boolean isOnline() {
		return online;
	}

	public void setOnline(boolean online) {
		this.online = online;
		lMainActivity.updateMenuTitles();
	}

	/**
	 * @return the paritybits
	 */
	public int getParitybits() {
		return mparitybits;
	}

	/**
	 * @param paritybits
	 *            the paritybits to set
	 */
	public void setParitybits(int paritybits) {
		mparitybits = paritybits;
	}

	/**
	 * @return the baudrate
	 */
	public int getBaudrate() {
		return mbaudrate;
	}

	/**
	 * @param baudrate
	 *            the baudrate to set
	 */
	public void setBaudrate(int baudrate) {
		mbaudrate = baudrate;
	}

	/**
	 * @return the databit
	 */
	public int getDatabit() {
		return mdatabits;
	}

	/**
	 * @param databit
	 *            the databit to set
	 */
	public void setDatabit(int databit) {
		mdatabits = databit;
	}

	/**
	 * @return the stopbits
	 */
	public int getStopbits() {
		return mstopbits;
	}

	/**
	 * @param stopbits
	 *            the stopbits to set
	 */
	public void setStopbits(int stopbits) {
		mstopbits = stopbits;
	}

}
