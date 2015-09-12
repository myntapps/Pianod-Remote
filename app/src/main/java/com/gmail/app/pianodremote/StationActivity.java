

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

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import com.melnykov.fab.FloatingActionButton;

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



public class StationActivity extends AppCompatActivity {


    ListView stationListView;
    List<String> stationList = new ArrayList<>();
    boolean stationsListed = false;
    boolean doWrite = true;
    boolean readStations = true;
    String outputString = "stations list";
    boolean failedStationList = false;
    public FloatingActionButton fabChannel;
    public String searchQuery;
    public ArrayList<addableStation> foundStationsList = new ArrayList<>();
    boolean doStop = true;
    public ProgressBar progressSpinner1;



    public void addChannel(View view) {
        final SearchView searchView = (SearchView) findViewById(R.id.searchView);
        searchView.setVisibility(View.VISIBLE);
        //stationListView.setAdapter(null);
        searchView.setIconified(false);
        searchView.requestFocus();

        Toolbar toolbarStation = (Toolbar) findViewById(R.id.toolbarstation);
        setSupportActionBar(toolbarStation);
        toolbarStation.setTitle("Add New Station");
        fabChannel.hide();
        //fabChannel.setVisibility(View.INVISIBLE);
        stationListView.setAdapter(null);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                stationListView.setAdapter(null);
                searchView.clearFocus();
                progressSpinner1 = (ProgressBar) findViewById(R.id.progressBar1);
                progressSpinner1.setVisibility(ProgressBar.VISIBLE);
                callSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return true;
            }

