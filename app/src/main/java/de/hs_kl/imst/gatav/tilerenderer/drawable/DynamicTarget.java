package de.hs_kl.imst.gatav.tilerenderer.drawable;

import android.graphics.Color;

import java.io.InputStream;


public class DynamicTarget extends MovableTileGraphics {
    private int score=42;
    public int getScore() { return score; }

    public DynamicTarget(int x, int y, InputStream is) {
        super(x, y, is);

        tilePaint.setColor(Color.parseColor("#BF1111"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPassable() {
        return true;
    }
}
