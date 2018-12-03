package com.lilach.mediaplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;

import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lilach.mediaplayer.Objects.AlbumObject;
import com.lilach.mediaplayer.Objects.ArtistObject;
import com.lilach.mediaplayer.Objects.TrackObject;

import java.io.IOException;
import java.util.ArrayList;


public class MediaPlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {



    private String trackName;
    private String albumName;
    private String artistName;

    private int audioIndex = 0;
    private int artistIndex = 0;
    private int albumIndex = 0;

    private int resumePosition = 0;
    private AudioManager audioManager;
    private ArrayList<ArtistObject> artists = new ArrayList<>();
    private ArrayList<TrackObject> tracks = new ArrayList<>();
    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    private Bitmap albumImage;
    private RemoteActions remoteActionsReceiver;

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private MediaSessionManager mediaSessionManager = null;
    private MediaSessionCompat mediaSession;
    public static MediaControllerCompat.TransportControls transportControls;
    private static final int NOTIFICATION_ID = 156;

    public static final String MEDIA_FILE_URI = "mediaFile";
    public static final String MEDIA_TRACK = "trackName";
    public static final String MEDIA_ALBUM = "albumName";
    public static final String MEDIA_ARTIST = "artistName";
    public static final String MEDIA_SEEK_TO = "seek_to";
    public static final String MEDIA_ARTIST_INDEX = "artistIndex";
    public static final String MEDIA_AUDIO_INDEX = "audioIndex";
    public static final String MEDIA_ALBUM_INDEX = "albumIndex";

    public static final String PLAY_NEW_AUDIO = "playNewAudio";

    public static final String INCREASE_VOLUME = "increase";
    public static final String REDUCE_VOLUME = "reduce";

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_NEXT = "next";
    public static final String ACTION_PREVIOUS = "previous";
    public static final String ACTION_SEEK_TO = "seekToPosition";



    private IBinder iBinder = new LocalBinder();


    public class LocalBinder extends Binder {

        public MediaPlayerService getService(){
            return MediaPlayerService.this;
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }


    // ---------------------------------------------------------------


// service lifecycle methods


    @Override
    public void onCreate() {
        super.onCreate();

        callStateListener();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);

        IntentFilter playNewAudioIntentFilter = new IntentFilter(PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, playNewAudioIntentFilter);

        IntentFilter volumeIntentFilter = new IntentFilter();
        volumeIntentFilter.addAction(REDUCE_VOLUME);
        volumeIntentFilter.addAction(INCREASE_VOLUME);
        registerReceiver(volumeReceiver, volumeIntentFilter);

        remoteActionsReceiver = new RemoteActions();
        IntentFilter actionIntentFilter = new IntentFilter();
        actionIntentFilter.addAction(ACTION_PLAY);
        actionIntentFilter.addAction(ACTION_PAUSE);
        actionIntentFilter.addAction(ACTION_PREVIOUS);
        actionIntentFilter.addAction(ACTION_NEXT);
        actionIntentFilter.addAction(ACTION_STOP);
        actionIntentFilter.addAction(ACTION_SEEK_TO);
        registerReceiver(remoteActionsReceiver,actionIntentFilter);

        albumImage = BitmapFactory.decodeResource(getResources(),R.drawable.headset);

        settings = getSharedPreferences("songData",MODE_PRIVATE);
        editor = settings.edit();
        Gson gson = new Gson();
        artists = gson.fromJson(settings.getString("artistList",null),new TypeToken<ArrayList<ArtistObject>>(){}.getType());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // get file name from intent extras
        try {
            mediaFile = intent.getExtras().getString(MEDIA_FILE_URI,settings.getString("lastSong",""));
            artistName = intent.getExtras().getString(MEDIA_ARTIST,settings.getString("artistName",""));
            albumName  = intent.getExtras().getString(MEDIA_ALBUM,settings.getString("albumName", ""));
            trackName  = intent.getExtras().getString(MEDIA_TRACK,settings.getString("nowPlaying",""));

            resumePosition = intent.getExtras().getInt(MEDIA_SEEK_TO,settings.getInt("seekTo",0));
            albumIndex = intent.getExtras().getInt(MEDIA_ALBUM_INDEX,settings.getInt("albumIndex",1));
            artistIndex = intent.getExtras().getInt(MEDIA_ARTIST_INDEX,settings.getInt("artistIndex",0));
            audioIndex = intent.getExtras().getInt(MEDIA_AUDIO_INDEX,settings.getInt("audioIndex",0));

        }catch (NullPointerException e){  // caught if intent has no extras
            stopSelf();  // stop the service
        }

