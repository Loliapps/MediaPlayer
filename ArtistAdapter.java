package com.lilach.mediaplayer;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.lilach.mediaplayer.Listeners.FilterCompleteListener;
import com.lilach.mediaplayer.Objects.AlbumObject;
import com.lilach.mediaplayer.Objects.ArtistObject;

import java.util.ArrayList;



public class ArtistAdapter extends BaseExpandableListAdapter implements Filterable {

    private ArrayList<ArtistObject> artists;
    private ArrayList<ArtistObject> filteredArray;
    private ArtistFilter artistFilter;
    private Handler handler;
    private FilterCompleteListener listener;
    private Context context;


    public ArtistAdapter(Context context, ArrayList<ArtistObject> artists, FilterCompleteListener listener) {
        this.context = context;
        this.artists = artists;
        this.filteredArray = artists;
        this.listener = listener;
        handler = new Handler();
    }


    @Override
    public int getGroupCount() {
        return artists.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        ArrayList<AlbumObject> albums = artists.get(groupPosition).getAlbums();
        return albums.size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return artists.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        ArrayList<AlbumObject> albums = artists.get(groupPosition).getAlbums();
        return albums.get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {

        ArtistObject artistObj = (ArtistObject) getGroup(groupPosition);

        if(convertView == null){
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.artists_list_item, null);
        }

        TextView artistName  = (TextView) convertView.findViewById(R.id.artistNameTv);
        artistName.setText(artistObj.getArtistName());

        TextView alNum  = (TextView) convertView.findViewById(R.id.artists_album_num);
        alNum.setText((artistObj.getAlbums().size()-1) + " Albums");

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

        AlbumObject albumObj = (AlbumObject) getChild(groupPosition,childPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.album_list_item, null);
        }

        TextView albumName = (TextView) convertView.findViewById(R.id.albumNameTv);
        albumName.setText(albumObj.getName());

        return convertView;

    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }


    @Override
    public Filter getFilter(){

        if(artistFilter == null){
            artistFilter = new ArtistFilter();
        }
        return artistFilter;
    }


    private class ArtistFilter extends Filter{

        @Override
        protected FilterResults performFiltering(CharSequence searchText) {

            FilterResults fResult = new FilterResults();
            ArrayList<ArtistObject> new_artist_result = new ArrayList<>();

           if(searchText != null && searchText.length() > 0){

                for(ArtistObject artistObject : filteredArray) {
                    if (artistObject.getArtistName().contains(searchText)) {
                        new_artist_result.add(artistObject);
                    }
                }
                if (new_artist_result.size() > 0) {
                    fResult.count = new_artist_result.size();
                    fResult.values = new_artist_result;
                }else{
                    fResult.count = 0;
                }

            }else{
                fResult.count = filteredArray.size();
                fResult.values = filteredArray;
            }

            return fResult;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if(results.count > 0) {
                artists = (ArrayList<ArtistObject>) results.values;
                notifyDataSetChanged();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.getFilteredList(artists);
                    }
                });
            }
        }
    }
}
