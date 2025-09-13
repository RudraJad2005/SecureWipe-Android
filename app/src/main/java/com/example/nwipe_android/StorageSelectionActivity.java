package com.example.nwipe_android;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StorageSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_PATH = "selected_path";
    public static final String EXTRA_SELECTED_NAME = "selected_name";

    private static class Entry {
        final String name; final String path;
        Entry(String name, String path) { this.name = name; this.path = path; }
        @Override public String toString() { return name + "\n" + path; }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_selection);

        ListView listView = findViewById(R.id.storage_list);
        List<SystemStorageManager.StorageLocation> roots = SystemStorageManager.getSystemStorageRoots(this);
        List<Entry> entries = new ArrayList<>();
        for (SystemStorageManager.StorageLocation loc : roots) {
            if (loc != null && loc.path != null) {
                File f = new File(loc.path);
                if (f.exists() && f.canWrite()) {
                    entries.add(new Entry(loc.displayName != null ? loc.displayName : "Storage", loc.path));
                }
            }
        }

        ArrayAdapter<Entry> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Entry e = entries.get(position);
                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_PATH, e.path);
                data.putExtra(EXTRA_SELECTED_NAME, e.name);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }
}
