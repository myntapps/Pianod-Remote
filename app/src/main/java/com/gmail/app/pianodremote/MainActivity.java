/*
 * Pianod Remote, for pandora
 * Copyright (c) 2015. Michael Obst
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
 */

package com.gmail.app.pianodremote;
//todo play/pause animation
//todo rate animations?
//todo skip animations
//todo search and add stations
//todo delete stations
//todo rename stations
//todo other station stuff? view seeds etc...?
//todo alternative resolutions, particularly fix tablet layout
//todo make widgets
//todo make cover updates seamless, so we never get flashes of the other cover
//todo animate cover transitions
//todo activity/fragment transition animations
//todo notification options
//todo pianod2
//todo tasker integration
//todo help page
//todo about page
//todo better loading, when song is being fetched from pandora

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.melnykov.fab.FloatingActionButton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.PriorityQueue;


public class MainActivity extends AppCompatActivity {

    //global variables
    private Boolean isPandoraLogged = false;
    private Boolean isPandoraLoggingIn = false;
    private Boolean isLoggingIn = false;
    private Boolean isLogged = false;
    private Boolean isAdmin = false;
    private Boolean keepUpdating = false;
    private backgroundUpdater mainLoop;
    private Drawable albumArt;
    private String currentRating = "neutral";
    private Boolean playStatus = false;
    private Integer currentVolume;
    private String lastAlbumLink = "";
    private Boolean defaultCover = true;   //used to decide is we are using stored or default cover
    private String currentStation = "No Station Selected";
    private PriorityQueue < String > writeBuffer = new PriorityQueue <> ();
    private Boolean serverDialogDismissed = false;
    private Boolean userAlertDismissed = false;
    private Boolean userLoginAlertDismissed = false;
    private Boolean userPandoraAlertDismissed = false;
    //ui objects
    private SeekBar bar;
    private TextView statusText;
    private MainActivity myContext = this;

    //Socket
    private Socket socket;

    //////////////////////////////////
    //
    //  BASIC CONTROLS
    //
    ///////////////////////////////////


    public void sendLike(View view) {
        if (currentRating.equals("good")) {
            socketWrite("RATE NEUTRAL");
        }
        else if (currentRating.equals("neutral") || currentRating.equals("bad")) {
            socketWrite("RATE GOOD");
        }
    }

    public void sendDislike(View view) {

        if (currentRating.equals("bad")) {
            socketWrite("RATE NEUTRAL");
        }
        else if (currentRating.equals("neutral") || currentRating.equals("good")) {
            socketWrite("RATE BAD");
        }
    }

    public void sendVolume(Integer volume) {
        if (volume < -35) volume = -35;
        if (volume > 35) volume = 35;
        socketWrite("Volume " + String.valueOf(volume));
    }

    public void sendPlayPause(View view) {socketWrite("PLAYPAUSE");}

    public void sendSkip(View view) { socketWrite("SKIP");}


    ///////////////////////////////////////
    //
    //  INTERACTIONS WITH ASYNC TASK
    //
    //////////////////////////////////////

    public void sendStatus() {
        socketWrite("STATUS");
    }

    public Boolean socketWrite(String writeString) {
        Log.d("add to buffer",writeString);
        writeBuffer.add(writeString);
        return true;
    }

    public void stopUpdater() {
        keepUpdating = false;
    }

    public void startUpdater() {
        keepUpdating = true;
    }

    public Boolean isConnected() {
        return socket.isConnected();
    }


    ///////////////////////////////////////
    //
    //  UI UPDATE FUNCTIONS
    //
    //////////////////////////////////////


