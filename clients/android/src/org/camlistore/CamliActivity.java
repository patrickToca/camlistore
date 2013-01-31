/*
Copyright 2011 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.camlistore;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CamliActivity extends Activity {
    private static final String TAG = "CamliActivity";
    private static final int MENU_SETTINGS = 1;
    private static final int MENU_STOP = 2;
    private static final int MENU_STOP_DIE = 3;
    private static final int MENU_UPLOAD_ALL = 4;

    private IUploadService mServiceStub = null;
    private IStatusCallback mCallback = null;

    private final Handler mHandler = new Handler();

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceStub = IUploadService.Stub.asInterface(service);
            Log.d(TAG, "Service connected, registering callback " + mCallback);

            try {
                mServiceStub.registerCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            mServiceStub = null;
        };
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Button buttonToggle = (Button) findViewById(R.id.buttonToggle);

        final TextView textStatus = (TextView) findViewById(R.id.textStatus);
        final TextView textStats = (TextView) findViewById(R.id.textStats);
        final TextView textBlobsRemain = (TextView) findViewById(R.id.textBlobsRemain);
        final TextView textUploadStatus = (TextView) findViewById(R.id.textUploadStatus);
        final ProgressBar progressBytes = (ProgressBar) findViewById(R.id.progressByteStatus);
        final ProgressBar progressFile = (ProgressBar) findViewById(R.id.progressFileStatus);

        buttonToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View btn) {
                Log.d(TAG, "button click!  text=" + buttonToggle.getText());
                if (getString(R.string.pause).equals(buttonToggle.getText())) {
                    try {
                        Log.d(TAG, "Pausing..");
                        mServiceStub.pause();
                    } catch (RemoteException e) {
                    }
                } else if (getString(R.string.resume).equals(buttonToggle.getText())) {
                    try {
                        Log.d(TAG, "Resuming..");
                        mServiceStub.resume();
                    } catch (RemoteException e) {
                    }
                }
            }
        });

        mCallback = new IStatusCallback.Stub() {
            private volatile int mLastBlobsUploadRemain = 0;
            private volatile int mLastBlobsDigestRemain = 0;

            @Override
            public void logToClient(String stuff) throws RemoteException {
                // TODO Auto-generated method stub
            }

            @Override
            public void setUploading(final boolean uploading) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (uploading) {
                            buttonToggle.setText(R.string.pause);
                            textStatus.setText(R.string.uploading);
                        } else if (mLastBlobsDigestRemain > 0) {
                            buttonToggle.setText(R.string.pause);
                            textStatus.setText(R.string.digesting);
                        } else {
                            buttonToggle.setText(R.string.resume);
                            int stepsRemain = mLastBlobsUploadRemain + mLastBlobsDigestRemain;
                            textStatus.setText(stepsRemain > 0 ? "Paused." : "Idle.");
                        }
                    }
                });
            }

            @Override
            public void setFileStatus(final int done, final int inFlight, final int total) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean finished = (done == total && mLastBlobsDigestRemain == 0);
                        buttonToggle.setEnabled(!finished);
                        progressFile.setMax(total);
                        progressFile.setProgress(done);
                        progressFile.setSecondaryProgress(done + inFlight);
                        if (finished) {
                            buttonToggle.setText(getString(R.string.pause_resume));
                        }

                        StringBuilder sb = new StringBuilder(40);
                        sb.append("Files to upload: ").append(total - done);
                        textBlobsRemain.setText(sb.toString());
                    }
                });
            }

            @Override
            public void setByteStatus(final long done, final int inFlight, final long total) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // setMax takes an (signed) int, but 2GB is a totally
                        // reasonable upload size, so use units of 1KB instead.
                        progressBytes.setMax((int) (total / 1024L));
                        progressBytes.setProgress((int) (done / 1024L));
                        progressBytes.setSecondaryProgress(progressBytes.getProgress() + inFlight / 1024);
                    }
                });
            }

            @Override
            public void setUploadStatusText(final String text) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        textUploadStatus.setText(text);
                    }
                });
            }

            @Override
            public void setUploadStatsText(final String text) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        textStats.setText(text);
                    }
                });
            }
        };

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // TODO: picking files/photos to upload?
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem uploadAll = menu.add(Menu.NONE, MENU_UPLOAD_ALL, 0, R.string.upload_all);
        uploadAll.setIcon(android.R.drawable.ic_menu_upload);

        MenuItem stop = menu.add(Menu.NONE, MENU_STOP, 0, R.string.stop);
        stop.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        MenuItem stopDie = menu.add(Menu.NONE, MENU_STOP_DIE, 0, R.string.stop);
        stopDie.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        MenuItem settings = menu.add(Menu.NONE, MENU_SETTINGS, 0, R.string.settings);
        settings.setIcon(android.R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_STOP:
            try {
                if (mServiceStub != null) {
                    mServiceStub.stopEverything();
                }
            } catch (RemoteException e) {
                // Ignore.
            }
            break;
        case MENU_STOP_DIE:
            System.exit(1);
        case MENU_SETTINGS:
            SettingsActivity.show(this);
            break;
        case MENU_UPLOAD_ALL:
            Intent uploadAll = new Intent(UploadService.INTENT_UPLOAD_ALL);
            uploadAll.setClass(this, UploadService.class);
            Log.d(TAG, "Starting upload all...");
            startService(uploadAll);
            Log.d(TAG, "Back from upload all...");
            break;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (mServiceStub != null)
                mServiceStub.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            // Ignore.
        }
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(Preferences.NAME, 0);
        HostPort hp = new HostPort(sp.getString(Preferences.HOST, ""));
        if (!hp.isValid()) {
            // Crashes oddly in some Android Instrumentation thing if
            // uncommented:
            // SettingsActivity.show(this);
            // return;
        }

        bindService(new Intent(this, UploadService.class), mServiceConnection, Context.BIND_AUTO_CREATE);

        Intent intent = getIntent();
        String action = intent.getAction();
        Log.d(TAG, "onResume; action=" + action);

        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            Intent serviceIntent = new Intent(intent);
            serviceIntent.setClass(this, UploadService.class);
            startService(serviceIntent);
            setIntent(new Intent(this, CamliActivity.class));
        } else {
            Log.d(TAG, "Normal CamliActivity viewing.");
        }
    }
}