        // ask for audioFocus
        if(requestAudioFocus() == false){
            // could not gain focus, have to stop service
            stopSelf();
        }

        if(mediaSessionManager == null){
            initMediaSession();
            if(!mediaFile.isEmpty()) {
                initMediaPlayer();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }

        return START_NOT_STICKY;
    }



    @Override
    public void onDestroy() {

        super.onDestroy();

        if(mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
            stopSelf();
        }

        if(phoneStateListener != null){
            telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_NONE);
        }
        // unregister receiver
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(remoteActionsReceiver);
        unregisterReceiver(volumeReceiver);
        unregisterReceiver(playNewAudio);
        removeAudioFocus();
    }

// --------------------------------------------------------------------

 // implementing MediaPlayer listeners (complete, error, seek, prepare buffering Updates)


    @Override
    public void onPrepared(MediaPlayer mp) {   // called when the media file is ready to be played.
        playMedia();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

        switch (focusChange){
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) {
                    initMediaPlayer();
                }else if(!mediaPlayer.isPlaying()){
                    playMedia();
                }else {
                    mediaPlayer.setVolume(1.0f, 1.0f);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:  //  Audio focus is lost by the app. You should stop playing the sound and release any assets acquired to play the sound.

                if(mediaPlayer.isPlaying()){
                   stopMedia();
                }

                mediaPlayer.release();
                mediaPlayer = null;

                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:  // Audio focus is lost by the app for a short period of time. You should pause the audio.

                if(mediaPlayer.isPlaying()){
                    pauseMedia();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:   // Audio focus is lost by the app for a short period of time. You can continue to play the audio but should lower the volume.

                if (mediaPlayer.isPlaying()){
                    mediaPlayer.setVolume(0.1f, 0.1f);
                }
                break;
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) { // called when media has reached the end of audio file
          nextTrack();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        switch(what){
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                break;

            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                break;

            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:   // unsupported audio file.
                break;

            case MediaPlayer.MEDIA_ERROR_UNKNOWN:       // Unspecified media player error.
                break;

            case MediaPlayer.MEDIA_ERROR_IO:            // File or network related operation errors.
                break;
        }
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        

    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }


// -----------------------------------------------------------------


// creating a MediaPlayer instance


    private MediaPlayer mediaPlayer;
    private String mediaFile;

    private void initMediaPlayer(){

        if(mediaPlayer != null){
           mediaPlayer.reset();
           mediaPlayer.release();
           mediaPlayer = null;
       }
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnInfoListener(this);

        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            mediaPlayer.setDataSource(mediaFile);

        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        mediaPlayer.prepareAsync();

    }


    public int getMediaProgress(){
        return mediaPlayer.getCurrentPosition();
    }

    public void setMaxVolume(){
        mediaPlayer.setVolume(1.0f,1.0f);
    }

    public void playMedia(){
        if(!mediaPlayer.isPlaying()){
            if(resumePosition != 0) {
                mediaPlayer.seekTo(resumePosition);
            }
            mediaPlayer.start();
        }
    }

    public void seekToPosition(int position){
        if(mediaPlayer.isPlaying()){
            mediaPlayer.seekTo(position);
        }else{
            resumePosition = position;
        }
    }

    public void stopMedia(){

        if (mediaPlayer == null) return;
        if(mediaPlayer.isPlaying()){
            mediaPlayer.stop();
        }
    }


    public void pauseMedia(){
        if(mediaPlayer.isPlaying()){
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
            sendBroadcast(new Intent(ACTION_PAUSE));
        }
    }


    private void resumeMedia(int resumePosition){
        if(!mediaPlayer.isPlaying()){
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            sendBroadcast(new Intent(ACTION_PLAY));
        }
    }



// --------------------------------------------------------------


// init AudioManager by requesting focus


    private boolean requestAudioFocus(){
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);

        if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            return true;
        }else {
            return false;
        }
    }


