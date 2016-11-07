package out386.afh;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.baoyz.widget.PullRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.msfjarvis.afh.Vars;

/**
 * Created by Js on 11/7/2016.
 */

class FindFiles {
    private final TextView mTextView;
    private String json = "";
    private final ScrollView sv;
    private final List<AfhFiles> filesD = new ArrayList<>();
    private AfhAdapter adapter;
    private final PullRefreshLayout pullRefreshLayout;
    private String savedID;
    private boolean sortByDate;
    private final RequestQueue queue;
    private final Context context;

    FindFiles(View rootView, RequestQueue queue) {
        this.queue = queue;
        context = rootView.getContext();

        mTextView = (TextView) rootView.findViewById(R.id.tv);
        sv = (ScrollView) rootView.findViewById(R.id.tvSv);
        ListView fileList = (ListView) rootView.findViewById(R.id.list);
        CheckBox sortCB = (CheckBox) rootView.findViewById(R.id.sortCB);


        sortCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    sortByDate = true;
                    Collections.sort(filesD,Comparators.byUploadDate);
                } else {
                    sortByDate = false;
                    Collections.sort(filesD,Comparators.byFileName);
                }
                adapter.notifyDataSetChanged();
            }
        });
        pullRefreshLayout = (PullRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);
        pullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                start(savedID);
            }
        });
        adapter = new AfhAdapter(context, R.layout.afh_items, filesD);
        fileList.setAdapter(adapter);
    }

    void start(final String did) {
        savedID = did;
        String url = String.format(new Vars().getDidEndpoint(), did);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        json = response;
                        pullRefreshLayout.setRefreshing(true);
                        List<String> fid = null;
                        try {
                            sv.setVisibility(View.GONE);
                            fid = parse();
                        } catch (Exception e) {
                            pullRefreshLayout.setRefreshing(false);
                            sv.setVisibility(View.VISIBLE);
                            mTextView.setText(String.format(context.getString(R.string.json_parse_error),  e.toString()));
                        }
                        sv.setVisibility(View.GONE);
                        if(fid != null)
                            queryDirs(fid);


                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                sv.setVisibility(View.VISIBLE);
                //pullRefreshLayout.setRefreshing(false);
                mTextView.setText(error.toString());
                start(did);
            }
        });

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(60000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(stringRequest);
    }

    private void queryDirs(List<String> did) {

        for (String url : did) {
            final String link = url;
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            pullRefreshLayout.setRefreshing(true);
                            try {
                                parseFiles(response);
                            } catch (Exception e) {
                                pullRefreshLayout.setRefreshing(false);
                                sv.setVisibility(View.VISIBLE);
                                mTextView.setText(mTextView.getText().toString() + "\n\n\n" + context.getString(R.string.json_parse_error) + link + " " + e.toString());
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    pullRefreshLayout.setRefreshing(false);
                    sv.setVisibility(View.VISIBLE);
                    mTextView.setText(mTextView.getText().toString() + "\n\n\n" + link + "  :   " + error.toString());
                }
            });

            stringRequest.setRetryPolicy(new DefaultRetryPolicy(60000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            queue.add(stringRequest);

        }

    }

    private List<String> parse() throws Exception {
        JSONObject afhJson;
        afhJson = new JSONObject(json);
        mTextView.setText("");
        List<String> fid = new ArrayList<>();
        JSONArray data = afhJson.getJSONArray("DATA");
        for (int i = 0; i < data.length(); i++) {
            fid.add(String.format(new Vars().getFlidEndpoint(), data.getJSONObject(i).getString(context.getString(R.string.flid_key))));
        }
        return fid;
    }

    private void print() {
        pullRefreshLayout.setRefreshing(false);
        if(sortByDate) {
            Collections.sort(filesD, Comparators.byUploadDate);
        } else {
            Collections.sort(filesD, Comparators.byFileName);
        }
        adapter.notifyDataSetChanged();

    }

    private void parseFiles(String Json) throws Exception {
        JSONObject fileJson = new JSONObject(Json);

        JSONObject data;
        if(fileJson.isNull("DATA"))
            return;

        // Data will be an Object, but if it is empty, it'll be an Array
        Object dataObj = fileJson.get("DATA");
        if(! (dataObj instanceof JSONObject))
            return;
        data = (JSONObject) dataObj;

        JSONArray files = null;
        if(! data.isNull("files"))
            files = data.getJSONArray("files");
        if(files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String name = file.getString("name");
                String url = file.getString("url");
                String upload_date = file.getString("upload_date");
                filesD.add(new AfhFiles(name, url, upload_date));
            }
        }
        JSONArray folders = null;
        if(! data.isNull("folders"))
            folders = data.getJSONArray("folders");

        if(folders != null) {
            List<String> foldersD = new ArrayList<>();
            for (int i = 0; i < folders.length(); i++) {
                foldersD.add("https://www.androidfilehost.com/api/?action=folder&flid=" + folders.getJSONObject(i).getString("flid"));
            }
            if(foldersD.size() > 0)
                queryDirs(foldersD);
        }
        print();
    }

}