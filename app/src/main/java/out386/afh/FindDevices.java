package out386.afh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Js on 11/7/2016.
 */

class FindDevices {

    private final String TAG = "TAG";
    private View rootView;
    private RequestQueue queue;
    private final PullRefreshLayout deviceRefreshLayout;
    private List<Device> devices = new ArrayList<>();
    private int currentPage = 1;
    private DeviceAdapter devAdapter;
    private int pages[];
    private FindFiles findFiles;

    FindDevices(final View rootView, final RequestQueue queue) {
        this.rootView = rootView;
        this.queue = queue;
        deviceRefreshLayout = (PullRefreshLayout) rootView.findViewById(R.id.deviceRefresh);
        devAdapter = new DeviceAdapter(rootView.getContext(), R.layout.device_items, devices);
        ListView deviceList = (ListView) rootView.findViewById(R.id.deviceList);
        findFiles = new FindFiles(rootView, queue);
        deviceList.setAdapter(devAdapter);

        deviceRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                devices = new ArrayList<>();
                currentPage = 1;
                findFirstDevice();
            }
        });

        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                animate();
                findFiles.start(devices.get(i).did);
            }
        });

    }

    void findFirstDevice() {
        String url = "https://www.androidfilehost.com/api/?action=devices&page=1&limit=100";
        deviceRefreshLayout.setRefreshing(true);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            Log.i(TAG, "onResponseJson: " + response);
                            processFindDevices(response);
                        } catch (Exception e) {
                            Log.i(TAG, "onResponse: " + e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(TAG, "onErrorResponse: " + error.toString());
                findFirstDevice();
            }
        });

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(60000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.start();
        queue.add(stringRequest);
    }

    private void findSubsequentDevices(final int pageNumber) {
        final String url = "https://www.androidfilehost.com/api/?action=devices&page=" + pageNumber + "&limit=100";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        currentPage++;
                        try {
                            JSONObject deviceListJson = new JSONObject(response);
                            Log.i(TAG, "onResponseSubs: " + url);
                            parseDevices(deviceListJson.getJSONArray("DATA"));
                        } catch (Exception e) {
                            currentPage--;
                            Log.i(TAG, "onResponse: " + e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(TAG, "onErrorResponseSubs: " + error.toString());
                findSubsequentDevices(pageNumber);
            }
        });

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(60000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(stringRequest);
    }


    private void processFindDevices(String json) throws Exception{
        JSONObject deviceListJson = new JSONObject(json);
        String message = deviceListJson.getString("MESSAGE");
        pages = findDevicePageNumbers(message);
        parseDevices(deviceListJson.getJSONArray("DATA"));
        Log.i(TAG, "processFindDevices: " + pages[3]);
        for(int currentPage = 2; currentPage <= pages[3]; currentPage++)
            findSubsequentDevices(currentPage);

    }

    private void parseDevices(JSONArray data) throws Exception {
        if(data != null)
            for(int i = 0; i < data.length(); i++) {
                JSONObject dev = data.getJSONObject(i);
                Device device = new Device(dev.getString("did"), dev.getString("manufacturer"), dev.getString("device_name"));
                devices.add(device);
            }
        if(currentPage == pages[3]) {
            Collections.sort(devices, Comparators.byManufacturer);
            devAdapter.notifyDataSetChanged();
            deviceRefreshLayout.setRefreshing(false);
        }
        Log.i(TAG, "parseDevices: " + devices.size());
    }

    private int[] findDevicePageNumbers(String message) {
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(message);
        int pages[] = new int[4];
        int i = 0;
        while (! m.hitEnd()) {
            if (m.find() && i < 4)
                pages[i++] = Integer.parseInt(m.group());
        }
        return pages;
}

    private void animate() {
        final LinearLayout lay = (LinearLayout) rootView.findViewById(R.id.mainLinearLayout);
        final RelativeLayout rl = (RelativeLayout) rootView.findViewById(R.id.listLayout);
        rl.setVisibility(View.VISIBLE);
        rl.setAlpha(0.0f);
        lay.animate()
                .setDuration(500)
                .translationX(-lay.getWidth())
                .alpha(0.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        lay.setVisibility(View.GONE);
                        rl.animate()
                                .setDuration(500)
                                .alpha(1.0f);
                    }
                });
    }
}