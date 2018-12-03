package com.lilach.mediaplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.lilach.mediaplayer.Objects.TrackObject;

import java.util.ArrayList;

public class TracksAdapter extends ArrayAdapter<TrackObject> {

    private ArrayList<TrackObject> tracks;
    private Context context;


    public TracksAdapter(@NonNull Context context, ArrayList<TrackObject> tracks) {
        super(context, R.layout.track_list_item, tracks);
        this.context = context;
        this.tracks = tracks;
    }


    private class ViewHolder{
        TextView trackName;
        TextView duration;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        ViewHolder holder = null;

        if(convertView == null){
            holder = new ViewHolder();
            convertView = LayoutInflater.from(context).inflate(R.layout.track_list_item,null);
            holder.trackName = (TextView) convertView.findViewById(R.id.trackNameTv);
            holder.duration  = (TextView) convertView.findViewById(R.id.track_duration);
            convertView.setTag(holder);
        }else{
            holder = (ViewHolder) convertView.getTag();
        }

        String mns = (tracks.get(position).getDuration() / 60000) % 60000 + "";
        String scs = (tracks.get(position).getDuration()% 60000 / 1000) + "";

        if(Integer.parseInt(scs) < 10){
            scs = "0"+scs;
        }

        holder.trackName.setText(tracks.get(position).getName());
        holder.duration.setText(mns+" : "+scs);
        return convertView;
    }
}
