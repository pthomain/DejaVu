package uk.co.glass_software.android.cache_interceptor.demo;


import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.Observable;
import uk.co.glass_software.android.cache_interceptor.demo.model.WeatherList;
import uk.co.glass_software.android.cache_interceptor.interceptors.cache.CacheToken;
import uk.co.glass_software.android.cache_interceptor.interceptors.error.ApiError;
import uk.co.glass_software.android.cache_interceptor.utils.Action;

class ExpandableListAdapter extends BaseExpandableListAdapter {
    
    private final Action onComplete;
    private final LinkedList<String> headers;
    private final LinkedList<String> logs;
    private final LinkedHashMap<String, List<String>> children;
    private final LayoutInflater inflater;
    private final SimpleDateFormat simpleDateFormat;
    
    ExpandableListAdapter(Context context,
                          Action onComplete) {
        this.onComplete = onComplete;
        headers = new LinkedList<>();
        logs = new LinkedList<>();
        children = new LinkedHashMap<>();
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        simpleDateFormat = new SimpleDateFormat("hh:mm:ss");
    }
    
    void loadCity(Observable<WeatherList> observable) {
        headers.clear();
        children.clear();
        logs.clear();
        long start = System.currentTimeMillis();
        observable
                .doOnComplete(() -> onComplete(start))
                .subscribe(weathers -> onNext( start, weathers));
    }
    
    private void onNext(long start,
                        WeatherList weathers) {
        CacheToken<WeatherList> cacheToken = weathers.getMetadata().getCacheToken();
        String ellapsed = " (" + cacheToken.getStatus() + ", " + (System.currentTimeMillis() - start) + "ms)";
        List<String> info = new ArrayList<>();
        String header;
        
        if (weathers.getMetadata().hasError()) {
            header = "An error occurred" + ellapsed;
            
            ApiError error = weathers.getMetadata().getError();
            info.add("Description: " + error.getDescription());
            info.add("Message: " + error.getMessage());
            info.add("Cause: " + error.getCause());
        }
        else {
            header = weathers.size() + " result(s)" + ellapsed;
            
            info.add("Cache token status: " + cacheToken.getStatus());
            info.add("Cache token cache date: " + simpleDateFormat.format(cacheToken.getCacheDate()));
            info.add("Cache token expiry date: " + simpleDateFormat.format(cacheToken.getExpiryDate()));
            
            for (int i = 0; i < weathers.size(); i++) {
                info.add("Weather " + i + ": " + weathers.get(i));
            }
        }
        
        headers.add(header);
        children.put(header, info);
        
        notifyDataSetChanged();
    }
    
    void log(String output) {
        logs.addLast(output);
    }
    
    private void onComplete(long start) {
        String header = "Log output (total: " + (System.currentTimeMillis() - start) + "ms)";
        headers.add(header);
        children.put(header, logs);
        notifyDataSetChanged();
        onComplete.act();
    }
    
    @Override
    public Object getChild(int groupPosition,
                           int childPosititon) {
        String key = headers.get(groupPosition);
        return children.get(key).get(childPosititon);
    }
    
    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }
    
    @Override
    public View getGroupView(int groupPosition,
                             boolean isExpanded,
                             View convertView,
                             ViewGroup parent) {
        String headerTitle = (String) getGroup(groupPosition);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_group, null);
        }
        
        TextView lblListHeader = convertView.findViewById(R.id.lblListHeader);
        lblListHeader.setTypeface(null, Typeface.BOLD);
        lblListHeader.setText(headerTitle);
        
        return convertView;
    }
    
    @Override
    public View getChildView(int groupPosition,
                             final int childPosition,
                             boolean isLastChild,
                             View convertView,
                             ViewGroup parent) {
        final String childText = (String) getChild(groupPosition, childPosition);
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_item, null);
        }
        
        TextView txtListChild = convertView.findViewById(R.id.lblListItem);
        
        txtListChild.setText(childText);
        return convertView;
    }
    
    @Override
    public int getChildrenCount(int groupPosition) {
        return children.get(headers.get(groupPosition)).size();
    }
    
    @Override
    public Object getGroup(int groupPosition) {
        return headers.get(groupPosition);
    }
    
    @Override
    public int getGroupCount() {
        return headers.size();
    }
    
    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }
    
    @Override
    public boolean hasStableIds() {
        return false;
    }
    
    @Override
    public boolean isChildSelectable(int groupPosition,
                                     int childPosition) {
        return false;
    }
}