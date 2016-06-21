package de.hs_kl.imst.gatav.tilerenderer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class MainGameActivity extends AppCompatActivity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String level=getIntent().getExtras().getString("level");

        gameView = new GameView(this, level);
        setContentView(gameView);
    }


}
