package com.danielbuenrrostro.twittermap;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.squareup.picasso.Picasso;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.services.StatusesService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * An activity that displays a Google map with a marker (pin) to indicate a particular location.
 */
public class TwitterMap extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap map;
    class TwitterInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private Tweet tweet;

        TwitterInfoWindowAdapter(Tweet tweet) {
            this.tweet = tweet;
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {
            View view = getLayoutInflater().inflate(R.layout.tweet_layout, null);

            ImageView imgView = (ImageView) view.findViewById(R.id.tweet_avatar);
            Picasso.get().load(tweet.user.profileImageUrl).noFade().into(imgView);

            TextView handle = (TextView) view.findViewById(R.id.tweet_user);
            handle.setText("@" + tweet.user.screenName);

            TextView text = (TextView) view.findViewById(R.id.tweet_text);
            text.setText(tweet.text);

            return view;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        // Initialize google maps and twitter
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        Twitter.initialize(this);

        // Get Twitter Client to work with their API
        TwitterApiClient twitterApiClient = TwitterCore.getInstance().getGuestApiClient();
        StatusesService statusesService = twitterApiClient.getStatusesService();


        final EditText editText = (EditText) findViewById(R.id.editText);
        editText.setOnEditorActionListener((view, keyCode, keyEvent) -> {
            if(keyCode == EditorInfo.IME_ACTION_DONE) {
                String fieldText = editText.getText().toString();

                if(TextUtils.isEmpty(fieldText)) {
                    editText.setError("Please enter a twitter handle to search.");
                    return false;
                }

                map.clear();

                Call<List<Tweet>> call = statusesService.userTimeline(null, fieldText, null, null, null, null, true, null, false);
                call.enqueue(new Callback<List<Tweet>>() {
                    @Override
                    public void onResponse(Call<List<Tweet>> call, Response<List<Tweet>> response) {
                        List<Tweet> tweets = getTweetsFromResponse(response);
                        List<Tweet> tweetsWithPlace = filterTweetsWithPlace(tweets);
                        if(tweetsWithPlace.isEmpty()) {
                            System.out.println("No Tweets with location enabled!");
                            Toast.makeText(TwitterMap.this, "No Tweets with location enabled!", Toast.LENGTH_SHORT).show();
                        } else {
                            addMarkerForTweet(tweetsWithPlace.get(0)); // Add marker for the first geolocation enabled tweet
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Tweet>> call, Throwable t) {
                        throw new Error(t);
                    }
                });
                editText.setText(null);
            }
            return false;
        });
    }

    /**
     * Manipulates the map when it's available.
     * The API invokes this callback when the map is ready to be used.
     * If Google Play services is not installed on the device, the user receives a prompt to install
     * Play services inside the SupportMapFragment. The API invokes this method after the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        LatLng US_Center = new LatLng(38.1033917, -94.5274508);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(US_Center, 3.0f));
    }

    private List<Tweet> getTweetsFromResponse(Response<List<Tweet>> r) {
        if(!r.isSuccessful()) {
            System.err.println(r.toString());
            return new ArrayList<Tweet>();
        } else if(r.body() == null) {
            System.err.println("No tweets from this account!");
            return new ArrayList<Tweet>();
        }

        return r.body();
    }

    private List<Tweet> filterTweetsWithPlace(List<Tweet> tweets) {
        Iterator<Tweet> iterator = tweets.iterator();
        List<Tweet> locationEnabled = new ArrayList<Tweet>();

        while(iterator.hasNext()) {
            Tweet currentTweet = iterator.next();
            if(currentTweet.place != null) {
                locationEnabled.add(currentTweet);
            }
        }

        return locationEnabled;
    }

    private void addMarkerForTweet(Tweet tweet) {
        List<List<Double>> coordinates = tweet.place.boundingBox.coordinates.get(0);
        LatLng CenterCoordinates = getCenterOfCoordinateSet(coordinates);

        Marker currentMarker = map.addMarker(new MarkerOptions().position(CenterCoordinates));
        GoogleMap.InfoWindowAdapter adapter = new TwitterInfoWindowAdapter(tweet);

        View view = getLayoutInflater().inflate(R.layout.tweet_layout, null);
        ImageView imgView = (ImageView) view.findViewById(R.id.tweet_avatar);

        Picasso.get().load(tweet.user.profileImageUrl).noFade().tag("avatar").into(imgView, new com.squareup.picasso.Callback() {
            @Override
            public void onSuccess() {
                map.setInfoWindowAdapter(adapter);
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(CenterCoordinates, 15.0f));
                currentMarker.showInfoWindow();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(TwitterMap.this, "Error loading image!", Toast.LENGTH_SHORT).show();
                System.err.println(e);
            }
        });
    }

    private LatLng getCenterOfCoordinateSet(List<List<Double>> coordinates) {
        Double latTotal = 0.0;
        Double lngTotal = 0.0;
        int coordinateAmount = 0;

        for(int i = 0; i < coordinates.size(); i++) {
            List<Double> set = coordinates.get(i);
            latTotal += set.get(1);
            lngTotal += set.get(0);
            coordinateAmount++;
        }

        return new LatLng(latTotal / coordinateAmount, lngTotal / coordinateAmount);
    }
}
