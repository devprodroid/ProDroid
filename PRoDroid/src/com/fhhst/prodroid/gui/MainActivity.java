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
package com.fhhst.prodroid.gui;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.dropbox.chooser.android.DbxChooser;
import com.fhhst.prodroid.R;
import com.fhhst.prodroid.printer.Printcore;
import com.fhhst.prodroid.webserver.ProDroidServer;
import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;

/**
 * @author robert
 * 
 */
public class MainActivity extends Activity implements OnItemSelectedListener {

	// Surface and GUI
	private TextView tvDriverInfo;
	private TextView mDumpTextView;
	private ScrollView mScrollView;

	private TextView tvProgress;
	private ToggleButton btnHeat;
	private ToggleButton btnBed;
	private ToggleButton btnMonitor;
	private Button btnStartPrint;
	private Button btnPausePrint;

	// X Y Translation
	private Button btn_switch_distance;
	private Button btn_y_down;
	private Button btn_y_up;
	private Button btn_x_down;
	private Button btn_x_up;
	private ImageButton btn_home_x;
	private ImageButton btn_home_y;

	// Z Translation
	private ImageButton btn_z_home;
	private Button btn_z_down;
	private Button btn_z_up;

	// Extruder
	private Button btnExtrude;
	private Button btnReverse;
	private EditText edtExtrueRate;
	private EditText edtExtrudeDist;

	private Spinner spHeat;
	private Spinner spBed;
	private ProgressBar pbHeat;
	private ProgressBar pbBed;
	private TextView tvHeat;
	private TextView tvBed;

	// Information box
	private TextView tvServerStatus;
	private TextView tvServerAddress;

	private TextView tvFileName;

	private boolean progressOpen;

	private Menu menu;
	public SharedPreferences sharedPref;

	// Progress Dialog
	private ProgressDialog pDialog;
	public static final int progress_bar_type = 2;

	public static final String KEY_progessOpen = "progessOpen";
	public static final String KEY_logoutput = "logoutput";
	public static final String KEY_filepointer = "filepointer";

	// private Printer lPrinter;
	private Printcore lPrintcore;
	ProDroidServer webserv;

	private final String TAG = "prodroid";

	private final int REQUEST_LOAD = 3;
	String filePath = "";
	double currentControlDistance = 1;
	public ProgressDialog dialog;

	/**
	 * Filament to extrude in mm
	 */
	int currentExtrudeDistance = 5;
	/**
	 * Extruderrate in mm/min
	 */
	int currentExtrudeRate = 300;
	long fileLength = 0;
	long resumeFilePointer = -1;

	// Dropbox
	static final int DBX_CHOOSER_REQUEST = 12;
	private static final String APP_KEY = "d4es7dq3pyam4o1";
	private DbxChooser mChooser;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		if (sharedPref == null) {
			throw (new ExceptionInInitializerError());
		}

		linkViews();

		lPrintcore = new Printcore(this);

		addListeners();

		lPrintcore.reset();// reset on printer connect maybe its already there

		btnStartPrint.setEnabled(false);

		updateInterface();
		// updateMenuTitles();
		restoreSavedState(savedInstanceState);
		getWindow().addFlags(
				android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		notifyNewTemperatures();

		// prevent Edittext from getting focus on start
		findViewById(R.id.mainLayout).requestFocus();

		mChooser = new DbxChooser(APP_KEY);
	}

	/**
	 * @param savedInstanceState
	 */
	private void restoreSavedState(Bundle savedInstanceState) {
		// TODO: Save all necessary fields
		if (savedInstanceState != null) {
			progressOpen = savedInstanceState.getBoolean(KEY_progessOpen);
			mDumpTextView.setText(savedInstanceState.getString(KEY_logoutput));
			// File position
			// maybe sentlines list
			// printing etc, maybe the whole printcore class?
			resumeFilePointer = savedInstanceState.getLong(KEY_filepointer);
		}
	}

	/**
	 * Restore state after rotation/restart
	 */
	@Override
	protected void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putBoolean(KEY_progessOpen, progressOpen);
		savedInstanceState.putString(KEY_logoutput, mDumpTextView.getText()
				.toString());

