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

public class addableStation {
    //holds data about stations
    public String stationID;
    public String stationArtist;
    public String stationSong;
    public String stationStation;

    public addableStation( String a, String b, String c, String d){
        stationID = a;
        stationArtist = b;
        stationSong = c;
        stationStation = d;
    }
}