package com.example.android.roomwordssample;

/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;


public class MainActivity extends AppCompatActivity {
    //Custom activities IDs
    public static final int NEW_WORD_ACTIVITY_REQUEST_CODE = 1;
    static final int REQUEST_VIDEO_CAPTURE = 2;

    private WordViewModel mWordViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Setup the recyclerview for RoomWords
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        final WordListAdapter adapter = new WordListAdapter(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Get a new or existing ViewModel from the ViewModelProvider.
        mWordViewModel = new ViewModelProvider(this).get(WordViewModel.class);

        // Add an observer on the LiveData returned by getAlphabetizedWords.
        // The onChanged() method fires when the observed data changes and the activity is
        // in the foreground.
        mWordViewModel.getAllWords().observe(this, new Observer<List<Word>>() {
            @Override
            public void onChanged(@Nullable final List<Word> words) {
                // Update the cached copy of the words in the adapter.
                adapter.setWords(words);
            }
        });

        //Set up list-add button
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, NewWordActivity.class);
                startActivityForResult(intent, NEW_WORD_ACTIVITY_REQUEST_CODE);
            }
        });

        //Set up camera button
        FloatingActionButton fabv = findViewById(R.id.fabVideo);
        fabv.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
                    Uri videoUri;
                    takeVideoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        videoUri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);
                        //Video URI can't be controlled on A70
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri); //Crashes A70 camera app
                        takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                        startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
                    }catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //Download file from URL
        /*try {
            String url = downloadUrl(new URL("http", "172.16.0.20", 8080, ""));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    private Uri getOutputMediaFileUri(int type){
        return FileProvider.getUriForFile(getApplicationContext(), getPackageName()+".provider", getOutputMediaFile(type));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Word insert result
        if (requestCode == NEW_WORD_ACTIVITY_REQUEST_CODE ){
            if(resultCode == RESULT_OK) {
                Word word = new Word(data.getStringExtra(NewWordActivity.EXTRA_REPLY));
                mWordViewModel.insert(word);
            } else {
                Toast.makeText(
                        getApplicationContext(),
                        R.string.empty_not_saved,
                        Toast.LENGTH_LONG).show();
            }
        }

        //Camera result
        if(requestCode == REQUEST_VIDEO_CAPTURE) {

            if (resultCode == RESULT_OK) {
                Word word = new Word(data.getData().getPath());
                mWordViewModel.insert(word);
                Toast.makeText(this, "Video saved to: " +data.getData(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(
                        getApplicationContext(),
                        "Video handling error",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static File getOutputMediaFile(int type){//TODO: actually make it work
        // Check that the SDCard is mounted
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory() +"/test_app");
        // Create the storage directory(MyCameraVideo) if it does not exist
        if (! mediaStorageDir.exists()){
            try {
                mediaStorageDir.mkdirs();
            }catch(SecurityException e){
                Log.d("", e.toString());
                return null;
            }
        }

        // Create a media file name
        java.util.Date date= new java.util.Date();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(date.getTime());

        File mediaFile;
        if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

        /**
     * Given a URL, sets up a connection and gets the HTTP response body from the server.
     * If the network request is successful, it returns the response body in String form. Otherwise,
     * it will throw an IOException.
     */
    private String downloadUrl(URL url) throws IOException {
        InputStream stream = null;
        HttpsURLConnection connection = null;
        String result = null;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            // Timeout for reading InputStream arbitrarily set to 3000ms.
            connection.setReadTimeout(3000);
            // Timeout for connection.connect() arbitrarily set to 3000ms.
            connection.setConnectTimeout(3000);
            // For this use case, set HTTP method to GET.
            connection.setRequestMethod("GET");
            // Already true by default but setting just in case; needs to be true since this request
            // is carrying an input (response) body.
            connection.setDoInput(true);
            // Open communications link (network traffic occurs here).
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            // Retrieve the response body as an InputStream.
            stream = connection.getInputStream();
            if (stream != null) {
                // Converts Stream to String with max length of 500.
                // result = readStream(stream, 500);
            }
        } finally {
            // Close Stream and disconnect HTTPS connection.
            if (stream != null) {
                stream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

}