    public void updateStatus(final String update) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() { statusText.setText(update);}
        });
    }

    public void doAlertDialog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Error");
                alertDialog.setCancelable(true);
                alertDialog.setMessage(message);
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        alertDialog.cancel();
                    }
                });
                alertDialog.show();
            }
        });
    }

    public void blankUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView albumText = (TextView) findViewById(R.id.textView);
                albumText.setText("Album");
                TextView songText = (TextView) findViewById(R.id.textView3);
                songText.setText("Song");
                TextView artistText = (TextView) findViewById(R.id.textView2);
                artistText.setText("Artist");
                try {
                    Bitmap bitmap = BitmapFactory.decodeResource(MainActivity.this.getResources(),R.drawable.bigbg);
                    defaultCover = true;
                    Drawable albumArt2 = new BitmapDrawable(getResources(), bitmap);
                    ImageView img = (ImageView) findViewById(R.id.imageView);
                    int width = MainActivity.this.getWindow().getDecorView().getHeight();
                    img.setImageDrawable(albumArt2);
                    img.setMinimumWidth(width);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    ////////////////////////////////////
    //
    //  BACKGROUND UPDATER (is the main socket service running this thing)
    //
    ////////////////////////////////////

    private class backgroundUpdater extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPostExecute(Void v) {
            Log.d("post exec", "post exec");
            //statusText.setText("Disconnected");
            ImageButton dislikeBut = (ImageButton) findViewById(R.id.dislikeImageButton);
            ImageButton likeBut = (ImageButton) findViewById(R.id.likeImageButton);
            ImageButton skipButton = (ImageButton) findViewById(R.id.skipImageButton);
            dislikeBut.setEnabled(false);
            likeBut.setEnabled(false);
            skipButton.setEnabled(false);
        }

        @Override
        protected void onPreExecute() {
            updateStatus("Connecting");
            ImageButton dislikeBut = (ImageButton) findViewById(R.id.dislikeImageButton);
            ImageButton likeBut = (ImageButton) findViewById(R.id.likeImageButton);
            ImageButton skipButton = (ImageButton) findViewById(R.id.skipImageButton);
            dislikeBut.setEnabled(false);
            likeBut.setEnabled(false);
            skipButton.setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            restartLoop:   //when we have total failure we breakout to here
            while (true) { //we start an infinite loop
                Log.d("started the loop","Started the loop");
                isLogged = false;
                isLoggingIn = false;
                isPandoraLogged = true;  //assume that it is logged in, but we check later
                isPandoraLoggingIn = false;
                keepUpdating = true;

                Log.d("start make", "socket");

                //if socket exists close it
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        socket = null;
                    }
                }

                //create socket
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                    int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                    Log.d("socket details", socketIP + " " + socketPort);
                    socket = new Socket();
                    socket.setSoTimeout(2000);
                    socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                    updateStatus("Socket Connected");
                } catch (IOException e) {
                    Log.e("failed to make socket", "failed");
                    socket = null;
                    blankUI();
                    updateStatus("Socket Failed");
                    if (!serverDialogDismissed) {
                        serverDialogDismissed = true;
                        Log.e("dialog", "failed");
                        doAlertDialog("I couldn't connect to the server. Use the settings menu to double check the server's address and port");
                    }
                    //SLEEP A LITTLE TO SAVE CPU
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                        //e.printStackTrace();
                        Log.d("interrupted", "do nothing");
                    }
                    continue;
                }

                serverDialogDismissed = true;

                //EXTRA CHECK
                if (!socket.isConnected()) {
                    Log.e("socket not connected", "OOPS");
                    updateStatus("Socket Not Connected");
                    blankUI();
                    continue;
                }


                //INITIALISE OUR BUFFERS
                PrintWriter mBufferOut;
                BufferedReader mBufferIn;

                try {
                    mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    updateStatus("Buffers Created");
                } catch (IOException e) {
                    Log.e("failed start io buffers", "quit main loop");
                    updateStatus("Buffers Failed");
                    blankUI();
                    continue;
                }

                //Set variables for main loop
                boolean isAlive = true;
                long oldTime = 0;
                int lostSignalCount = 0;
                sendStatus();
                Log.d("start main loop","start main loop");
                //MAIN LOOP STARTS HERE
                while (keepUpdating) {

                    //READ ANYTHING THAT IS WAITING

                    try {
                        if (mBufferIn.ready()) {
                            isAlive = true;
                            try {
                                String outPut = mBufferIn.readLine();
                                Log.d("Received", outPut);
                                parseOutput(outPut);
                            } catch (IOException e) {
                                Log.e("failed to read", "continuing");
                                updateStatus("Reading Failed");
                            }

                            continue; //repeat loop as we don't want to write if still reading
                        }
                    } catch (IOException e) {
                        Log.e("socket failed to read", "exiting");
                        updateStatus("Socket Broken");
                        doAlertDialog("Socket Broke Restarting");
                        blankUI();
                        continue restartLoop;
                    }

                    //LOG US IN AS PIANOD USER
                    if (!isLoggingIn && !isLogged && !mBufferOut.checkError()) {
                        isLoggingIn = true;
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        String username = prefs.getString("prefUserName", "admin");
                        String password = prefs.getString("prefUserPassword", "admin");
                        mBufferOut.println("USER " + username + " " + password);
                        mBufferOut.flush();
                        mBufferOut.println("GET PRIVILEGES");
                        mBufferOut.flush();
                        mBufferOut.println("GET PANDORA USER");
                        mBufferOut.flush();
                        updateStatus("Pianod Logging In");
                        continue;
                    }

                    //CHECK PANDORA IS AVAILABLE
                    if (!isPandoraLoggingIn && !isPandoraLogged && isAdmin && !mBufferOut.checkError()) {
                        isPandoraLoggingIn = true;
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        String pandora_username = prefs.getString("prefPandoraUser", "admin");
                        String pandora_password = prefs.getString("prefPandoraPassword", "admin");
                        mBufferOut.println("PANDORA USER " + pandora_username + " " + pandora_password);
                        mBufferOut.flush();
                        mBufferOut.println("GET PANDORA USER");
                        mBufferOut.flush();
                        updateStatus("Pandora Logging In");
                        continue;
                    }

                    //KEEP ALIVE STUFF
                    long newTime = System.currentTimeMillis();
                    if ((newTime - oldTime) > 1000) {
                        oldTime = newTime;
                        if (!isAlive) {
                            lostSignalCount = lostSignalCount + 1;
                            Log.d("NOT SIGNAL", "NO SIGNAL");
                            updateStatus("Waiting for Connection " + String.valueOf(11 - lostSignalCount));
                            if (lostSignalCount > 10) {
                                blankUI();
                                updateStatus("Connection Lost");
                                //doAlertDialog("Connection Lost");
                                continue restartLoop;
                            }
                        } else {
                            lostSignalCount = 0;
                        }
                        //THIS SENDS BLANK MESSAGE FREQUENTLY TO GET CURRENT PLAYING TIME
                        if (!mBufferOut.checkError()) {
                            mBufferOut.println("");
                            mBufferOut.flush();
                            isAlive = false;
                        }
                    }

                    //WRITE THE OUTPUT STRING CURRENTLY WAITING
                    if (!writeBuffer.isEmpty() && !mBufferOut.checkError()) {
                        Log.e("Writing", writeBuffer.peek());
                        mBufferOut.println(writeBuffer.remove());
                        mBufferOut.flush();
                    }

                    //SLEEP A LITTLE TO SAVE CPU
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        //e.printStackTrace();
                        Log.d("interrupted", "do nothing");
                    }
                }

                //TIDYING UP
                Log.d("exiting", "out");

                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Log.d("out now", "out");
                updateStatus("Disconnected");
                return null;
            }
        }
    }

    //////////////////////////////////////
    //
    //   LIFECYCLE STUFF BELOW HERE
    //
    //////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("draw", "load create");
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                Window window = getWindow();
                window.setStatusBarColor(getResources().getColor(R.color.transparentlevel1));
                int height = 0;
                int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    height = getResources().getDimensionPixelSize(resourceId);
                }
                toolbar.setPadding(0, height, 0, 0);
            }
            else {
                Window window = getWindow();
                window.setStatusBarColor(getResources().getColor(R.color.thememaindark));
                android.support.v7.widget.Toolbar toolbar2 = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
                toolbar2.layout(0,0,0,0);
            }
        }




        statusText = (TextView) findViewById(R.id.textStatus);

        //seek bar
        bar = (SeekBar) findViewById(R.id.seekBar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                //Log.d("seekBar", String.valueOf(seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //do something
                //Log.d("seekBar", String.valueOf(seekBar.getProgress()));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int volume = seekBar.getProgress() - 100;
                volume = volume / 3;
                sendVolume(volume);
                //Log.d("seekBar", String.valueOf(seekBar.getProgress()));
            }
        });


    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("pause","pause");
        stopUpdater();
        mainLoop = null;
        statusText.setText("Not Connected");
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusText = (TextView) findViewById(R.id.textStatus);
        Log.e("resume","resume");
        //set navbar colour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.thememaindark));
        }
        //load the last saved album art form the file and apply it
        try {
            //todo figure out whether to load generic or album somehow?
            //Bitmap bitmap = BitmapFactory.decodeFile(getFilesDir() + "albumArt");
            Bitmap bitmap = BitmapFactory.decodeResource(this.getResources(),R.drawable.bigbg);
            defaultCover = true;
            Drawable albumArt2 = new BitmapDrawable(getResources(), bitmap);
            ImageView img = (ImageView) findViewById(R.id.imageView);
            int width = this.getWindow().getDecorView().getHeight();
            img.setImageDrawable(albumArt2);
            img.setMinimumWidth(width);
            //lastAlbumLink = "";
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        //FAB ANIMATION
        TranslateAnimation anim = new TranslateAnimation(0,0,1000,0);
        anim.setDuration(1000);
        anim.setFillAfter(true);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.startAnimation(anim);
        //restart the background updater
        if (mainLoop == null) {
            mainLoop = new backgroundUpdater();
            mainLoop.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            Log.d("onResume", "main start check");
        }
        sendStatus();
    }

    //////////////////////////////
    //
    //  OPTIONS/TOOLBAR MENU STUFF
    //
    //////////////////////////////

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        super.onCreateOptionsMenu(menu);
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
        switch (id) {
            case R.id.action_settings:
                openSettings();
                return true;
            case R.id.action_stations:
                openStations(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }

    }

    public void openSettings() {
        Log.d("draw","load settings");
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    public void openStations(View view) {
        Log.d("draw","load stations");
        startActivity(new Intent(MainActivity.this, StationActivity.class));
    }

    //////////////////////////
    //
    //  album art download async task
    //
    ///////////////////////////

    private class DownloadCoverTask extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... link) {

            try {
                //download album art

                String test = link[0];
                Log.e("ALBUM","DOWNLOADER");
                Log.d(test, test);
                System.gc();
                InputStream is = (InputStream) new URL(link[0]).getContent();
                //create drawable
                albumArt = Drawable.createFromStream(is, "src name");
                //also create bitmap
                Bitmap bitmapAlbumArt = ((BitmapDrawable) albumArt).getBitmap();
                //save it to a file for quick reloading as needed
                FileOutputStream aArt = new FileOutputStream(getFilesDir() + "albumArt");
                bitmapAlbumArt.compress(Bitmap.CompressFormat.PNG, 100, aArt);
                aArt.close();
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == 1)
                {
                    //actually display, for portrait
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView img = (ImageView) findViewById(R.id.imageView);
                            img.setImageDrawable(albumArt);
                            int width = myContext.getWindow().getDecorView().getHeight();
                            img.setMinimumWidth(width);
                        }
                    });
                } else {
                    //actually display, for landscape
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView img = (ImageView) findViewById(R.id.imageView);
                            img.setImageDrawable(albumArt);
                            int width = myContext.getWindow().getDecorView().getHeight();
                            img.setMinimumWidth(width);
                        }
                    });
                }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            defaultCover = true;
                            ImageView img = (ImageView) findViewById(R.id.imageView);
                            img.setImageResource(R.drawable.bigbg);
                            int width = myContext.getWindow().getDecorView().getHeight();
                            img.setMinimumWidth(width);
                        }
                    });

                }
            return null;
            }


        protected void onProgressUpdate(Void v) {

        }

        protected void onPostExecute(Void v) {

        }
    }

    /////////////////////////////////////////////
    //
    //  IMAGE RESIZER FOR LANDSCAPE (MAYBE FOR ALL LATER
    //
    ////////////////////////////////////////////

    public void scaleImage(ImageView view) {
        //Get ImageView
        Drawable drawing = view.getDrawable();
        Bitmap bitmap = ((BitmapDrawable)drawing).getBitmap();

        //Set some dimesions
        int picWidth = bitmap.getWidth();
        int picHeight = bitmap.getHeight();
        int viewHeight = view.getHeight();

        //calculate what we need to scale by
        float scale = viewHeight / picHeight;

        //set the image view dimensions (image should scale inside)
        view.setMaxWidth(Math.round(scale * picWidth));
        view.setMinimumWidth(Math.round(scale * picWidth));

    }

    /////////////////////////////////////////////
    //
    //  PARSE ALL OF THE OUTPUT
    //
    ////////////////////////////////////////////

    public void parseOutput(String outPut) {
        //every line is sent here and we react to it
        //set info received and update the screen
        String outPutCode = outPut.substring(0, 3);
        final String outPutText;
        switch (outPutCode) {


            //Playing status 101
            case "101":
                playStatus = true;
                //Log.d("got 101 setting to", outPut);
                outPutText = outPut.substring(5,15);
                //Log.d("got 101 setting to", outPutText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus(outPutText);
                        com.melnykov.fab.FloatingActionButton fab = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                        fab.setImageResource(R.drawable.ic_pause);
                    }
                });
                break;

            //Paused status 102
            case "102":
                playStatus = false;
                Log.d("got 102 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Paused");
                        com.melnykov.fab.FloatingActionButton fab = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                        fab.setImageResource(R.drawable.ic_play);
                    }
                });
                break;

            //Stopped status 103
            case "103":
                playStatus = false;
                Log.d("got 103 setting to", outPut);
                blankUI();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("No Station Selected");
                        com.melnykov.fab.FloatingActionButton fab = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                        fab.setImageResource(R.drawable.ic_play);
                    }
                });
                break;

            //Stopped status 106
            case "106":
                playStatus = false;
                blankUI();
                Log.d("got 106 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Stalled");
                        com.melnykov.fab.FloatingActionButton fab = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                        fab.setImageResource(R.drawable.ic_play);
                    }
                });
                break;



            //TODO finish setting proper settings
            //station is 108
            case "108":
                currentStation = "No Station Selected";
                Log.d("got 108 setting to", "No STation Selected");
                lastAlbumLink = null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(currentStation);
                        ImageView img = (ImageView) findViewById(R.id.imageView);
                        img.setImageResource(R.drawable.bigbg);
                        int width = myContext.getWindow().getDecorView().getHeight();
                        img.setMinimumWidth(width);
                    }
                });
                break;

            //station is 109
            case "109":
                outPutText = outPut.substring(29);
                currentStation = outPutText;
                Log.d("got 109 setting to", outPutText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(outPutText);
                    }
                });
                break;

            //112 is album name
            case "112":
                outPutText = outPut.substring(11);
                Log.d("got 112 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView albumText = (TextView) findViewById(R.id.textView);
                        albumText.setText(outPutText);
                    }
                });
                break;

                //artist data is 113
            case "113":
                outPutText = outPut.substring(12);
                Log.d("got 113 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView albumText = (TextView) findViewById(R.id.textView2);
                        albumText.setText(outPutText);
                    }
                });
                break;


                //song title is 114
            case "114":
                outPutText = outPut.substring(11);
                Log.d("got 114 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView albumText = (TextView) findViewById(R.id.textView3);
                        albumText.setText(outPutText);
                    }
                });
                break;


                //Track Rating is 116
            case "116":
                outPutText = outPut.substring(12);
                Log.d("got 116 setting to", outPut);
                if (outPutText.contains("neutral")) {
                    currentRating = "neutral";
                } else if (outPutText.contains("good")) {
                    currentRating = "good";
                } else if (outPutText.contains("bad")) {
                    currentRating = "bad";
                }

                Log.d("parse", currentRating);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageButton dislikeBut = (ImageButton) findViewById(R.id.dislikeImageButton);
                        ImageButton likeBut = (ImageButton) findViewById(R.id.likeImageButton);
                        switch (currentRating) {
                            case "good":
                                likeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.themehighlight));
                                dislikeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.textwhite));
                                break;
                            case "bad":
                                likeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.textwhite));
                                dislikeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.themehighlight));
                                break;
                            case "neutral":
                                likeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.textwhite));
                                dislikeBut.setColorFilter(MainActivity.this.getResources().getColor(R.color.textwhite));
                                break;
                        }
                    }
                });
                break;

            //album art is 118
            case "118":
                outPutText = outPut.substring(14);
                Log.d("got 118 setting to", outPut);
                    //check if art the same as previous
                defaultCover = false;
                if (!outPutText.equals(lastAlbumLink)) {
                    lastAlbumLink = outPutText;
                    DownloadCoverTask albumLoop = new DownloadCoverTask();
                    albumLoop.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, outPutText);
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap bitmap = BitmapFactory.decodeFile(getFilesDir() + "albumArt");
                            Drawable albumArt2 = new BitmapDrawable(getResources(), bitmap);
                            ImageView img = (ImageView) findViewById(R.id.imageView);
                            img.setImageDrawable(albumArt2);
                        }
                    });
                }

                break;



            //TODO Improve Pandora Login
            //pandora credentials set
            case "133":
                isPandoraLogged = true;
                isPandoraLoggingIn = false;
                updateStatus("Pandora Logged In");
                Log.d("got 133 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                break;

            //get privileges result 136
            //todo add more user types
            case "136":
                isLoggingIn = false;
                outPutText = outPut.substring(16);
                Log.d("got 136 setting to", outPut);
                if (outPutText.contains("admin")) {
                    updateStatus("Pianod Logged In");
                    isLogged = true;
                    isAdmin = true;
                    Log.e("setting", "logged In");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Pianod Logged In");
                            ImageButton dislikeBut = (ImageButton) findViewById(R.id.dislikeImageButton);
                            ImageButton likeBut = (ImageButton) findViewById(R.id.likeImageButton);
                            ImageButton skipButton = (ImageButton) findViewById(R.id.skipImageButton);
                            dislikeBut.setEnabled(true);
                            likeBut.setEnabled(true);
                            skipButton.setEnabled(true);
                            dislikeBut.setVisibility(View.VISIBLE);
                            likeBut.setVisibility(View.VISIBLE);
                            skipButton.setVisibility(View.VISIBLE);
                            com.melnykov.fab.FloatingActionButton playButton = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                            playButton.setEnabled(true);
                            SeekBar volBar = (SeekBar) findViewById(R.id.seekBar);
                            volBar.setEnabled(true);
                        }
                    });
                } else {
                    isLogged = true;
                    isAdmin = false;
                    Log.e("set", "logged out");

                    if (!userAlertDismissed) {
                        doAlertDialog("You don't have admin privileges on the server. Some functions will be missing.");
                        userAlertDismissed = true;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Pianod Logged In");
                            ImageButton dislikeBut = (ImageButton) findViewById(R.id.dislikeImageButton);
                            ImageButton likeBut = (ImageButton) findViewById(R.id.likeImageButton);
                            ImageButton skipButton = (ImageButton) findViewById(R.id.skipImageButton);
                            dislikeBut.setEnabled(false);
                            likeBut.setEnabled(false);
                            skipButton.setEnabled(false);
                            dislikeBut.setVisibility(View.INVISIBLE);
                            likeBut.setVisibility(View.INVISIBLE);
                            skipButton.setVisibility(View.INVISIBLE);
                            com.melnykov.fab.FloatingActionButton playButton = (com.melnykov.fab.FloatingActionButton) findViewById(R.id.fab);
                            playButton.setEnabled(false);
                            SeekBar volBar = (SeekBar) findViewById(R.id.seekBar);
                            volBar.setEnabled(false);
                        }
                    });
                    updateStatus("Low Pianod User Privileges");
                }
                break;



            //volume is 141
            case "141":
                outPutText = outPut.substring(12);
                Log.d("got 141 setting to", outPut);
                currentVolume = Integer.valueOf(outPutText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bar = (SeekBar) findViewById(R.id.seekBar);
                        bar.setProgress((Integer.valueOf(outPutText) * 3 + 100));
                    }
                });
                break;

            //TODO offer option of whether to do our user or stick with current
            //Pandora user is 170
            case "170":
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                String pandora_username = prefs.getString("prefPandoraUser", "admin");
                if (outPut.contains(pandora_username)) {
                    //pandora is logged in with our chosen user
                    isPandoraLogged = true;
                    isPandoraLoggingIn = false;
                } else {
                    //pandora is logged in but with a different user, so we will attempt to use our user
                    isPandoraLogged = false;
                    isPandoraLoggingIn = false;
                }


                updateStatus("Pandora Logged In");
                Log.d("got 170 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                break;


            //200 is success so we update status for whatever succeeded
            case "200":
                //sendStatus();
                Log.d("got 103 setting to", outPut);
                break;



            //Error pandora credentials not set
            case "204":
                outPutText = outPut.substring(12);
                if (outPutText.contains("Pandora credentials not set")) {
                    isPandoraLoggingIn = false;
                    isPandoraLogged = false;
                    updateStatus("Pandora Bad Credentials");
                }
                Log.d("got 204 setting to", outPut);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                break;


            //Error bad pandora credentials
            case "406":
                updateStatus("Pandora Bad Credentials");
                Log.d("got 406 setting to", outPut);
                if (outPut.contains("Invalid login or password") && !outPut.contains("email")) {
                    if (!userLoginAlertDismissed) {
                        userLoginAlertDismissed = true;
                        doAlertDialog("The user name and password supplied for your Pianod user were invalid. Use the settings menu to change these.");
                        updateStatus("Pandora Bad Credentials");
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                break;

            //Error bad pandora credentials
            case "504":
                updateStatus("Pandora Bad Credentials");
                Log.d("got 504 setting to", outPut);
                if (outPut.contains("Authentication Failure")) {
                    if (!userPandoraAlertDismissed) {
                        userPandoraAlertDismissed = true;
                        doAlertDialog("The Pandora user name and password supplied were invalid. Use the settings menu to change these.");
                        updateStatus("Pandora Bad Credentials");
                        isPandoraLoggingIn = false;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                break;

            default:

                //sendStatus();
                Log.e("UNPARSED", outPut);
                break;

        }
    }

    /////////////////////////////////////////////
    //
    //  GRAB VOLUME KEYS
    //
    ////////////////////////////////////////////

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    sendVolume(currentVolume + 5);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    sendVolume(currentVolume - 5);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

}