            public void callSearch(String query) {
                Log.e(query,query);
                stationListView.setAdapter(null);
                searchQuery = "FIND ANY \"" + query + "\"";
                Log.e("query",searchQuery);
                searchStation search = new searchStation();
                search.execute();
            }
        });
    }

    private class loadStations extends AsyncTask<Void, Void, Void> {
        private Socket socket;
        @Override
        protected Void doInBackground(Void... params) {

            failedStationList = false;

            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StationActivity.this);
                String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                Log.d("socket details", socketIP + " " + socketPort);
                socket = new Socket();
                socket.setSoTimeout(2000);
                socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                Log.d("finished socket details", socketIP + " " + socketPort);
            } catch (IOException e) {
                Log.e("failed to make socket","failed");
                failedStationList = true;
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
                boolean firstStation = true;
                while (readStations) {
                    if (mBufferIn.ready()) {
                        String outPut = mBufferIn.readLine();
                        String outPutCode = outPut.substring(0,3);

                        //station is 115
                        if (outPutCode.equals("115")) {
                            //skip first or else we get a duplicate
                            if(firstStation) {
                                firstStation = false;
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
            setContentView(R.layout.activity_station);
            Toolbar toolbarStation = (Toolbar) findViewById(R.id.toolbarstation);
            setSupportActionBar(toolbarStation);
            toolbarStation.setTitle("Stations");
            stationListView = (ListView) findViewById(R.id.listView);
            fabChannel = (FloatingActionButton) findViewById(R.id.fabChannel);
            fabChannel.show();
            fabChannel.setVisibility(View.VISIBLE);
            fabChannel.attachToListView(stationListView);
            progressSpinner1 = (ProgressBar) findViewById(R.id.progressBar1);
            progressSpinner1.setVisibility(ProgressBar.INVISIBLE);
            String[] testArray = new String[stationList.size()];
            Collections.sort(stationList);
            stationList.toArray(testArray);
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getBaseContext(), android.R.layout.simple_list_item_1, testArray);
            stationListView.setAdapter(arrayAdapter);
            stationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    String item = ((TextView) view).getText().toString();
                    outputString = "play station \"" + item + "\"";
                    Log.d("test", outputString);
                    runStation executor = new runStation();
                    doStop = true;
                    executor.execute();
                    openController();

                }
            });
            stationListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    final String item = ((TextView) view).getText().toString();
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which){
                                case DialogInterface.BUTTON_POSITIVE:
                                    outputString = "delete station \"" + item + "\"";
                                    Log.d("test", outputString);
                                    runStation executor = new runStation();
                                    doStop = false;
                                    executor.execute();
                                    openStations();
                                    break;

                                case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    break;
                            }
                        }
                    };

                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    builder.setMessage("Are you sure you want to delete that channel?").setPositiveButton("Yes", dialogClickListener)
                            .setNegativeButton("No", dialogClickListener).show();

                    return true;
                }
            });
        }
    }

    private class runStation extends AsyncTask<Void, Void, Void> {
        private Socket socket;

        @Override
        protected Void doInBackground(Void... params) {

            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StationActivity.this);
                String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                Log.d("socket details", socketIP + " " + socketPort);
                socket = new Socket();
                socket.setSoTimeout(2000);
                socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                Log.d("finished socket details", socketIP + " " + socketPort);
            } catch (IOException e) {
                Log.e("failed to make socket","failed");
                failedStationList = true;
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

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StationActivity.this);
                String username = prefs.getString("prefUserName", "admin");
                String password = prefs.getString("prefUserPassword", "admin");
                mBufferOut.println("USER " + username + " " + password);
                mBufferOut.flush();
                if (doStop) {
                    mBufferOut.println("stop now");
                    mBufferOut.flush();
                }
                mBufferOut.println(outputString);
                mBufferOut.flush();
                Log.d("now print",outputString);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }


    }

    private class searchStation extends AsyncTask<Void, Void, Void> {
        private Socket socket;

        @Override
        protected Void doInBackground(Void... params) {
            foundStationsList.clear();
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StationActivity.this);
                String socketIP = prefs.getString("prefSocketIP", "192.168.1.100");
                int socketPort = Integer.valueOf(prefs.getString("prefSocketPort", "4445"));
                Log.d("socket details", socketIP + " " + socketPort);
                socket = new Socket();
                socket.setSoTimeout(2000);
                socket.connect(new InetSocketAddress(socketIP, socketPort), 2000);
                Log.d("finished socket details", socketIP + " " + socketPort);
            } catch (IOException e) {
                Log.e("failed to make socket","failed");
                failedStationList = true;
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
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StationActivity.this);
                String username = prefs.getString("prefUserName", "admin");
                String password = prefs.getString("prefUserPassword", "admin");
                mBufferOut.println("USER " + username + " " + password);
                mBufferOut.flush();
                mBufferOut.println(searchQuery);
                mBufferOut.flush();

                Boolean searchStation = true;


                addableStation currentStation = new addableStation("","","","");
                boolean firstStation = true;
                while (searchStation) {
                    if (mBufferIn.ready()) {
                        String outPut = mBufferIn.readLine();
                        String outPutCode = outPut.substring(0,3);
                        String[][] foundStations = new String[3][];
                        if (outPutCode.equals("114") | outPutCode.equals("113") | outPutCode.equals("111") | outPutCode.equals("203") | outPutCode.equals("115")){
                            //skip first or else we get a duplicate
                            if(firstStation) {
                                if(outPutCode.equals("203")) {
                                    firstStation = false;
                                }
                            }
                            else {
                                switch (outPutCode) {
                                    case "203":
                                        foundStationsList.add(new addableStation(currentStation.stationID, currentStation.stationArtist, currentStation.stationSong, currentStation.stationStation));
                                        currentStation.stationArtist = "";
                                        currentStation.stationID = "";
                                        currentStation.stationSong = "";
                                        currentStation.stationStation = "";
                                        break;
                                    case "111":
                                        currentStation.stationID = outPut.substring(8);
                                        //Log.d("got 111 set station to", currentStation.stationID);
                                        break;
                                    case "113":
                                        currentStation.stationArtist = outPut.substring(12);
                                        //Log.d("got 113 set station to", currentStation.stationArtist);
                                        break;
                                    case "114":
                                        currentStation.stationSong = outPut.substring(11);
                                        //Log.d("got 114 set station to", currentStation.stationSong);
                                        break;
                                    case "115":
                                        currentStation.stationStation = outPut.substring(13);
                                        Log.d("got 114 set station to", currentStation.stationStation);
                                        break;
                                }
                            }
                        }
                        //station is 204
                        else if (outPutCode.equals("204")) {
                            //Log.d("got 204 set station to", " ");
                            searchStation = false;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }


        @Override
        protected void onPostExecute(Void v) {
            Log.e("made post search", "yay");
            stationListView = (ListView) findViewById(R.id.listView);
            String[] testArray = new String[stationList.size()];
            //stationList.toArray(testArray);
            Log.e("made post search", "ya2y");
            fabChannel = (FloatingActionButton) findViewById(R.id.fabChannel);
            fabChannel.setVisibility(View.GONE);
            progressSpinner1 = (ProgressBar) findViewById(R.id.progressBar1);
            progressSpinner1.setVisibility(ProgressBar.INVISIBLE);
            stationAdapter addStationArrayAdapter = new stationAdapter(StationActivity.this, R.layout.list_item, foundStationsList);
            stationListView.setAdapter(addStationArrayAdapter);
            stationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    //String item = ((TextView) view).getText().toString();
                    outputString = "CREATE STATION FROM SUGGESTION " + foundStationsList.get(i).stationID;
                    Log.d("test", Integer.toString(i));
                    Log.d("test", foundStationsList.get(i).stationArtist);
                    Log.d("test", foundStationsList.get(i).stationSong);
                    Log.d("test", outputString);
                    runStation executor = new runStation();
                    doStop = false;
                    executor.execute();
                    openStations();
                }
            });
        }

    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(0xff303f9f);
        }

        //do before loading stations
        setContentView(R.layout.activity_station);
        Toolbar toolbarStation = (Toolbar) findViewById(R.id.toolbarstation);
        toolbarStation.setTitle("Stations");
        stationListView = (ListView) findViewById(R.id.listView);
        fabChannel = (FloatingActionButton) findViewById(R.id.fabChannel);
        fabChannel.setVisibility(View.GONE);
        progressSpinner1 = (ProgressBar) findViewById(R.id.progressBar1);
        progressSpinner1.setVisibility(ProgressBar.VISIBLE);


        //load stations automatically
        loadStations loader = new loadStations();
        loader.execute();
        //todo timeout for station loading

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_station, menu);
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
            case R.id.action_stations:
                openStations();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void openSettings() {
        doWrite = false;
        startActivity(new Intent(StationActivity.this, SettingsActivity.class));
    }

    public void openController() {
        startActivity(new Intent(StationActivity.this, MainActivity.class));
    }

    public void openStations() {
        startActivity(new Intent(StationActivity.this, StationActivity.class));
    }
}
