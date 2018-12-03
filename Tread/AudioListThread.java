package com.lilach.mediaplayer.Thread;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;

import com.google.gson.Gson;
import com.lilach.mediaplayer.Objects.AlbumObject;
import com.lilach.mediaplayer.Objects.ArtistObject;
import com.lilach.mediaplayer.Listeners.OnDataAudioCompleteListener;
import com.lilach.mediaplayer.Objects.TrackObject;

import java.util.ArrayList;


public class AudioListThread extends Thread {

    private Context context;
    private OnDataAudioCompleteListener listener;
    private Handler handler;
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;


    public AudioListThread(Context context, OnDataAudioCompleteListener listener){
        this.context = context;
        this.listener = listener;
        handler = new Handler();
        settings = context.getSharedPreferences("songData",Context.MODE_PRIVATE);
        editor = settings.edit();
    }


    @Override
    public void run() {

        final ArrayList<ArtistObject> artists = new ArrayList<>();

        ContentResolver contentResolver = context.getContentResolver();
        Uri artistName = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;   // find artist folder name
        String sortOrder = MediaStore.Audio.Artists.ARTIST + " ASC";
        final ArrayList<String> myArtists = new ArrayList<>();


        // iterate artists list
        Cursor artistCursor = contentResolver.query(artistName,new String[]{MediaStore.Audio.Artists.ARTIST},null,null,sortOrder);

        if(artistCursor != null && artistCursor.moveToFirst()){

            int aName   = artistCursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST);
            String artist_name = artistCursor.getString(aName);
            myArtists.add(artist_name);

            while (artistCursor.moveToNext()){
                artist_name = artistCursor.getString(aName);
                myArtists.add(artist_name);
            }

            artistCursor.close();
        }


        Uri albumName = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;   // find artist folder name

        for(String arName : myArtists) {

            ArrayList<String> myAlbums = new ArrayList<>();
            String order = MediaStore.Audio.Albums.ALBUM + " ASC";
            Cursor albumCursor = contentResolver.query(albumName, new String[]{MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST}, "artist=?", new String[]{arName}, order);

            if (albumCursor != null && albumCursor.moveToFirst()) {

                int alName = albumCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                if (!myAlbums.contains(albumCursor.getString(alName))) {
                    myAlbums.add(albumCursor.getString(alName));
                }

                while (albumCursor.moveToNext()) {
                    if (!myAlbums.contains(albumCursor.getString(alName))) {
                        myAlbums.add(albumCursor.getString(alName));
                    }
                }
                albumCursor.close();
            }

            ArrayList<AlbumObject> albums = new ArrayList<>();
            ArrayList<TrackObject> tracks = new ArrayList<>();

            if (myAlbums.size() > 0) {
                for (String album : myAlbums) {

                    ArrayList<TrackObject> myTracks = new ArrayList<>();
                    Uri trackName = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    Cursor trackCursor = contentResolver.query(trackName, new String[]{MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA}, "album=?", new String[]{album}, null);

                    if (trackCursor != null && trackCursor.moveToFirst()) {

                        int track = trackCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                        int tDuration = trackCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                        int location = trackCursor.getColumnIndex(MediaStore.Audio.Media.DATA);

                        String filePath = trackCursor.getString(location);
                        myTracks.add(new TrackObject(trackCursor.getString(track), trackCursor.getInt(tDuration), filePath));

                        while (trackCursor.moveToNext()) {
                            filePath = trackCursor.getString(location);
                            myTracks.add(new TrackObject(trackCursor.getString(track), trackCursor.getInt(tDuration), filePath));
                        }
                    }
                    tracks.addAll(myTracks);
                    albums.add(new AlbumObject(album, myTracks));
                }
                albums.add(0,new AlbumObject("All Songs",tracks));
                artists.add(new ArtistObject(arName, albums));
            }
        }  // end of artists loop

        Gson gson = new Gson();
        editor.putString("artistList",gson.toJson(artists));
        editor.commit();


        handler.post(new Runnable() {
            @Override
            public void run() {
                listener.onAudioListComplete(artists);
            }
        });
    }
}

