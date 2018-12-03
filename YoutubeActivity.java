package com.lilach.mediaplayer;

import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.lilach.mediaplayer.Listeners.YoutubeResultListener;
import com.lilach.mediaplayer.Objects.YoutubeObject;
import com.lilach.mediaplayer.Thread.YoutubeThread;

import java.util.ArrayList;



public class YoutubeActivity extends YouTubeBaseActivity implements YoutubeResultListener, AdapterView.OnItemClickListener {

    private ArrayList<YoutubeObject> youtubeObjects = new ArrayList<>();
    private YouTubePlayerView playerView;
    private ListView youtubeListView;
    private YouTubePlayer uPlayer;
    private YoutubeAdapter adapter;
    private int lastPosition = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube);

        youtubeListView = findViewById(R.id.youtube_listView);
        youtubeListView.setOnItemClickListener(this);

        playerView = findViewById(R.id.youtube_playerView);

        if(savedInstanceState == null) {
            String ar = getIntent().getStringExtra("ar");
            YoutubeThread thread = new YoutubeThread(ar,this);
            thread.start();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("youtubeObjects",youtubeObjects);
        outState.putInt("lastPosition",lastPosition);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        lastPosition = savedInstanceState.getInt("lastPosition",0);
        youtubeObjects = savedInstanceState.getParcelableArrayList("youtubeObjects");
    }

    @Override
    protected void onResume() {

        if(youtubeObjects.size() > 0) {
            adapter = new YoutubeAdapter(this,youtubeObjects);
            youtubeListView.setAdapter(adapter);
            playerView.initialize("video_data", new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                    uPlayer = youTubePlayer;
                    if(!b) {// if was not restored onSavedInstance state
                        youTubePlayer.loadVideo(youtubeObjects.get(0).getVideoId());
                        youTubePlayer.play();
                    }else{
                        youTubePlayer.play();
                    }
//                    }else{
//                        youTubePlayer.loadVideo(youtubeObjects.get(lastPosition).getVideoId());
//                        youTubePlayer.play();
//                    }
                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {
                    Toast.makeText(YoutubeActivity.this,"Error loading video", Toast.LENGTH_LONG).show();
                }
            });
        }
        super.onResume();
    }


    @Override
    public void onYoutubeResultComplete(ArrayList<YoutubeObject> results) {
        youtubeObjects = results;

        adapter = new YoutubeAdapter(this,youtubeObjects);
        youtubeListView.setAdapter(adapter);

        playerView.initialize("video_data", new YouTubePlayer.OnInitializedListener() {
            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                uPlayer = youTubePlayer;
                if(!b) { // if was not restored onSavedInstance state
                    youTubePlayer.loadVideo(youtubeObjects.get(0).getVideoId());
                    youTubePlayer.play();
                }
            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {
                Toast.makeText(YoutubeActivity.this,"Error loading video", Toast.LENGTH_LONG).show();
            }
        });
    }





    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {

        lastPosition = position;

        if(uPlayer != null){
            uPlayer.loadVideo(youtubeObjects.get(position).getVideoId());
            uPlayer.play();
        }else {
            playerView.initialize("video_data", new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                        uPlayer = youTubePlayer;
                        youTubePlayer.loadVideo(youtubeObjects.get(position).getVideoId());
                        youTubePlayer.play();
                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {
                    Toast.makeText(YoutubeActivity.this,"Error loading video", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
