package com.lilach.mediaplayer.Objects;

import android.os.Parcel;
import android.os.Parcelable;

public class YoutubeObject implements Parcelable {

    private String videoId,title,description,imgUrl;


    public YoutubeObject(String videoId,String title,String description,String imgUrl){
        this.videoId = videoId;
        this.title = title;
        this.description = description;
        this.imgUrl = imgUrl;
    }


    protected YoutubeObject(Parcel in) {
        videoId = in.readString();
        title = in.readString();
        description = in.readString();
        imgUrl = in.readString();
    }

    public static final Creator<YoutubeObject> CREATOR = new Creator<YoutubeObject>() {
        @Override
        public YoutubeObject createFromParcel(Parcel in) {
            return new YoutubeObject(in);
        }

        @Override
        public YoutubeObject[] newArray(int size) {
            return new YoutubeObject[size];
        }
    };


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(videoId);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(imgUrl);
    }


    @Override
    public int describeContents() {
        return 0;
    }


    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImgUrl() {
        return imgUrl;
    }
}
