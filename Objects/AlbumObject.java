package com.lilach.mediaplayer.Objects;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;



public class AlbumObject implements Parcelable {

    String name;
    ArrayList<TrackObject> tracks;

    public AlbumObject(String name, ArrayList<TrackObject> tracks){
        this.name = name;
        this.tracks = tracks;
    }

    protected AlbumObject(Parcel in) {
        name = in.readString();
    }

    public static final Creator<AlbumObject> CREATOR = new Creator<AlbumObject>() {
        @Override
        public AlbumObject createFromParcel(Parcel in) {
            return new AlbumObject(in);
        }

        @Override
        public AlbumObject[] newArray(int size) {
            return new AlbumObject[size];
        }
    };

    public String getName() {
        return name;
    }

    public ArrayList<TrackObject> getTracks() {
        return tracks;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeTypedList(tracks);
    }
}
