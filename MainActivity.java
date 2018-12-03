package com.lilach.mediaplayer;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.Toast;

import com.lilach.mediaplayer.Listeners.FilterCompleteListener;
import com.lilach.mediaplayer.Listeners.OnDataAudioCompleteListener;
import com.lilach.mediaplayer.Objects.AlbumObject;
import com.lilach.mediaplayer.Objects.ArtistObject;
import com.lilach.mediaplayer.Objects.TrackObject;
import com.lilach.mediaplayer.Thread.AudioListThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements OnDataAudioCompleteListener, RecognitionListener,
        View.OnClickListener, TextToSpeech.OnInitListener, TextWatcher, FilterCompleteListener {

    private MediaPlayerService playerService;
    private boolean isServiceBound = false;

    private String firstSongUri = "";
    private String firstTrackName = "";
    private String firsArtistName = "";
    private String firsAlbumName = "";
    private String lastPlayedTrack = "";
    private String searchText = "";
    private String nowPlaying = "";
    private String albumName = "";
    private String artistName = "";
    private String artistFirstResult = "";
    private String lastCommand;

    private ImageView mic;
    private ExpandableListView artistLV;

    private boolean[] allArtistsGroups;
    private boolean [] filteredArrayGroups;
    private boolean isMicActive = false;

    private int selectedPosition = 0;
    private int seekTo = 0;
    private int albumIndex = 1;
    private int artistIndex = 0;
    private int audioIndex = 0;

    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private EditText searchArtistEt;
    private ArtistAdapter adapter;
    private InputMethodManager imm;


    private static final int EXTERNAL_STORAGE_PERMISSION_REQUEST = 123;
    private ArrayList<ArtistObject> filterList = new ArrayList<>();
    private ArrayList<ArtistObject> allArtists = new ArrayList<>();



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
                return true;
            }
        }
        return false;
    }

    private BroadcastReceiver speechCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startRecording(intent.getExtras().getString("msg"));
        }
    };


    private BroadcastReceiver finishTaskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };



// -----------------------------------------------------------------

