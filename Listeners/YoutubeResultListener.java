package com.lilach.mediaplayer.Listeners;

import com.lilach.mediaplayer.Objects.YoutubeObject;
import java.util.ArrayList;

public interface YoutubeResultListener {
    public void onYoutubeResultComplete(ArrayList<YoutubeObject> results);
}
