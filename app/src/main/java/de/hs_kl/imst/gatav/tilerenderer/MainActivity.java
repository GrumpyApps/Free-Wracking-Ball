package de.hs_kl.imst.gatav.tilerenderer;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AssetManager am = getResources().getAssets();
        ArrayList<String> levelList = new ArrayList<String>();  // alle Level-Namen ohne .txt

        try {
            String[] files = am.list("levels");
            for(String s : files) {
                if(!s.endsWith(".txt")) continue;
                s = s.substring(0, s.lastIndexOf("."));
                levelList.add(s);
            }
        }catch(IOException e){
            e.printStackTrace();
        }

        ArrayAdapter<String> itemsAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, levelList);

        ListView listView = (ListView) findViewById(R.id.level_list);
        listView.setAdapter(itemsAdapter);

		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
			    //Toast.makeText(getApplicationContext(), ((TextView) view).getText(), Toast.LENGTH_SHORT).show();
                String level = (String) parent.getItemAtPosition(position);
                Intent intent = new Intent(MainActivity.this, MainGameActivity.class);
                intent.putExtra("level", level);
                startActivity(intent);
			}
		});
    }


}

