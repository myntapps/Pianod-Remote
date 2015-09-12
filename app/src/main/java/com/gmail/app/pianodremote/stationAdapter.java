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

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class stationAdapter extends ArrayAdapter<addableStation> {
    private ArrayList<addableStation> objects;
    public stationAdapter(Context context, int textViewResourceId, ArrayList<addableStation> objects) {
        super(context, textViewResourceId, objects);
        this.objects = objects;
    }


    public View getView(int position, View convertView, ViewGroup parent){
        View v = convertView;

        if (v==null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.list_item, null);
        }
        addableStation i = objects.get(position);
        if (i != null) {
            Log.d(i.stationArtist, i.stationSong);
            TextView tt = (TextView) v.findViewById(R.id.toptext);
            TextView ttd = (TextView) v.findViewById(R.id.toptextdata);
            TextView mt = (TextView) v.findViewById(R.id.middletext);
            TextView mtd = (TextView) v.findViewById(R.id.middletextdata);

            if (i.stationSong.equals("") && i.stationStation.equals("")) {
                tt.setVisibility(View.GONE);
                ttd.setVisibility(View.GONE);
            } else {
                tt.setVisibility(View.VISIBLE);
                ttd.setVisibility(View.VISIBLE);
            }

            if (i.stationArtist.equals("")) {
                mt.setVisibility(View.GONE);
                mtd.setVisibility(View.GONE);
            } else {
                mt.setVisibility(View.VISIBLE);
                mtd.setVisibility(View.VISIBLE);
            }

            // check to see if each individual textview is null.
            // if not, assign some text!
            if (i.stationStation.equals("")){
                tt.setText("Song: ");
            } else {
                tt.setText("Genre: ");
            }
            if (i.stationStation.equals("")){
                ttd.setText(i.stationSong);
            } else {
                ttd.setText(i.stationStation);
            }
                mt.setText("Artist: ");
                mtd.setText(i.stationArtist);

        }
        return v;
    }
}