    private boolean removeAudioFocus(){
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }



// --------------------------------------------------------------

// creating and listening to broadcastReceivers


    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // reduce audio volume when AudioManager.ACTION_AUDIO_BECOMING_NOISY
            if(mediaPlayer.isPlaying()){
                mediaPlayer.setVolume(0.5f,0.5f);
            }
        }
    };

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            mediaFile = intent.getExtras().getString(MEDIA_FILE_URI);
            resumePosition = 0;
            artistName = intent.getExtras().getString(MEDIA_ARTIST);
            albumName  = intent.getExtras().getString(MEDIA_ALBUM);
            trackName  = intent.getExtras().getString(MEDIA_TRACK);
            albumIndex = intent.getExtras().getInt(MEDIA_ALBUM_INDEX);
            artistIndex = intent.getExtras().getInt(MEDIA_ARTIST_INDEX);
            audioIndex = intent.getExtras().getInt(MEDIA_AUDIO_INDEX);
            initMediaPlayer();
            if(mediaSession != null){
                updateMediaData();
                buildNotification(PlaybackStatus.PLAYING);
            }else{
                initMediaSession();
            }
        }
    };


    BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(mediaPlayer != null){
                if(intent.getAction().equalsIgnoreCase(REDUCE_VOLUME)){
                    mediaPlayer.setVolume(0.05f, 0.05f);
                }else{
                    mediaPlayer.setVolume(1f, 1f);
                }
            }
        }
    };



    // listening to phone state (incoming calls, onGoing calls, off-hook, call ended)

    // CALL_STATE_OFF_HOOK => At least one call exists that is dialing, active, or on hold
    // CALL_STATE_IDLE     => Call ended
    // CALL_STATE_RINGING  =>  A new call arrived and is ringing or waiting. In the latter case, another call is already active.

    // handling incoming calls

    private void callStateListener(){

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {

                switch (state){
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:

                       if(mediaPlayer != null){
                           pauseMedia();
                           ongoingCall = true;
                       }
                       break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        if(mediaPlayer != null){
                            if(ongoingCall) {  //true
                                ongoingCall = false;
                                resumeMedia(resumePosition);
                            }
                        }
                }

            }

        };

        telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_CALL_STATE);

    }



