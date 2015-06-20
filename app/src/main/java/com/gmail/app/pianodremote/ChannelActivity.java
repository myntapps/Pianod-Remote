

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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



public class ChannelActivity extends ActionBarActivity {


    private ListView channelListView;
    List<String> stationList = new ArrayList<>();
    boolean stationsListed = false;
    boolean doWrite = true;
    boolean readStations = true;
    String outputString = "stations list";
    boolean failedChannelList = false;

    private class loadChannels extends AsyncTask<Void, Void, Void> {
        private Socket socket;
        @Override
        protected Void doInBackground(Void... params) {

            failedChannelList = false;

            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ChannelActivity.this);
                String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                Log.d("socket details", socketIP + " " + socketPort);
                socket = new Socket();
                socket.setSoTimeout(2000);
                socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                Log.d("finished socket details", socketIP + " " + socketPort);
            } catch (IOException e) {
                Log.e("failed to make socket","failed");
                failedChannelList = true;
                socket = null;
                //e.printStackTrace();
                return null;
            }
            //EXTRA CHECK
            if (!socket.isConnected()) {
                Log.e("socket not connected","OOPS");
            }

             try {
                PrintWriter mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                BufferedReader mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 if (!mBufferOut.checkError()) {
                     mBufferOut.println("stations list");
                     mBufferOut.flush();
                 }
                readStations = true;
                boolean firstChannel = true;
                while (readStations) {
                    if (mBufferIn.ready()) {
                        String outPut = mBufferIn.readLine();
                        String outPutCode = outPut.substring(0,3);

                        //station is 115
                        if (outPutCode.equals("115")) {
                            //skip first or else we get a duplicate
                            if(firstChannel) {
                                firstChannel = false;
                            }
                            else {
                                outPut = outPut.substring(13);
                                stationList.add(outPut);
                                Log.d("got 115 set station to", outPut);
                            }
                        }
                        //station is 204
                        else if (outPutCode.equals("204") && !stationsListed) {
                            stationsListed = true;
                            Log.d("got 204 set station to", " ");
                            readStations = false;
                        }

                        Thread.sleep(10);
                        continue;
                    }

                    Thread.sleep(50);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
        setContentView(R.layout.activity_channel);
        Toolbar toolbarChannel = (Toolbar) findViewById(R.id.toolbarchannel);
        setSupportActionBar(toolbarChannel);
        toolbarChannel.setTitle("Channel");
        channelListView = (ListView) findViewById(R.id.listView);
        String[] testArray = new String[stationList.size()];
        Collections.sort(stationList);
        stationList.toArray(testArray);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getBaseContext(), android.R.layout.simple_list_item_1, testArray);
        channelListView.setAdapter(arrayAdapter);
        channelListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String item = ((TextView) view).getText().toString();
                outputString = "play station \"" + item + "\"";
                Log.d("test", outputString);
                runChannel executor = new runChannel();
                executor.execute();
                openController();

                }
            });
        }
    }


    private class runChannel extends AsyncTask<Void, Void, Void> {
        private Socket socket;

        @Override
        protected Void doInBackground(Void... params) {

            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ChannelActivity.this);
                String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                Log.d("socket details", socketIP + " " + socketPort);
                socket = new Socket();
                socket.setSoTimeout(2000);
                socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                Log.d("finished socket details", socketIP + " " + socketPort);
            } catch (IOException e) {
                Log.e("failed to make socket","failed");
                failedChannelList = true;
                socket = null;
                //e.printStackTrace();
                return null;
            }
            //EXTRA CHECK
            if (!socket.isConnected()) {
                Log.e("socket not connected","OOPS");
            }

            try {
                PrintWriter mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ChannelActivity.this);
                String username = prefs.getString("prefUserName", "admin");
                String password = prefs.getString("prefUserPassword", "admin");
                mBufferOut.println("USER " + username + " " + password);
                mBufferOut.flush();
                mBufferOut.println("stop now");
                mBufferOut.flush();
                mBufferOut.println(outputString);
                mBufferOut.flush();
                Log.d("now print",outputString);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }


    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(0xff303f9f);
        }
        loadChannels loader = new loadChannels();
        loader.execute();
        //todo timeout for channel loading
        //todo loading animation?


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_channel, menu);
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
            case R.id.action_controller:
                openController();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    public void openSettings() {
        doWrite = false;
        startActivity(new Intent(ChannelActivity.this, SettingsActivity.class));
    }

    public void openController() {
        startActivity(new Intent(ChannelActivity.this, MainActivity.class));
    }
}
