package com.lilach.mediaplayer.Thread;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.google.gson.JsonObject;
import com.lilach.mediaplayer.Listeners.YoutubeResultListener;
import com.lilach.mediaplayer.Objects.YoutubeObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class YoutubeThread extends Thread {

    private String request;
    private YoutubeResultListener listener;
    private Handler handler;


    public YoutubeThread(String request, YoutubeResultListener listener) {
        this.request = request;
        this.listener = listener;
        handler = new Handler();
    }


    @Override
    public void run() {

        HttpsURLConnection connection = null;

        try {

            URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&q="+request+"&safeSearch=strict&type=video&maxResults=50&key=AIzaSyC6_FlT_q1cvQ5ybt515EPz5_9pQhYzcQU");
            connection = (HttpsURLConnection) url.openConnection();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String data = "";
            String line = null;

            while ((line = reader.readLine()) != null) {
                data += line;
            }
            Log.d("meir",data);

            connection.disconnect();
            final ArrayList<YoutubeObject> objects = getObjectArray(data);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onYoutubeResultComplete(objects);
                }
            });

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<YoutubeObject> getObjectArray(String data) {

        ArrayList<YoutubeObject> objects = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(data);
            JSONArray items = jsonObject.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                String videoId="";

                JSONObject id = items.getJSONObject(i).getJSONObject("id");
                if(id.has("videoId")){
                    videoId = id.getString("videoId");
                }else if (id.has("channelId")){
                    videoId = id.getString("channelId");
                }else if (id.has("playlistId")){
                    videoId = id.getString("playlistId");
                }

                JSONObject snippet = items.getJSONObject(i).getJSONObject("snippet");
                String title = snippet.getString("title");
                String description = snippet.getString("description");
                JSONObject thumbnails = snippet.getJSONObject("thumbnails");
                JSONObject defaultImage = thumbnails.getJSONObject("default");
                String imgUrl = defaultImage.getString("url");

                objects.add(new YoutubeObject(videoId, title, description, imgUrl));
                Log.e("meir ", title);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return objects;
    }
}
