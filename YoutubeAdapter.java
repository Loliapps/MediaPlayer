package com.lilach.mediaplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.lilach.mediaplayer.Objects.YoutubeObject;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;


public class YoutubeAdapter extends ArrayAdapter<YoutubeObject> {

    private ArrayList<YoutubeObject> objects;
    private Context context;


    public YoutubeAdapter(@NonNull Context context, @NonNull ArrayList<YoutubeObject> objects) {
        super(context, R.layout.youtube_list_item,objects);
        this.context = context;
        this.objects = objects;
    }


    private class ViewHolder{
        TextView title, description;
        ImageView thumbnail;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        ViewHolder holder = null;

        if (convertView == null){
            holder = new ViewHolder();
            convertView = LayoutInflater.from(context).inflate(R.layout.youtube_list_item,null);

            holder.title = convertView.findViewById(R.id.video_title);
            holder.description = convertView.findViewById(R.id.video_description);
            holder.thumbnail = convertView.findViewById(R.id.video_thumbnail);

            convertView.setTag(holder);
        }else{
            holder = (ViewHolder) convertView.getTag();
        }

        holder.title.setText(objects.get(position).getTitle());
        holder.description.setText(objects.get(position).getDescription());

        Picasso.get()
                .load(objects.get(position).getImgUrl())
                .error(R.drawable.default_video_thumbnail)
                .into(holder.thumbnail);

        return convertView;

    }
}
