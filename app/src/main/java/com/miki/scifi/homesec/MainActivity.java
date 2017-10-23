package com.miki.scifi.homesec;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveIdResult;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;

import org.apache.commons.io.IOUtils;

public class MainActivity extends AppCompatActivity implements ConnectionCallbacks,
        OnConnectionFailedListener {

    private static final String TAG = "HomeSec";
    private static final String EXISTING_FILE_ID = "0B68qLJdVq5WQVTV4NTVnWmVGbHc";
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private static final int REQUEST_CODE_OPENER = 4;

    private GoogleApiClient mGoogleApiClient;
    private boolean mIsStart;

    /**
     * Create a new file and save it to Drive.
     */
    private void saveFileToDrive(boolean isStart) {
        // Start by creating a new contents, and setting a callback.
        Log.i(TAG, "Creating new contents.");
        mIsStart = isStart;

        final ResultCallback<DriveContentsResult> contentsOpenedCallback =
                new ResultCallback<DriveContentsResult>() {
                    @Override
                    public void onResult(DriveContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            // display an error saying file can't be opened
                            return;
                        }
                        // DriveContents object contains pointers
                        // to the actual byte stream
                        DriveContents contents = result.getDriveContents();

                        try
                        {
                            OutputStream outputStream = contents.getOutputStream();
                            InputStream is = getResources().openRawResource(
                                    mIsStart ? R.raw.start_config : R.raw.stop_config);
                            byte[] payload = IOUtils.toByteArray(is);
                            outputStream.write(payload);
                            contents.commit(mGoogleApiClient, null).setResultCallback(new ResultCallback<Status>() {
                                        @Override
                                        public void onResult(Status result) {
                                            // handle the response status
                                            if (!result.getStatus().isSuccess()) {
                                                Log.i(TAG,"Cannot commit file.");
                                                return;
                                            }
                                        }
                                    });

                        } catch (IOException e) {
                            Log.e(TAG, "IOException while appending to the output stream", e);
                        }
                    }
                };

        final ResultCallback<DriveIdResult> idCallback = new ResultCallback<DriveIdResult>() {
            @Override
            public void onResult(DriveIdResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.i(TAG,"Cannot find DriveId. Are you authorized to view this file?");
                    // if not authorized open for auth
                    IntentSender intentSender = Drive.DriveApi
                            .newOpenFileActivityBuilder()
                            .setMimeType(new String[] { "text/plain", "text/html" })
                            .build(mGoogleApiClient);
                    try {
                        startIntentSenderForResult(
                                intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.w(TAG, "Unable to send intent", e);
                    }

                    return;
                }
                DriveId driveId = result.getDriveId();
                DriveFile file = driveId.asDriveFile();

                file.open(
                        mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(contentsOpenedCallback);

            }
        };
        Drive.DriveApi.fetchDriveId(mGoogleApiClient, EXISTING_FILE_ID)
                .setResultCallback(idCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the client. Once connected, the camera is launched.
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_OPENER:
                // Called after a file is opened with Drive.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "File successfully opened.");
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        // The failure has a resolution. Resolve it.
        // Called typically when the app is not yet authorized, and an
        // authorization
        // dialog is displayed to the user.
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton startButton = (FloatingActionButton) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFileToDrive(true);
                Snackbar.make(view, "System have been started", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        FloatingActionButton stopButton = (FloatingActionButton) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFileToDrive(false);
                Snackbar.make(view, "System have been stopped", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
