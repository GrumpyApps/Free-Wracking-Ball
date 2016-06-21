package de.hs_kl.imst.gatav.tilerenderer.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.InputStream;


public class Target extends TileGraphics {
    private int score=1;
    public int getScore() { return score; }

    public Target(int x, int y, InputStream is) {
        super(x, y, is);

        tilePaint.setColor(Color.parseColor("#BF1111"));
    }

    public Target(int x, int y, InputStream is, int score) {
        this(x, y, is);
        this.score = score;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPassable() {
        return true;
    }
}