		savedInstanceState.putLong(KEY_filepointer,
				lPrintcore.getLastReadByte());
	}

	/**
	 * Find Views of the GUI elements
	 */
	private void linkViews() {
		// TextViews
		tvDriverInfo = (TextView) findViewById(R.id.tvDriverInfo);
		mDumpTextView = (TextView) findViewById(R.id.demoText);

		tvProgress = (TextView) findViewById(R.id.tvProgress);
		tvHeat = (TextView) findViewById(R.id.tvHeat);
		tvBed = (TextView) findViewById(R.id.tvBed);

		tvServerStatus = (TextView) findViewById(R.id.tvWebserver);
		tvServerAddress = (TextView) findViewById(R.id.tvServerAddress);
		tvFileName = (TextView) findViewById(R.id.tvFileName);

		// ScrollViews
		mScrollView = (ScrollView) findViewById(R.id.demoScroller);

		// TemperatureButtons
		btnHeat = (ToggleButton) findViewById(R.id.btnHeat);
		btnBed = (ToggleButton) findViewById(R.id.btnBed);
		btnMonitor = (ToggleButton) findViewById(R.id.btnMonitor);
		btnStartPrint = (Button) findViewById(R.id.btnStartPrint);
		btnPausePrint = (Button) findViewById(R.id.btnPausePrint);

		// X Y Translation
		btn_switch_distance = (Button) findViewById(R.id.btn_switch_distance);
		btn_y_down = (Button) findViewById(R.id.btn_y_down);
		btn_y_up = (Button) findViewById(R.id.btn_y_up);
		btn_x_down = (Button) findViewById(R.id.btn_x_down);
		btn_x_up = (Button) findViewById(R.id.btn_x_up);

		btn_home_x = (ImageButton) findViewById(R.id.btn_home_X);
		btn_home_y = (ImageButton) findViewById(R.id.btn_home_Y);

		// Z Translation
		btn_z_home = (ImageButton) findViewById(R.id.btn_z_home);
		btn_z_down = (Button) findViewById(R.id.btn_z_down);
		btn_z_up = (Button) findViewById(R.id.btn_z_up);

		btnExtrude = (Button) findViewById(R.id.btnExtrude);
		btnReverse = (Button) findViewById(R.id.btnReverse);

		edtExtrueRate = (EditText) findViewById(R.id.edtExtrudeRate);
		edtExtrudeDist = (EditText) findViewById(R.id.edtExtrudeDist);

		// Spinner
		spHeat = (Spinner) findViewById(R.id.spHeat);
		spHeat.setOnItemSelectedListener(this);

		spBed = (Spinner) findViewById(R.id.spBed);
		spBed.setOnItemSelectedListener(this);

		// Progressbars
		pbHeat = (ProgressBar) findViewById(R.id.pbHeat);
		pbBed = (ProgressBar) findViewById(R.id.pbBed);

	}

	/**
	 * Add onclick and other listeners to the gui elements
	 */
	private void addListeners() {
		btnHeat.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (btnHeat.isChecked()) {
					lPrintcore.changeHeatTemp(lPrintcore.getSelectedTemp());
				} else { // Turn Off
					lPrintcore.changeHeatTemp(0);

				}
			}
		});
		btnBed.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (btnBed.isChecked()) {
					lPrintcore.changeBedTemp(lPrintcore.getSelectedBedTemp());
				} else { // Turn Off
					lPrintcore.changeBedTemp(0);
				}
			}
		});
		btnStartPrint.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				lPrintcore.startPrint(filePath, fileLength, resumeFilePointer);
				updateInterface();
			}

		});
		btnPausePrint.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				updateInterface();
				lPrintcore.pausePrint();
			}

		});

		btnMonitor.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (btnMonitor.isChecked()) {
					lPrintcore.enableMonitoring();
				} else { // Turn Off
					lPrintcore.disableMonitoring();
				}
			}
		});

		OnClickListener controlClickListener = new OnClickListener() {

			@Override
			public void onClick(View v) {

				switch (v.getId()) {
				case R.id.btn_x_up:
					lPrintcore.executeMove('X', currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_xyfeedrate", "300")));
					break;
				case R.id.btn_x_down:
					lPrintcore.executeMove('X', -currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_xyfeedrate", "300")));
					break;
				case R.id.btn_y_up:
					lPrintcore.executeMove('Y', currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_xyfeedrate", "300")));
					break;
				case R.id.btn_y_down:
					lPrintcore.executeMove('Y', -currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_xyfeedrate", "300")));
					break;
				case R.id.btn_home_X:
					lPrintcore.homeAxis('X');
					break;
				case R.id.btn_home_Y:
					lPrintcore.homeAxis('Y');
					break;
				case R.id.btn_switch_distance: {
					if (currentControlDistance >= 100)
						currentControlDistance = 0.1;
					else
						currentControlDistance = currentControlDistance * 10;
					updateInterface();
					break;
				}
				}
			}
		};
		btn_switch_distance.setOnClickListener(controlClickListener);
		btn_x_down.setOnClickListener(controlClickListener);
		btn_x_up.setOnClickListener(controlClickListener);
		btn_y_down.setOnClickListener(controlClickListener);
		btn_y_up.setOnClickListener(controlClickListener);

		btn_home_x.setOnClickListener(controlClickListener);
		btn_home_y.setOnClickListener(controlClickListener);

		OnClickListener zControlListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case R.id.btn_z_up:
					lPrintcore.executeMove('Z', currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_zfeedrate", "300")));
					break;
				case R.id.btn_z_down:
					lPrintcore.executeMove('Z', -currentControlDistance, Float
							.parseFloat(sharedPref.getString(
									"pref_print_zfeedrate", "300")));
					break;
				case R.id.btn_z_home:
					lPrintcore.homeAxis('Z');
					break;
				}

			}
		};

		btn_z_home.setOnClickListener(zControlListener);
		btn_z_down.setOnClickListener(zControlListener);
		btn_z_up.setOnClickListener(zControlListener);

		OnClickListener extruderControlListener = new OnClickListener() {

			@Override
			public void onClick(View v) {

				switch (v.getId()) {
				case R.id.btnExtrude:
					lPrintcore.executeMove('E', currentExtrudeDistance,
							currentExtrudeRate);
					break;
				case R.id.btnReverse:
					lPrintcore.executeMove('E', -currentExtrudeDistance,
							currentExtrudeRate);

					break;
				}

			}
		};

		btnExtrude.setOnClickListener(extruderControlListener);
		btnReverse.setOnClickListener(extruderControlListener);

		edtExtrueRate = (EditText) findViewById(R.id.edtExtrudeRate);

		edtExtrudeDist = (EditText) findViewById(R.id.edtExtrudeDist);

		edtExtrueRate.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {

				try {
					String tmp = edtExtrueRate.getText().toString();
					currentExtrudeRate = Integer.parseInt(tmp);
				} catch (NumberFormatException e) {
					alertUser(
							getString(R.string.invalid_input_),
							getString(R.string.pleace_specify_the_extrude_rate_in_mm_min_default_300_));
					edtExtrueRate.setText(Integer.toString(currentExtrudeRate));
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}
		});
		edtExtrudeDist.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
				try {
					String tmp = edtExtrudeDist.getText().toString();
					currentExtrudeDistance = Integer.parseInt(tmp);
				} catch (NumberFormatException e) {
					alertUser(
							getString(R.string.invalid_input_),
							getString(R.string.pleace_specify_the_extrude_distance_in_mm_default_5_));
					edtExtrudeDist.setText(Integer
							.toString(currentExtrudeDistance));

				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}
		});

	}

	/**
	 * Check parameters of the input file
	 * 
	 * @param lfilePath
	 *            Absolute path to the inputfile
	 * @return
	 * @throws IOException
	 */
	protected boolean checkFile(String lfilePath) throws IOException {
		

		File lFile = new File(lfilePath);
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		try {
			inputStreamReader = new InputStreamReader(
					new FileInputStream(lFile), "UTF8");

			bufferedReader = new BufferedReader(inputStreamReader);

			fileLength = lFile.length();
			String line = null;
			showMessage(lfilePath + "\n");
			int lineCount = 0; // print the first few lines
			while ((lineCount < 16)
					&& (line = bufferedReader.readLine()) != null) {
				showMessage(line);
				lineCount++;

			}
			tvFileName.setText(lFile.getName());
			return true;
		} finally {
			if (inputStreamReader != null)
				inputStreamReader.close();
			if (bufferedReader != null)
				bufferedReader.close();
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		this.menu = menu;
		return true;
	}

	public void updateMenuTitles() {
		if (menu != null) {
			MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
			if (lPrintcore.isOnline()) {
				connectMenuItem.setTitle(R.string.lblBtnDisconnect);

			} else {
				connectMenuItem.setTitle(R.string.lblBtnConnect);
			}
		}
	}

	public void resetInterface() {
		btnHeat.setChecked(false);
		btnBed.setChecked(false);
		mDumpTextView.setText("");
		tvDriverInfo.setText("");

	}

	public boolean onOptionsItemSelected(MenuItem item) {

		// Handle item selection
		switch (item.getItemId()) {
		case R.id.action_loadFile:

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Select File Location.")
					.setItems(R.array.saFileServiecs,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									switch (which) {
									case 0:
										openLocalFileDialog();
										break;

									case 1:
										mChooser.forResultType(
												DbxChooser.ResultType.FILE_CONTENT)
												.launch(MainActivity.this,
														DBX_CHOOSER_REQUEST);
										break;
									default:
										break;
									}

								}
							}).show();

			return true;
		case R.id.action_settings:

			Intent i = new Intent(MainActivity.this, SettingsActivity.class);
			startActivityForResult(i, 5);

			return true;
		case R.id.action_connect:
			lPrintcore.connect();
			updateMenuTitles();

			return true;
		case R.id.action_reset:
			lPrintcore.reset();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	/**
	 * Open a FileDialog for choosing the input .gcode file fr
	 */
	private void openLocalFileDialog() {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.START_PATH, Environment
				.getExternalStorageDirectory().getPath());
		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

		// can user select directories or not
		intent.putExtra(FileDialog.CAN_SELECT_DIR, false);

		intent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "gcode" });

		startActivityForResult(intent, REQUEST_LOAD);
	}

	public synchronized void onActivityResult(final int requestCode,
			int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_LOAD) {
				requestLoadFromLocal(data);
			} else if (requestCode == DBX_CHOOSER_REQUEST) {
				requestLoadFromDropbox(data);

			}
		} else if (resultCode == Activity.RESULT_CANCELED) {
			// "file not selected");
		}

	}

	/**
	 * Activity callback for loading the file from the drobox cache
	 * 
	 * @param data
	 */
	private void requestLoadFromDropbox(final Intent data) {

		DbxChooser.Result result = new DbxChooser.Result(data);
		Log.d("main", "Link to selected file: " + result.getLink());

		if (!result.getName().endsWith("gcode")) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Warning, selected file is not a .gcode file!\nPlease Select another one with the correct extension. ");

			builder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							mChooser.forResultType(
									DbxChooser.ResultType.FILE_CONTENT).launch(
									MainActivity.this, DBX_CHOOSER_REQUEST);
						}
					});
			builder.show();

		} else {

			loadDropboxFile(result.getLink());
		}
	}

	/**
	 * Activity callback for loading the file from local storage
	 * 
	 * @param data
	 */
	private void requestLoadFromLocal(final Intent data) {
		System.out.println("Loading...");
		filePath = data.getStringExtra(FileDialog.RESULT_PATH);

		try {
			btnStartPrint.setEnabled(checkFile(filePath));

		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}

	/**
	 * @param aFileURI
	 * @throws FileNotFoundException
	 * @throws DropboxException
	 */
	private void loadDropboxFile(Uri aFileURI) {
		CacheDropboxFile task = new CacheDropboxFile(this);
		// start async task setting the progress to zero
		task.execute(aFileURI.getPath());
	}

	@Override
	protected void onPause() {
		super.onPause();
		lPrintcore.pause();
		if (webserv != null)

			webserv.stopServer();
	}

	@Override
	protected void onResume() {
		super.onResume();

		lPrintcore.resume();
		lPrintcore.connect();

		updateMenuTitles();

		resumeWebserver();
	}

	/**
	 * Instantiate and start the webserver
	 */
	private void resumeWebserver() {
		// start Webserver
		if (sharedPref.getBoolean("pref_comm_webserver", false)) {
			if (webserv == null)
				webserv = new ProDroidServer(this);

			if (webserv != null) {
				int lPort = Integer.parseInt(sharedPref.getString(
						"pref_comm_webserver_port", "8080"));

				webserv.startServer(lPort);
			}
		} else
			updateServerStatus(false, "");
	}

	/**
	 * Shows a Line of text in the logwindow
	 * 
	 * @param aLine
	 *            The Message
	 */
	public void showMessage(String data) {
		Time now = new Time();
		now.setToNow();
		mDumpTextView.append(now.hour + ":" + now.minute + " " + data + "\n");
		mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
	}

	public void updateDriverInfo(String aMessage) {
		tvDriverInfo.setText(aMessage);
	}

	/**
	 * Showing Dialog
	 * */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case progress_bar_type:
			if (progressOpen) {
				pDialog = new ProgressDialog(this);
				pDialog.setMessage(getString(R.string.loading_file_please_stand_by_)
						+ "\n");
				pDialog.setIndeterminate(false);
				pDialog.setMax(100);

				pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				pDialog.setCancelable(false);
				pDialog.show();
				return pDialog;
			}
			return null;

		default:
			return null;
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
			long arg3) {
		if (arg0.equals(spHeat)) {
			switch (spHeat.getSelectedItemPosition()) {
			case 0: // 0
				lPrintcore.setSelectedTemp(0);
				break;
			case 1: // 185
				lPrintcore.setSelectedTemp(185);
				break;
			case 2: // 230
				lPrintcore.setSelectedTemp(230);
				break;
			case 3: // Custom
				// open a dialog
				break;

			default:
				break;
			}
			if (btnHeat.isChecked()) {
				lPrintcore.changeHeatTemp(lPrintcore.getSelectedTemp());
			}

		}
		if (arg0.equals(spBed)) {
			switch (spBed.getSelectedItemPosition()) {
			case 0: // 0
				lPrintcore.setSelectedBedTemp(0);
				break;
			case 1: // 60
				lPrintcore.setSelectedBedTemp(60);
				break;
			case 2: // 110
				lPrintcore.setSelectedBedTemp(110);
				break;
			case 3: // Custom
				// open a dialog
				break;

			default:
				break;
			}
			if (btnBed.isChecked()) {
				lPrintcore.changeBedTemp(lPrintcore.getSelectedBedTemp());
			}

		}
	}
	
	

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {

	}

	/**
	 * Display Temperature readings from the printer
	 */
	public void notifyNewTemperatures() {
		int heatTemp = Math
				.round((Math.min(lPrintcore.getTemperature(), 230) / 230) * 100);
		int bedTemp = Math
				.round((Math.min(lPrintcore.getBedtemperature(), 110) / 110) * 100);
		pbHeat.setProgress(heatTemp);
		pbBed.setProgress(bedTemp);

		tvHeat.setText("Heater: " + lPrintcore.getTemperature() + "°C / "
				+ lPrintcore.getTemperature_target());
		tvBed.setText("Bed: " + lPrintcore.getBedtemperature() + "°C / "
				+ lPrintcore.getBedtemperature_target());

	}

	/**
	 * Set Buttons and text properties according to current application state
	 */
	public void updateInterface() {
		btnPausePrint.setEnabled(lPrintcore.isPrinting());
		btnStartPrint.setEnabled(!lPrintcore.isPrinting());
		btn_switch_distance.setText(String.valueOf(currentControlDistance));

		edtExtrudeDist.setText(Integer.toString(currentExtrudeDistance));
		edtExtrueRate.setText(Integer.toString(currentExtrudeRate));

		if (filePath == "")
			tvFileName.setText(R.string.no_file_loaded_);
	}

	/**
	 * Show alert dialog
	 */
	public void alertUser(String aTitle, String aMessage) {
		AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this)
				.create();
		alertDialog.setTitle(aTitle);
		alertDialog.setMessage(aMessage);
		alertDialog.show();
	}

	/**
	 * Show print progress as integer
	 * 
	 * @param aProgress
	 *            Progress 0-100 as int
	 */
	public void showProgress(int aProgress) {
		tvProgress.setText("Print:" + aProgress + "%");
		if (webserv != null)
			webserv.notifyProgress(aProgress);
	}

	/**
	 * Show print progress as String
	 * 
	 * @param aProgress
	 *            Textual representation of progress like finished or error
	 */
	public void showProgress(String aProgress) {
		tvProgress.setText(aProgress);
	}

	// Dropbox Stuff
	class CacheDropboxFile extends AsyncTask<String, Integer, Integer> {
		Context context;
		String str_mesg;
		ProgressDialog progress;
		String errorMessage = null;

		public CacheDropboxFile(Context context) {
			this.context = context;
			this.str_mesg = context.getString(R.string.downloading_file_);
		}

		// Method executed before the async task start. All things needed to be
		// setup before the async task must be done here. In this example we
		// simply display a message.
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progress = new ProgressDialog(context);
			progress.setMessage(str_mesg);
			progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

			progress.show();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {

			super.onProgressUpdate(values);

		}

		@Override
		protected void onCancelled(Integer result) {

			super.onCancelled(result);
		}

		@Override
		protected void onPostExecute(Integer result) {

			super.onPostExecute(result);
			progress.cancel();
			if (errorMessage != null) {
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setMessage("Caching of file failed")
						.setTitle(R.string.connection_error)
						.setNegativeButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										dialog.cancel();
									}
								}).create().show();
			} else
				try {
					// checkFile(filePath);
					btnStartPrint.setEnabled(checkFile(filePath));
				} catch (IOException e) {
					
					e.printStackTrace();
				}

		}

		/**
		 * Load cached dropboxfile into app storeage
		 */
		@Override
		protected Integer doInBackground(String... params) {
			BufferedInputStream br = null;
			BufferedOutputStream bw = null;
			// get remote path
			Uri dropUri = Uri.parse(params[0]);
			File dropFile = new File("" + dropUri);

			// create local file with remote filename
			File localFile = new File(context.getFilesDir().getPath()
					.toString()
					+ dropFile.getName());

			try {
				if (!localFile.exists()) {
					localFile.createNewFile();
				}

				// copy file
				br = new BufferedInputStream(new FileInputStream(
						dropUri.getPath()));
				bw = new BufferedOutputStream(new FileOutputStream(localFile));

				byte[] buf = new byte[1024];
				int read = 0;
				while ((read = br.read(buf, 0, buf.length)) != -1) {
					bw.write(buf, 0, read);

				}
				// set local path to new file
				filePath = localFile.getAbsolutePath();

			} catch (Exception e) {
				errorMessage = e.getLocalizedMessage();
				filePath = null;
				e.printStackTrace();
			} finally {
				try {
					if (br != null)
						br.close();
					if (bw != null)
						bw.close();

				} catch (IOException e) {

				}
			}

			return null;
		}

	}

	public void updateServerStatus(boolean running, String ipAddress) {
		if (running) {
			tvServerStatus.setText(getString(R.string.webserver_status_) + " "
					+ getString(R.string.running));
			tvServerAddress.setText("IP:Port: " + ipAddress);
		} else {
			tvServerStatus.setText(getString(R.string.webserver_status_) + " "
					+ getString(R.string.stopped));
			tvServerAddress.setText("IP:Port: -");
		}
	}
}
