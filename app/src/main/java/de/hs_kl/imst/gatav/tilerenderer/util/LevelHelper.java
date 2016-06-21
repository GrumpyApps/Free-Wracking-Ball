package de.hs_kl.imst.gatav.tilerenderer.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Hilfsfunktionen, die den Umgang mit den Leveldateien erleichtern
 */
public class LevelHelper {
    /**
     * Durchsucht alle im assets/levels/ Ordner hinterlegten Leveldateien und ermittelt die maximale
     * horizontale und vertikale Größe in Blöcken (nicht Pixeln)
     * @param ctxt context
     * @return {@link Pair}[maxBreite, maxHöhe]
     */
    public static Pair<Integer, Integer> getLargestLevelDimensions(Context ctxt) {
        int maxWidth = -1;
        int maxHeight = -1;
        AssetManager am = ctxt.getAssets();
        try {
            String[] files = am.list("levels");
            for(String f : files) {
                if(!f.endsWith(".txt")) continue; // nur Dateien mit der Endung .txt berücksichtigen
                Pair<Integer, Integer> currentLevelSize = getLevelDimensions(ctxt, am.open(String.format("levels/%s", f)));
                if(currentLevelSize.first > maxWidth)
                    maxWidth = currentLevelSize.first;
                if(currentLevelSize.second > maxHeight)
                    maxHeight = currentLevelSize.second;
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return new Pair<>(maxWidth, maxHeight);
    }

    /**
     * Ermittelt die Größe eines Levels
     * @param ctxt Kontext
     * @param levelName Name des zu verarbeitenden Levels (ohne .txt)
     * @return {@link Pair}[Breite, Höhe]
     * @throws IOException
     */
    public static Pair<Integer, Integer> getLevelDimensions(Context ctxt, String levelName) throws IOException {
        return getLevelDimensions(ctxt, ctxt.getAssets().open(String.format("levels/%s.txt", levelName)));
    }

    /**
     * Ermittelt die Größe eines Levels
     * @param ctxt Kontext
     * @param levelIs InputStream des zu verarbeitenden Levels
     * @return {@link Pair}[Breite, Höhe]
     * @throws IOException
     */
    public static Pair<Integer, Integer> getLevelDimensions(Context ctxt, InputStream levelIs) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(levelIs));
        String currentLine = null;
        int maxLineLength = -1;
        int lineCount = 0;
        while((currentLine = br.readLine()) != null) {
            maxLineLength = Math.max(maxLineLength, currentLine.length());
            lineCount++;
        }
        br.close();
        return new Pair<Integer, Integer>(maxLineLength, lineCount);
    }
}
