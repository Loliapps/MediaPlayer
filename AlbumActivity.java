package com.lilach.mediaplayer;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lilach.mediaplayer.Objects.AlbumObject;
import com.lilach.mediaplayer.Objects.ArtistObject;
import com.lilach.mediaplayer.Objects.TrackObject;


import java.util.ArrayList;

public class AlbumActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private MediaPlayerService playerService;
    private boolean isPlaying = true;
    private boolean isServiceBound = false;
    private boolean isReceivedFromService;
    private ArrayList<TrackObject> tracks = new ArrayList<>();
    private ListView trackLV;
    private TracksAdapter adapter;
    private String lastPlayedTrack = "";
    private String albumName="", artistName="";
    private int seekTo = 0;
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    private TextView albumTitle, trackTitle, durationProgress;
    private String nowPlaying;
    private Handler progressHandler = new Handler();
    private Handler seekBarHandler = new Handler();
    private Runnable progressRunnable;
    private Runnable seekBarRunnable;
    private SeekBar seekBar;
    private int seekBarMax, audioIndex, artistIndex, albumIndex;
    private ImageView nextBtn, playBtn, prevBtn;
    private android.support.v7.app.ActionBar actionBar;
    private ArrayList<ArtistObject> artists = new ArrayList<>();





    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder)service;
            playerService = binder.getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                playerService = (MediaPlayerService) getSystemService(service.service.getClassName());
                return true;
            }
        }
        return false;
    }


    private BroadcastReceiver finishTaskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopProgress();
            if(isMyServiceRunning(MediaPlayerService.class)){
                if(isServiceBound){
                    unbindService(connection);
                    isServiceBound = false;
                }
                Intent destroyIntent = new Intent(AlbumActivity.this, MediaPlayerService.class);
                stopService(destroyIntent);
            }
            finish();
        }
    };


    private BroadcastReceiver controllerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            switch (intent.getAction()){
                case "playReceiver":
                    startProgress();
                    break;
                case "pauseReceiver":
                    stopProgress();
                    break;
                case "nextReceiver":
                    isReceivedFromService = true;
                    nextTrack();
                    break;
                case "previousReceiver":
                    isReceivedFromService = true;
                    lastTrack();
                    break;
                default:
                    break;

            }
        }
    };

// --------------------------------------------------------------------

