package com.lilach.mediaplayer.Objects;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;


public class TrackObject implements Parcelable{

    String name;
    int duration;
    String uri;

    public TrackObject(String name, int duration, String uri){
        this.name = name;
        this.duration = duration;
        this.uri = uri;
    }

    protected TrackObject(Parcel in) {
        name = in.readString();
        duration = in.readInt();
        uri = in.readString();
    }

    public static final Creator<TrackObject> CREATOR = new Creator<TrackObject>() {
        @Override
        public TrackObject createFromParcel(Parcel in) {
            return new TrackObject(in);
        }

        @Override
        public TrackObject[] newArray(int size) {
            return new TrackObject[size];
        }
    };

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public int getDuration() {
        return duration;
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
        dest.writeInt(duration);
        dest.writeString(uri);
    }
}
