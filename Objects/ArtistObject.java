package com.lilach.mediaplayer.Objects;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;


public class ArtistObject implements Parcelable{

    String artistName;
    ArrayList<AlbumObject> albums;


    public ArtistObject(String artistName, ArrayList<AlbumObject> albums){
        String artist = artistName.toLowerCase();

        artist = artist.replaceAll("[.&!]","");

        this.artistName = artist.toLowerCase();
        this.albums = albums;
    }


    protected ArtistObject(Parcel in) {
        artistName = in.readString();
        albums = in.createTypedArrayList(AlbumObject.CREATOR);
    }

    public static final Creator<ArtistObject> CREATOR = new Creator<ArtistObject>() {
        @Override
        public ArtistObject createFromParcel(Parcel in) {
            return new ArtistObject(in);
        }

        @Override
        public ArtistObject[] newArray(int size) {
            return new ArtistObject[size];
        }
    };

    public String getArtistName() {
        return artistName;
    }

    public ArrayList<AlbumObject> getAlbums() {
        return albums;
    }

    @Override
    public String toString() {
        return artistName;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(artistName);
        dest.writeTypedList(albums);
    }
}