// activity lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);


        settings = getSharedPreferences("songData",MODE_PRIVATE);
        editor = settings.edit();
        if(savedInstanceState == null) {
            artistIndex = getIntent().getIntExtra("artistIndex", 0);
            albumIndex = getIntent().getIntExtra("albumIndex", 1);
            audioIndex = getIntent().getIntExtra("audioIndex", 0);
            Gson gson = new Gson();
            artists = gson.fromJson(settings.getString("artistList",null),new TypeToken<ArrayList<ArtistObject>>(){}.getType());
        }

        nextBtn =  findViewById(R.id.album_activity_next);
        playBtn =  findViewById(R.id.album_activity_play_pause);
        prevBtn =  findViewById(R.id.album_activity_prev_track);

        nextBtn.setOnClickListener(this);
        playBtn.setOnClickListener(this);
        prevBtn.setOnClickListener(this);

        trackLV =  findViewById(R.id.album_activity_LV);
        trackTitle =  findViewById(R.id.album_activity_track_title_TV);
        albumTitle =  findViewById(R.id.album_activity_album_name_TV);
        durationProgress =  findViewById(R.id.album_activity_seekBar_TV);
        seekBar =  findViewById(R.id.album_activity_seekBar);
        seekBar.setOnSeekBarChangeListener(this);

        tracks = getIntent().getParcelableArrayListExtra("album_tracks");

        if(getIntent().getParcelableArrayListExtra("album_tracks") != null){
            lastPlayedTrack = tracks.get(audioIndex).getUri();
            nowPlaying = tracks.get(audioIndex).getName();
            seekTo = 0;
            seekBarMax = tracks.get(0).getDuration();
            seekBar.setProgress(0);
            seekBar.setMax(seekBarMax);
        }


        if(artists != null && artists.size() > 0) {
            artistName = artists.get(artistIndex).getArtistName();
            albumName = artists.get(artistIndex).getAlbums().get(albumIndex).getName();
            albumTitle.setText(albumName);
        }
        actionBar = (android.support.v7.app.ActionBar) getSupportActionBar();
        actionBar.setTitle(artistName);

        registerReceiver(finishTaskReceiver,new IntentFilter("finishTask"));
        IntentFilter controllerFilter = new IntentFilter();
        controllerFilter.addAction("playReceiver");
        controllerFilter.addAction("pauseReceiver");
        controllerFilter.addAction("previousReceiver");
        controllerFilter.addAction("nextReceiver");
        registerReceiver(controllerReceiver,controllerFilter);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {

        outState.putSerializable("artistIndex",artistIndex);
        outState.putSerializable("albumIndex",albumIndex);
        outState.putSerializable("audioIndex",audioIndex);
        outState.putSerializable("artistName",artistName);
        outState.putSerializable("albumName",albumName);
        outState.putSerializable("audio_list",artists);
        outState.putSerializable("track_list",tracks);
        outState.putString("nowPlaying", nowPlaying);
        outState.putString("lastTrack", lastPlayedTrack);
        outState.putInt("progress", seekTo);
        outState.putInt("seekBarMax", seekBarMax);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        audioIndex = savedInstanceState.getInt("audioIndex",0);
        albumIndex = savedInstanceState.getInt("albumIndex",1);
        artistIndex = savedInstanceState.getInt("artistIndex",0);

        albumName = savedInstanceState.getString("albumName");
        artistName = savedInstanceState.getString("artistName");

        artists = (ArrayList<ArtistObject>) savedInstanceState.get("audio_list");
        tracks = (ArrayList<TrackObject>) savedInstanceState.get("track_list");

        nowPlaying = savedInstanceState.getString("nowPlaying","");
        lastPlayedTrack = savedInstanceState.getString("lastTrack","");
        seekBarMax = savedInstanceState.getInt("seekBarMax",260000);
        seekBar.setMax(seekBarMax);
        seekTo = savedInstanceState.getInt("progress");
    }


    @Override
    protected void onPause() {
        super.onPause();
        if(isMyServiceRunning(MediaPlayerService.class)) {
            if(progressHandler != null && progressRunnable != null){
                progressHandler.removeCallbacks(progressRunnable);
                progressHandler = null;
            }
            if(seekBarHandler != null && seekBarRunnable != null){
                seekBarHandler.removeCallbacks(seekBarRunnable);
                seekBarHandler = null;
            }
            if (isServiceBound) {
                unbindService(connection);
                isServiceBound = false;
            }
        }
    }

    @Override
    protected void onResume() {

        if(tracks.size() > 0){

            adapter = new TracksAdapter(this,tracks);
            trackLV.setAdapter(adapter);
            trackLV.setOnItemClickListener(this);

            if(nowPlaying.isEmpty()){
                nowPlaying = tracks.get(0).getName();
            }

            playMusic();

            trackTitle.setText(nowPlaying);
            albumTitle.setText(albumName);
            seekBar.setProgress(seekTo);

        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        unregisterReceiver(finishTaskReceiver);
        unregisterReceiver(controllerReceiver);

        super.onDestroy();

    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        nowPlaying = tracks.get(position).getName();
        audioIndex = position;
        lastPlayedTrack = tracks.get(position).getUri();
        seekBarMax = tracks.get(position).getDuration();
        seekBar.setMax(seekBarMax);
        seekTo = 0;
        seekBar.setProgress(seekTo);
        trackTitle.setText(nowPlaying);

        playerService.stopMedia();

        Intent playNewAudioIntent = new Intent(MediaPlayerService.PLAY_NEW_AUDIO);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_FILE_URI,lastPlayedTrack);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_ARTIST,artistName);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_ARTIST_INDEX,artistIndex);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_ALBUM,albumName);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_ALBUM_INDEX,albumIndex);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_TRACK,nowPlaying);
        playNewAudioIntent.putExtra(MediaPlayerService.MEDIA_AUDIO_INDEX,audioIndex);

        sendBroadcast(playNewAudioIntent);
        seekTo = 0;
        startProgress();

    }


    private void playMusic(){
        if(progressHandler != null && progressRunnable != null){
            progressHandler.removeCallbacks(progressRunnable);
        }
        if(seekBarHandler != null && seekBarRunnable != null){
            seekBarHandler.removeCallbacks(seekBarRunnable);
        }
        Intent playIntent = new Intent(this,MediaPlayerService.class);
        playIntent.putExtra(MediaPlayerService.MEDIA_FILE_URI, lastPlayedTrack);
        playIntent.putExtra(MediaPlayerService.MEDIA_TRACK,nowPlaying);
        playIntent.putExtra(MediaPlayerService.MEDIA_ALBUM,albumName);
        playIntent.putExtra(MediaPlayerService.MEDIA_ARTIST,artistName);

        playIntent.putExtra(MediaPlayerService.MEDIA_SEEK_TO,seekTo);
        playIntent.putExtra(MediaPlayerService.MEDIA_ALBUM_INDEX,albumIndex);
        playIntent.putExtra(MediaPlayerService.MEDIA_AUDIO_INDEX, audioIndex);
        playIntent.putExtra(MediaPlayerService.MEDIA_ARTIST_INDEX, artistIndex);

        if(!isMyServiceRunning(MediaPlayerService.class)){
            startService(playIntent);
            sendBroadcast(new Intent(MediaPlayerService.INCREASE_VOLUME));
        }

        bindService(new Intent(this,MediaPlayerService.class),connection,0);
        isServiceBound = true;

        trackTitle.setText(nowPlaying);
        albumTitle.setText(albumName);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startProgress();
            }
        },200);

    }


    private void startProgress() {

        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        if (seekBarHandler != null && seekBarRunnable != null) {
            seekBarHandler.removeCallbacks(seekBarRunnable);
        }

        seekBarRunnable = new Runnable() {
            @Override
            public void run() {
                int duration = playerService.getMediaProgress();
                seekBar.setProgress(duration);
                seekBarHandler.postDelayed(this, 200);
            }
        };
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                int duration = playerService.getMediaProgress();
                durationProgress.setText(getCurrentProgress(duration));
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
        seekBarHandler.post(seekBarRunnable);

    }


    private void stopProgress(){

        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        if (seekBarHandler != null && seekBarRunnable != null) {
            seekBarHandler.removeCallbacks(seekBarRunnable);
        }
        seekBar.setProgress(0);
    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        seekTo = progress;
        durationProgress.setText(getCurrentProgress(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

        Intent seekToIntent = new Intent(MediaPlayerService.ACTION_SEEK_TO);
        seekToIntent.putExtra("seekTo",(long)seekBar.getProgress());
        sendBroadcast(seekToIntent);
        seekBar.setProgress(seekBar.getProgress());
    }


// ----------------------------------------------------------------

    private String getCurrentProgress(int progress) {
        long min = progress / 60000 % 60000;
        long sec = progress % 60000 / 1000;
        String current = "";
        if (sec < 10) {
            current = min + " : 0" + sec;
        } else {
            current = min + " : " + sec;
        }

        return current;
    }

// ----------------------------------  buttons functionality  ---------------------------------------

    private void nextTrack() {
        playerService.stopMedia();
        stopProgress();

        if(audioIndex+1 > artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().size()-1){
            if(albumIndex+1 > artists.get(artistIndex).getAlbums().size()-1){
                if(artistIndex+1 > artists.size()-1){
                    artistIndex = 0;
                }else{
                    artistIndex ++;
                }
                albumIndex = 1;
            }else{
                albumIndex ++;
            }
            tracks = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks();
            adapter = null;
            adapter = new TracksAdapter(AlbumActivity.this,tracks);
            trackLV.setAdapter(adapter);
            audioIndex = 0;
        }else{
            audioIndex ++;
        }

        lastPlayedTrack = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getUri();
        artistName = artists.get(artistIndex).getArtistName();
        albumName  = artists.get(artistIndex).getAlbums().get(albumIndex).getName();
        nowPlaying  = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();

        seekBarMax = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getDuration();
        seekBar.setMax(seekBarMax);
        artistName = artists.get(artistIndex).getArtistName();
        albumName = artists.get(artistIndex).getAlbums().get(albumIndex).getName();

        seekTo = 0;
//        actionBar.setTitle(artistName);
//        albumTitle.setText(albumName);
        nowPlaying = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();
        trackTitle.setText(nowPlaying);

        if(!isReceivedFromService) {

            Intent nextIntent = new Intent(MediaPlayerService.PLAY_NEW_AUDIO);
            nextIntent.putExtra(MediaPlayerService.MEDIA_FILE_URI, lastPlayedTrack);
            nextIntent.putExtra(MediaPlayerService.MEDIA_ARTIST, artistName);
            nextIntent.putExtra(MediaPlayerService.MEDIA_ALBUM, albumName);
            nextIntent.putExtra(MediaPlayerService.MEDIA_TRACK, nowPlaying);
            nextIntent.putExtra(MediaPlayerService.MEDIA_ALBUM_INDEX, albumIndex);
            nextIntent.putExtra(MediaPlayerService.MEDIA_ARTIST_INDEX, artistIndex);
            nextIntent.putExtra(MediaPlayerService.MEDIA_AUDIO_INDEX, audioIndex);
            sendBroadcast(nextIntent);
        }

        startProgress();
    }



    private void lastTrack() {
        playerService.stopMedia();
        stopProgress();

        if(audioIndex-1 < 0){
            if(albumIndex-1 <= 0){
                if(artistIndex-1 < 0){
                    artistIndex = artists.size()-1;
                }else{
                    artistIndex --;
                }
                ArrayList<AlbumObject> albums = artists.get(artistIndex).getAlbums();
                albumIndex = albums.size()-1;
            }else{
                albumIndex --;
            }
            tracks = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks();
            adapter = null;
            adapter = new TracksAdapter(AlbumActivity.this,tracks);
            trackLV.setAdapter(adapter);
            audioIndex = tracks.size()-1;
        }else{
            audioIndex --;
        }

        lastPlayedTrack = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getUri();
        artistName = artists.get(artistIndex).getArtistName();
        String newAlbumName  = artists.get(artistIndex).getAlbums().get(albumIndex).getName();
        nowPlaying  = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();

        seekBarMax = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getDuration();
        seekBar.setMax(seekBarMax);
        artistName = artists.get(artistIndex).getArtistName();
        albumName = artists.get(artistIndex).getAlbums().get(albumIndex).getName();


        seekTo = 0;
//        actionBar.setTitle(artistName);
//        albumTitle.setText(albumName);
        nowPlaying = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();
        trackTitle.setText(nowPlaying);

        if(!isReceivedFromService) {

            Intent lastIntent = new Intent(MediaPlayerService.PLAY_NEW_AUDIO);
            lastIntent.putExtra(MediaPlayerService.MEDIA_FILE_URI, lastPlayedTrack);
            lastIntent.putExtra(MediaPlayerService.MEDIA_ARTIST, artistName);
            lastIntent.putExtra(MediaPlayerService.MEDIA_ALBUM, albumName);
            lastIntent.putExtra(MediaPlayerService.MEDIA_TRACK, nowPlaying);
            lastIntent.putExtra(MediaPlayerService.MEDIA_ALBUM_INDEX, albumIndex);
            lastIntent.putExtra(MediaPlayerService.MEDIA_ARTIST_INDEX, artistIndex);
            lastIntent.putExtra(MediaPlayerService.MEDIA_AUDIO_INDEX, audioIndex);
            sendBroadcast(lastIntent);
        }
        startProgress();
    }


    @Override
    public void onClick(View v) {

        switch (v.getId()){
            case R.id.album_activity_next:
                isReceivedFromService = false;
                nextTrack();
                break;

            case R.id.album_activity_play_pause:
                if(isPlaying){
                    playBtn.setImageResource(android.R.drawable.ic_media_play);
                    sendBroadcast(new Intent(MediaPlayerService.ACTION_PAUSE));
                    isPlaying = false;

                }else{
                    playBtn.setImageResource(android.R.drawable.ic_media_pause);
                    sendBroadcast(new Intent(MediaPlayerService.ACTION_PLAY));
                    isPlaying = true;
                    startProgress();
                }
                break;

            case R.id.album_activity_prev_track:
                isReceivedFromService = false;
                lastTrack();
                break;
        }
    }
}