// activity lifeCycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        artistLV = findViewById(R.id.artists_listView);

        settings = getSharedPreferences("songData",MODE_PRIVATE);
        editor = settings.edit();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(this);

        searchArtistEt = findViewById(R.id.mainActivity_search_artist_et);
        searchArtistEt.setInputType(InputType.TYPE_NULL);
        searchArtistEt.addTextChangedListener(this);
        searchArtistEt.setOnClickListener(this);

        mic = findViewById(R.id.mainActivity_mic_btn);
        mic.setOnClickListener(this);
        mic.setEnabled(false);

        registerReceiver(finishTaskReceiver,new IntentFilter("finishTask"));
        registerReceiver(speechCompleteReceiver, new IntentFilter("speechComplete"));

    }


    @Override
    public void onReadyForSpeech(Bundle params) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {
        speechRecognizer.stopListening();
    }

    @Override
    public void onError(int error) {

        if(error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH){
            if(lastCommand.contentEquals("are you the driver?")){
                speak("please answer yes or no");
            }else if (lastCommand.contentEquals("what would you like to play?")){
                speak("I\'m sorry, I didn\'t get that");
            } else if (lastCommand.contentEquals("please answer yes or no") || lastCommand.contentEquals("I\'m sorry, I didn\'t get that") || lastCommand.contentEquals("I couldn\'t find that name on the device.Would you like to search youTube for that name?")){
                mic.setEnabled(true);
                if(isServiceBound){
                    playerService.setMaxVolume();
                }
            }
        }
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> driverAnswer = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        String command = speechIntent.getExtras().getString("msg");

        if(driverAnswer != null) {

            if (command.equals("are you the driver?") || command.equals("please answer yes or no")) {
                if (driverAnswer.get(0).contentEquals("yes")) {
                    // ask for artist name
                    speak("what would you like to play?");

                } else if (driverAnswer.get(0).contentEquals("no")) {

                    searchArtistEt.setInputType(InputType.TYPE_CLASS_TEXT);
                    searchArtistEt.requestFocus();
                    sendBroadcast(new Intent(MediaPlayerService.INCREASE_VOLUME));

                    imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);

                } else {
                    speak("please answer yes or no");
                }
            } else if (command.equals("what would you like to play?") || command.equals("I\'m sorry, I didn\'t get that")) {

                ArtistObject artistObject = null;
                AlbumObject albumObject = null;
                TrackObject trackObject = null;


                if (driverAnswer.size() > 0) {
                    artistFirstResult = driverAnswer.get(0).replaceAll("'", "");
                }
                //
                for (String answer : driverAnswer) {

                    answer = answer.replace("\u200F", "").toLowerCase().trim();

                    for (ArtistObject aObject : allArtists) {
                        String an = answer.replaceAll("'", "");
                        if (aObject.getArtistName().toLowerCase().contains(an)) {
                            artistObject = aObject;
                            break;
                        }
                    }

                    if (artistObject == null) {
                        for (ArtistObject aObject : allArtists) {
                            if(artistObject != null){
                                break;
                            }else {
                                for (AlbumObject album_object : aObject.getAlbums()) {
                                    if (album_object.getName().toLowerCase().contains(answer)) {
                                        artistObject = aObject;
                                        albumObject = album_object;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if(artistObject == null) {
                        for (ArtistObject aObject : allArtists) {
                            if(artistObject != null){
                                break;
                            }else {
                                for (AlbumObject album_object : aObject.getAlbums()) {
                                    if (albumObject != null) {
                                        break;
                                    } else {
                                        if (aObject.getAlbums().get(0) == album_object) {
                                            album_object = aObject.getAlbums().get(1);
                                        }

                                        for (TrackObject track_object : album_object.getTracks()) {
                                            if (track_object.getName().toLowerCase().contains(answer)) {
                                                artistObject = aObject;
                                                albumObject = album_object;
                                                trackObject = track_object;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if(artistObject != null) {
                    if (isMyServiceRunning(MediaPlayerService.class)) {
                        if (isServiceBound) {
                            unbindService(connection);
                            isServiceBound = false;
                        }
                        Intent intent = new Intent(MainActivity.this, MediaPlayerService.class);
                        startService(intent);
                    }
                    Intent intent = new Intent(MainActivity.this, AlbumActivity.class);
                    if (albumObject != null) {
                        intent.putExtra("album_tracks", albumObject.getTracks());
                        intent.putExtra("albumIndex", artistObject.getAlbums().indexOf(albumObject));
                    } else {
                        intent.putExtra("album_tracks", artistObject.getAlbums().get(1).getTracks());
                        intent.putExtra("albumIndex", 1);
                    }
                    intent.putExtra("artistIndex", allArtists.indexOf(artistObject));
                    if (trackObject != null) {
                        intent.putExtra("audioIndex", albumObject.getTracks().indexOf(trackObject));
                    } else {
                        intent.putExtra("audioIndex", 0);
                    }

                    MainActivity.this.startActivity(intent);
                }else {
                    speak("I couldn\'t find that name on the device.Would you like to search youTube for that name?");
                }

            } else if (command.equals("I couldn\'t find that name on the device.Would you like to search youTube for that name?")) {

                if (driverAnswer.get(0).contains("yes")) {

                    if (!artistFirstResult.isEmpty()) {
                        if (isMyServiceRunning(MediaPlayerService.class)) {
                            if(isServiceBound){
                                unbindService(connection);
                                isServiceBound = false;
                            }
                            Intent intent = new Intent(MainActivity.this, MediaPlayerService.class);
                            stopService(intent);
                        }
                        artistFirstResult = artistFirstResult.replaceAll(" ","%20");

                        Intent intent = new Intent(MainActivity.this, YoutubeActivity.class);
                        intent.putExtra("ar", artistFirstResult);
                        startActivity(intent);
                    }
                } else {
                    mic.setEnabled(true);
                    if (isServiceBound) {
                        playerService.setMaxVolume();
                    }
                }
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("audio_list",filterList);
        outState.putSerializable("all_list",allArtists);
        if(allArtistsGroups != null) {
            outState.putBooleanArray("allArtistsGroups", allArtistsGroups);
        }
        if(filteredArrayGroups != null) {
            outState.putBooleanArray("filteredArrayGroups", filteredArrayGroups);
        }

        outState.putString("searchText",searchText);
        outState.putString("artistFirstResult",artistFirstResult);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {

        super.onRestoreInstanceState(savedInstanceState);
        filterList = (ArrayList<ArtistObject>) savedInstanceState.get("audio_list");
        allArtists = (ArrayList<ArtistObject>) savedInstanceState.get("all_list");
        if(savedInstanceState.getBooleanArray("allArtistsGroups") != null) {
            allArtistsGroups = savedInstanceState.getBooleanArray("allArtistsGroups");
        }
        if(savedInstanceState.getBooleanArray("filteredArrayGroups") != null) {
            filteredArrayGroups = savedInstanceState.getBooleanArray("filteredArrayGroups");
        }
        searchText = savedInstanceState.getString("searchText", "");
        artistFirstResult = savedInstanceState.getString("artistFirstResult","");
        showListView();
    }


    @Override
    protected void onPause() {

        searchArtistEt.removeTextChangedListener(this);

        if(isMyServiceRunning(MediaPlayerService.class)) {
            if (isServiceBound) {
                unbindService(connection);
                isServiceBound = false;
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {

        if(!searchText.isEmpty()){
            searchArtistEt.setInputType(InputType.TYPE_CLASS_TEXT);
            searchArtistEt.setText(searchText);
            searchArtistEt.addTextChangedListener(this);
            searchArtistEt.setSelection(searchText.length());

        }else{
            if(searchArtistEt.hasFocus()){
                searchArtistEt.clearFocus();
            }
        }
        if(allArtists.size() == 0) {
            checkReadExternalStoragePermission();
        }else {
            firstSongUri = allArtists.get(0).getAlbums().get(0).getTracks().get(0).getUri();
            firstTrackName = allArtists.get(0).getAlbums().get(0).getTracks().get(0).getName();
            firsAlbumName = allArtists.get(0).getAlbums().get(0).getName();
            firsArtistName = allArtists.get(0).getArtistName();

            if (!isMyServiceRunning(MediaPlayerService.class)) {
                seekTo = settings.getInt("seekTo", 0);
                albumIndex = settings.getInt("albumIndex", 1);
                artistIndex = settings.getInt("artistIndex", 0);
                audioIndex = settings.getInt("audioIndex", 0);
                albumName = settings.getString("albumName", firsAlbumName);
                artistName = settings.getString("artistName", firsArtistName);
                nowPlaying = settings.getString("nowPlaying", firstTrackName);
                lastPlayedTrack = settings.getString("lastSong", firstSongUri);
            }

            playMusic();
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        if(textToSpeech != null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if(speechRecognizer != null){
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }

        unregisterReceiver(finishTaskReceiver);
        unregisterReceiver(speechCompleteReceiver);

        super.onDestroy();
    }



// ------------------------------------------------------------------------


// music methods

    private void playMusic(){

        Intent playIntent = new Intent(this,MediaPlayerService.class);
        playIntent.putExtra(MediaPlayerService.MEDIA_FILE_URI, lastPlayedTrack);
        playIntent.putExtra(MediaPlayerService.MEDIA_TRACK,nowPlaying);
        playIntent.putExtra(MediaPlayerService.MEDIA_ALBUM,albumName);
        playIntent.putExtra(MediaPlayerService.MEDIA_ARTIST,artistName);
        playIntent.putExtra(MediaPlayerService.MEDIA_ARTIST_INDEX,artistIndex);
        playIntent.putExtra(MediaPlayerService.MEDIA_ALBUM_INDEX,albumIndex);
        playIntent.putExtra(MediaPlayerService.MEDIA_AUDIO_INDEX, audioIndex);
        playIntent.putExtra(MediaPlayerService.MEDIA_SEEK_TO,seekTo);
        if(!isMyServiceRunning(MediaPlayerService.class)) {
            startService(playIntent);
        }

        bindService(new Intent(this, MediaPlayerService.class), connection, 0);
        isServiceBound = true;

        mic.setEnabled(true);
    }





// ------------------------------------------------------

// permission check

    private void checkReadExternalStoragePermission() {
//TODO check audio permission
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)){

                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setMessage("This App needs access to music files that are stored on your device.")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, EXTERNAL_STORAGE_PERMISSION_REQUEST);
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                    builder.create();
                    builder.show();

                }else{
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, EXTERNAL_STORAGE_PERMISSION_REQUEST);
                }
            }else {
                new AudioListThread(MainActivity.this,this).start();
            }
        }else{
            new AudioListThread(MainActivity.this,this).start();
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == EXTERNAL_STORAGE_PERMISSION_REQUEST){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                new AudioListThread(MainActivity.this,this).start();
            }
        }else{
            createDialog();
        }
    }


    private void createDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setMessage("This App did not gain permission for accessing you music files. If you want to play music files using this app, please turn permission on in app settings.")
                .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        startActivity(intent);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.create();
        builder.show();
    }



// this method is called by the get audio thread and after screen orientation (onSavedInstanceState)

    private void showListView() {

        adapter = new ArtistAdapter(this, filterList,this);
        artistLV.setAdapter(adapter);

        if(allArtistsGroups != null && (filterList.size() == allArtists.size())) {
            for (int selected = 0; selected < allArtistsGroups.length; selected++) {
                if (allArtistsGroups[selected]) {
                    artistLV.expandGroup(selected);
                    artistLV.setSelectedGroup(selected);
                }
            }
        }else{
            if(filteredArrayGroups != null) {
                for (int selected = 0; selected < filteredArrayGroups.length; selected++) {
                    if (filteredArrayGroups[selected]) {
                        artistLV.expandGroup(selected);
                        artistLV.setSelectedGroup(selected);
                    }
                }
            }
        }

        artistLV.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
           @Override
           public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {

               String artist = filterList.get(groupPosition).getArtistName();

               for(ArtistObject aObj : allArtists){
                   if(aObj.getArtistName().equals(artist)){
                       selectedPosition = allArtists.indexOf(aObj);
                   }
               }

               if(!filteredArrayGroups[groupPosition]){
                   filteredArrayGroups[groupPosition] = true;
               }else{
                   filteredArrayGroups[groupPosition] = false;
               }

               if(!allArtistsGroups[selectedPosition]) {
                    allArtistsGroups[selectedPosition] = true;
               }else{
                    allArtistsGroups[selectedPosition] = false;
               }
               return false;
            }
        });

        artistLV.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {

                if(isMyServiceRunning(MediaPlayerService.class)) {
                    Intent serviceIntent = new Intent(MainActivity.this, MediaPlayerService.class);
                    stopService(serviceIntent);
                }
                artistIndex = selectedPosition;
                albumIndex = childPosition;

                Intent intent = new Intent(MainActivity.this,AlbumActivity.class);
                intent.putExtra("album_tracks",filterList.get(groupPosition).getAlbums().get(childPosition).getTracks());
                intent.putExtra("albumIndex",albumIndex);
                intent.putExtra("artistIndex",artistIndex);
                MainActivity.this.startActivity(intent);

                return true;
            }
        });
    }



// -----------------------------------------------------------

// listeners

    @Override
    public void onAudioListComplete(ArrayList<ArtistObject> artists) {

        filterList = artists;
        allArtists = artists;

        if(artists.size() > 0) {

            firstSongUri = artists.get(0).getAlbums().get(0).getTracks().get(0).getUri();
            firstTrackName  = artists.get(0).getAlbums().get(0).getTracks().get(0).getName();
            firsAlbumName = artists.get(0).getAlbums().get(0).getName();
            firsArtistName = artists.get(0).getArtistName();

            if (!isMyServiceRunning(MediaPlayerService.class)) {
                seekTo = settings.getInt("seekTo", 0);
                albumIndex = settings.getInt("albumIndex", 0);
                artistIndex = settings.getInt("artistIndex", 0);
                audioIndex = settings.getInt("audioIndex", 0);
                albumName  = settings.getString("albumName",firsAlbumName);
                artistName = settings.getString("artistName",firsArtistName);
                nowPlaying = settings.getString("nowPlaying",firstTrackName);
                lastPlayedTrack = settings.getString("lastSong",firstSongUri);
            }

            playMusic();

            ArtistAdapter adapter = new ArtistAdapter(this, filterList,this);
            artistLV.setAdapter(adapter);
            allArtistsGroups = new boolean[adapter.getGroupCount()];
            filteredArrayGroups = new boolean[adapter.getGroupCount()];
            for (boolean selected : allArtistsGroups) {
                selected = false;
            }
            for (boolean selected : filteredArrayGroups) {
                selected = false;
            }
            showListView();

        }else{
            Toast.makeText(this,"No music files where located.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onClick(View v) {

        if(v == searchArtistEt){
            isMicActive = false;
            if(searchArtistEt.getInputType() == InputType.TYPE_NULL) {
                sendBroadcast(new Intent(MediaPlayerService.REDUCE_VOLUME));

                if(textToSpeech != null){

                    textToSpeech.stop();
                    textToSpeech.shutdown();
                    textToSpeech = new TextToSpeech(this, this);
                }else{
                    textToSpeech = new TextToSpeech(this, this);
                }
            }
        }else{
            isMicActive = true;
            sendBroadcast(new Intent(MediaPlayerService.REDUCE_VOLUME));

            if(textToSpeech != null){
                textToSpeech.stop();
                textToSpeech.shutdown();
                textToSpeech = new TextToSpeech(this, this);
            }else{
                textToSpeech = new TextToSpeech(this, this);
            }
        }
    }

    @Override
    public void onInit(int status) {
        if(status != TextToSpeech.ERROR){
            int language = textToSpeech.setLanguage(Locale.UK);
            if(language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED){
                Toast.makeText(this,"English UK is not supported on your device",Toast.LENGTH_LONG).show();
            }else{
                textToSpeech.setSpeechRate(0.9f);
                Voice voice = new Voice("en-gb-x-fis#female_3-local", Locale.UK, 400,200, false,null);
                textToSpeech.setVoice(voice);
                if(isMicActive){
                    speak("what would you like to play?");
                    mic.setEnabled(false);
                }else{
                    speak("are you the driver?");
                }
            }

        }
    }

    private void speak(final String msg) {

        Bundle bundle = new Bundle();
        bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,msg);

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, msg);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            textToSpeech.speak(msg,TextToSpeech.QUEUE_FLUSH,bundle,TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);

        }else{
            textToSpeech.speak(msg,TextToSpeech.QUEUE_FLUSH,map);
        }


        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {

            }

            @Override
            public void onDone(String utteranceId) {
                Intent intent = new Intent("speechComplete");
                intent.putExtra("msg",msg);
                lastCommand = msg;
                sendBroadcast(intent);
            }

            @Override
            public void onError(String utteranceId) {

            }
        });

    }

    private void startRecording(String msg) {

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra("msg", msg);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500);
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        if(msg.contentEquals("what would you like to play?")){
            speechIntent.putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, new String[]{"en-US", "en-GB", "en-CA", Locale.getDefault().getLanguage()});
        }else {
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-001");

        }

        if (speechRecognizer != null) {
            speechRecognizer.startListening(speechIntent);
        }
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if(adapter != null) {
            adapter.getFilter().filter(s.toString().trim());
        }
        searchText = searchArtistEt.getText().toString().trim();
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void getFilteredList(ArrayList<ArtistObject> artists) {

        filterList = artists;
        filteredArrayGroups = new boolean[filterList.size()];
        for(Boolean selected : filteredArrayGroups){
            selected = false;
        }
        adapter.notifyDataSetChanged();
    }

}


