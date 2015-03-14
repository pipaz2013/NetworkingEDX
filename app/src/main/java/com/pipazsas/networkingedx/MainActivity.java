package com.pipazsas.networkingedx;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {
    // URL to download file
    private static final String URL = "http://web.mit.edu/bentley/www/papers/a30-bentley.pdf";
    // Dialog to show progress
    ProgressDialog mProgressDialog;
    // Variable to see download status
    long total = 0;
    // Variable to peek the buffer every second
    String mThroughputEverySecond;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Button to start downloading the PDF
        Button mDownloadButton;

        // instantiate the progress dialog
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getResources().getString(R.string.progress_dialog));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);

        mDownloadButton = (Button) findViewById(R.id.buttonDownload);

        // Set click listener to Button
        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myClickHandler(v);
            }
        });
    }

    // When user clicks button, calls AsyncTask.
    // Before attempting to fetch the URL, makes sure that there is a network connection.
    public void myClickHandler(View v) {

        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected()) {
            final DownloadPdfTask mDownloadTask = new DownloadPdfTask();
            mDownloadTask.execute(URL);

            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mDownloadTask.cancel(true);
                }
            });
        }
    }

    // Uses AsyncTask to create a task away from the main UI thread. This task takes a
    // URL string and uses it to create an HttpUrlConnection. Once the connection
    // has been established, the AsyncTask downloads the contents of the webpage as
    // an InputStream.
    private class DownloadPdfTask extends AsyncTask<String, Integer, String> {

        private PowerManager.WakeLock mWakeLock;

        @Override
        protected String doInBackground(String... params) {

            // See http://stackoverflow.com/questions/3028306/download-a-
            // file-with-android-and-showing-the-progress-in-a-progressdialog
            // params comes from the execute() call: params[0] is the url.
            InputStream is = null;
            OutputStream os = null;
            HttpURLConnection mConnection = null;

            // Variable to measure overall average throughput (bytes/sec)
            long beforeDownload = System.currentTimeMillis();

            int fileLength;

            // Variable that checks elapsed time until first byte is received
            Long mLatency;

            try {
                // Create the URL, needs java.net.URL
                URL mUrl;
                mUrl = new URL(params[0]);

                // Variable to measure Latency (until first byte is received)
                long beforeConnect = System.currentTimeMillis();


                // Open connection to the URL
                mConnection = (HttpURLConnection) mUrl.openConnection();
                mConnection.connect();

                long afterConnect = System.currentTimeMillis();

                mLatency = afterConnect - beforeConnect;

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (mConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + mConnection.getResponseCode()
                            + " " + mConnection.getResponseMessage();
                }

                // This will be useful to display download percentage
                // might be -1: server did not report the length
                fileLength = mConnection.getContentLength();

                // Download the file
                is = mConnection.getInputStream();
                final File osFile = new File("sdcard/a30-bentley.pdf");
                if (!osFile.exists())
                {
                    try
                    {
                        osFile.createNewFile();
                    }
                    catch (IOException e)
                    {
                        // TODO Auto-generated catch block
                        return e.toString();
                    }
                }

                os = new FileOutputStream(osFile);

                final byte data[] = new byte[4096];
                int count;

                //  Create timer thread to peek buffer and see how many bytes are there
                Timer mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        mThroughputEverySecond += " " + total;
                    }
                }, 0, 1000);

                while ((count = is.read(data)) != -1) {
                    // Allow canceling with back button
                    if (isCancelled()) {
                        is.close();
                        return null;
                    }
                    total += count;
                    // Publishing the progress
                    if (fileLength > 0) { // Only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    os.write(data, 0, count);
                }

                mTimer.cancel();

            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (os != null) {
                        os.close();
                    }
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException ignored) {
                }
                if (mConnection != null)
                    mConnection.disconnect();
            }

            long afterDownload = System.currentTimeMillis();

            Long averageDownloadTime = afterDownload - beforeDownload;
            Long averageThroughput = fileLength / averageDownloadTime;

            Time mTime = new Time();
            mTime.setToNow();

            String result = "Downloaded date and time: " + mTime + "\n"
                    + "Overall average throughput: " + averageThroughput + "\n"
                    + "Latency: " + mLatency + "\n"
                    + "Throughput for each second interval: " + mThroughputEverySecond
                    + "Network type: " + getNetworkClass(getApplicationContext());

            return result;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
            mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mWakeLock.release();
            mProgressDialog.dismiss();

            Toast.makeText(getApplicationContext(), "Result was: "
                    + result, Toast.LENGTH_LONG).show();

            appendLog(result);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // If we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }
    }

    public void appendLog(String text)
    {
        File logFile = new File("sdcard/EdxLogNetworking.txt");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String getNetworkClass(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return "-"; //not connected
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                default:
                    return "?";
            }
        }
        return "?";
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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