// -------------------------------------------- media session  ---------------------------------------------

    public enum PlaybackStatus{
        PLAYING, PAUSED;
    }


    private void nextTrack() {

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
            audioIndex = 0;
        }else{
            audioIndex ++;
        }

        mediaFile = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getUri();
        artistName = artists.get(artistIndex).getArtistName();
        albumName  = artists.get(artistIndex).getAlbums().get(albumIndex).getName();
        trackName  = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();

        resumePosition = 0;
        trackName = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();
        sendBroadcast(new Intent("nextReceiver"));

        initMediaPlayer();
        if(mediaSession != null){
            updateMediaData();
            buildNotification(PlaybackStatus.PLAYING);
        }else{
            initMediaSession();
        }

    }



    private void lastTrack() {

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
            ArrayList<TrackObject> tracks = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks();
            audioIndex = tracks.size()-1;
        }else{
            audioIndex --;
        }
        mediaFile = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getUri();
        artistName = artists.get(artistIndex).getArtistName();
        albumName  = artists.get(artistIndex).getAlbums().get(albumIndex).getName();
        trackName  = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();

        resumePosition = 0;
        trackName = artists.get(artistIndex).getAlbums().get(albumIndex).getTracks().get(audioIndex).getName();
        sendBroadcast(new Intent("previousReceiver"));
        initMediaPlayer();
        if(mediaSession != null){
            updateMediaData();
            buildNotification(PlaybackStatus.PLAYING);
        }else{
            initMediaSession();
        }
    }



    private void initMediaSession(){
        if(mediaSessionManager != null){
            return;
        }

        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(getApplicationContext(),"AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updateMediaData();

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia(resumePosition);
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                nextTrack();
                updateMediaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                lastTrack();
                updateMediaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                seekToPosition((int)pos);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
            }
        });

    }



    private void updateMediaData() {
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
               .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,albumImage)
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                .putString(MediaMetadata.METADATA_KEY_ALBUM, albumName)
                                .putString(MediaMetadata.METADATA_KEY_TITLE, trackName)
                                .build());
    }


    private void buildNotification(PlaybackStatus playbackStatus) {

        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent playPauseAction = null;

        if(playbackStatus == PlaybackStatus.PLAYING){
            notificationAction = android.R.drawable.ic_media_pause;
            playPauseAction = playbackAction(1);
        }else if(playbackStatus == PlaybackStatus.PAUSED){
            notificationAction = android.R.drawable.ic_media_play;
            playPauseAction = playbackAction(0);
        }

        Notification nBuilder = new NotificationCompat.Builder(this, "audio_")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentText(artistName)
                .setContentTitle(albumName)
                .setContentInfo(trackName)
                .setLargeIcon(albumImage)
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0,1,2,3))
                .setColor(getResources().getColor(R.color.colorPrimary))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"close",playbackAction(4))
                .addAction(android.R.drawable.ic_media_previous,"previous",playbackAction(3))
                .addAction(android.R.drawable.ic_media_pause,"pause",playPauseAction)
                .addAction(android.R.drawable.ic_media_next,"next",playbackAction(2)).build();

        startForeground(NOTIFICATION_ID,nBuilder);
    }


    private void removeNotification(){
        NotificationManager nManager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
        nManager.cancel(NOTIFICATION_ID);

        if(mediaPlayer != null){

            sendBroadcast(new Intent("finishTask"));

            editor.putString("lastSong", mediaFile);
            editor.putInt("seekTo", mediaPlayer.getCurrentPosition());
            editor.putInt("audioIndex",audioIndex);
            editor.putInt("artistIndex",artistIndex);
            editor.putInt("albumIndex",albumIndex);
            editor.putString("nowPlaying",trackName);
            editor.putString("artistName",artistName);
            editor.putString("albumName", albumName);
            editor.commit();

            stopMedia();
            mediaPlayer.release();
            mediaPlayer = null;
            stopSelf();
        }
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this,RemoteActions.class);

        switch(actionNumber){
            case 0:
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getBroadcast(MediaPlayerService.this,actionNumber,playbackAction,0);

            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getBroadcast(this,actionNumber,playbackAction,0);

            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getBroadcast(this,actionNumber,playbackAction,0);

            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getBroadcast(this,actionNumber,playbackAction,PendingIntent.FLAG_UPDATE_CURRENT);

            case 4:
                playbackAction.setAction(ACTION_STOP);
                return PendingIntent.getBroadcast(this,actionNumber,playbackAction,0);

            default:
                break;
        }

        return null;
    }



    public static class RemoteActions extends BroadcastReceiver{

        public RemoteActions(){
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String actionString = intent.getAction();

            if(actionString.equalsIgnoreCase(ACTION_PLAY)){
                transportControls.play();
            }else if(actionString.equalsIgnoreCase(ACTION_PAUSE)){
                transportControls.pause();
            }else if(actionString.equalsIgnoreCase(ACTION_NEXT)){
                transportControls.skipToNext();
            }else if(actionString.equalsIgnoreCase(ACTION_PREVIOUS)){
                transportControls.skipToPrevious();
            }else if(actionString.equalsIgnoreCase(ACTION_STOP)){
                transportControls.stop();
            }else if(actionString.equalsIgnoreCase(ACTION_SEEK_TO)){
                long milli = intent.getLongExtra("seekTo",0);
                transportControls.seekTo(milli);
            }
        }
    }


}
