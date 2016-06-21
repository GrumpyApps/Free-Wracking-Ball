package de.hs_kl.imst.gatav.tilerenderer.drawable;

import android.content.Context;
        import android.content.res.AssetManager;
        import android.graphics.Canvas;
        import android.graphics.Paint;
        import android.support.annotation.Nullable;
        import android.util.Log;

        import java.io.BufferedReader;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;
        import java.util.ArrayList;
        import java.util.Collections;
        import java.util.Random;

        import de.hs_kl.imst.gatav.tilerenderer.util.Direction;

public class GameContent implements Drawable {
    /**
     * Breite und Höhe des Spielfeldes in Pixel
     */
    private int gameWidth = -1;
    private int gameHeight = -1;
    public int getGameWidth() { return gameWidth; }
    public int getGameHeight() { return gameHeight; }

    /**
     * Beinhaltet alle Tiles, die das Spielfeld als solches darstellen. Diese werden als erstes
     * gezeichnet und bilden somit die unterste Ebene.
     */
    private TileGraphics[][] tiles;         // [zeilen][spalten]

    /**
     * Beinhaltet Referenzen auf alle dynamischen Kacheln, deren {@link Drawable#update(float)} Methode
     * aufgerufen werden muss. Damit lassen sich Kachel-Animationen durchführen.
     */
    private ArrayList<TileGraphics> dynamicTiles = new ArrayList<>();

    /**
     * Beinhaltet alle Ziele. Diese werden als zweites und somit über die in {@link GameContent#tiles}
     * definierten Elemente gezeichnet.
     */
    private TileGraphics[][] targetTiles;   // [zeilen][spalten]

    /**
     * Beinhaltet Referenzen auf alle Ziele
     */
    private ArrayList<TileGraphics> targets = new ArrayList<>();

    /**
     * Beinhaltet Referenzen auf Kacheln (hier alle vom Typ {@link Floor}), auf welchen ein Ziel
     * erscheinen kann.
     */
    private ArrayList<TileGraphics> possibleTargets = new ArrayList<>();

    /**
     * Anzahl der eingesammelten Ziele
     */
    private int collectedTargets = 0;
    public int getCollectedTargets() { return collectedTargets; }

    /**
     * Anzahl der gesammelten Punkte
     */
    private int collectedScore = 0;
    public int getCollectedScore() { return collectedScore; }

    /**
     * Beinhaltet Referenz auf Spieler, der bewegt wird.
     */
    private Player player = null;

    /**
     * Dynamisches Ziel
     */
    private DynamicTarget dynTarget = null;

    public DynamicTarget getDynTarget() { return dynTarget;}

    /**
     * Wird in {@link GameContent#movePlayer(Direction)} verwendet, um dem Game Thread
     * die Bewegungsrichtung des Players zu übergeben.
     * Wird vom Game Thread erst auf IDLE zurückgesetzt, sobald die Animation abgeschlossen ist
     */
    private volatile Direction playerDirection = Direction.IDLE;
    synchronized public void resetPlayerDirection() { playerDirection = Direction.IDLE;}
    synchronized public boolean isPlayerDirectionIDLE() { return playerDirection == Direction.IDLE; }
    synchronized public void setPlayerDirection(Direction newDirection) { playerDirection = newDirection;}
    synchronized public Direction getPlayerDirection() { return playerDirection; }

    /**
     * Zufallszahlengenerator zum Hinzufügen neuer Ziele
     */
    private Random random = new Random();


    private Context context;

    /**
     * {@link AssetManager} über den wir unsere Leveldaten beziehen
     */
    private AssetManager assetManager;

    /**
     * Name des Levels
     */
    private String levelName;


    /**
     * @param context TODO <insert wise words here :-)/>
     * @param levelName Name des zu ladenden Levels
     */
    public GameContent(Context context, String levelName) {
        this.context = context;
        this.assetManager = context.getAssets();
        this.levelName = levelName;

        // Level laden mit Wall (W), Floor (F) und Player (P)
        // Target wird im geladenen Level zum Schluss zusätzlich gesetzt
        try {
            loadLevel(assetManager.open(String.format("levels/%s.txt", levelName)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Player ist animiert und muss deshalb updates auf seine Position erfahren
        dynamicTiles.add(player);
    }


    /**
     * Überprüfung der Möglichkeit einer Verschiebung des Players in eine vorgegebene Richtung
     * Geprüft wird auf Spielfeldrand und Hindernisse.
     * Falls das zulässige Zielfeld ein Target ist, wird dieses konsumiert und ein neues Target gesetzt.
     * Dann wird die Bewegung des Players durchgeführt bzw. angestoßen (Animation)
     *
     * @param direction Richtung in die der Player bewegt werden soll
     * @return true falls Zug erfolgreich durchgeführt bzw. angestoßen, false falls Zug nicht durchgeführt
     */
    public boolean movePlayer(Direction direction) {

        // Erster Schritt: Basierend auf Zugrichtung die Zielposition bestimmen
        int newX = -1;
        int newY = -1;
        switch(direction) {
            case UP: newX = player.getX(); newY = player.getY() - 1; break;
            case DOWN: newX = player.getX(); newY = player.getY() + 1; break;
            case RIGHT: newX = player.getX() + 1; newY = player.getY(); break;
            case LEFT: newX = player.getX() - 1; newY = player.getY(); break;
        }
        if ((!(newX >= 0 && newX < gameWidth && newY >= 0 && newY < gameHeight)))
            throw new AssertionError("Spieler wurde außerhalb des Spielfeldes bewegt. Loch im Level?");

        // Zweiter Schritt: Prüfen ob Spieler sich an Zielposition bewegen kann (Zielkachel.isPassable())
        TileGraphics targetTile = tiles[newY][newX];
        if(targetTile == null || !targetTile.isPassable())
            return false;

        // Dritter Schritt: Spieler verschieben bzw. Verschieben starten.
        // Hinterher steht der Spieler logisch bereits auf der neuen Position
        player.move(newX, newY);

        // Vierter Schritt: Prüfen ob auf der Zielkachel ein Target existiert
        if(targetTiles[newY][newX] != null && targetTiles[newY][newX] instanceof Target) {
            collectedTargets++;
            collectedScore += ((Target)targetTiles[newY][newX]).getScore();
            // Altes Ziel entfernen
            targets.remove(targetTiles[newY][newX]);
            targetTiles[newY][newX] = null;
            // Neues Ziel erzeugen
            createNewTarget();
        }
        // Prüfen ob auf der Zielposition das dynamische Target existert => Sonderpunkte :-)
        if(dynTarget!=null) {
            if(samePosition(player, dynTarget)) {
                collectedScore += dynTarget.getScore();
                dynamicTiles.remove(dynTarget);
                dynTarget = null;
            }
        }

        return true;
    }


    /**
     * Spielinhalt zeichnen
     * @param canvas Zeichenfläche, auf die zu Zeichnen ist
     */
    @Override
    public void draw(Canvas canvas) {
        // Erste Ebene zeichnen (Wände und Boden)
        for (int yIndex = 0; yIndex < tiles.length; yIndex++)
            for (int xIndex = 0; xIndex < tiles[yIndex].length; xIndex++) {
                if(tiles[yIndex][xIndex] == null) continue;
                tiles[yIndex][xIndex].draw(canvas);
            }
        // Zweite Ebene zeichnen
        for (int yIndex = 0; yIndex < targetTiles.length; yIndex++)
            for (int xIndex = 0; xIndex < targetTiles[yIndex].length; xIndex++) {
                if(targetTiles[yIndex][xIndex] == null) continue;
                targetTiles[yIndex][xIndex].draw(canvas);
            }
        // Dynamisches Ziel zeichnen
        if(dynTarget!=null)
            dynTarget.draw(canvas);
        // Spieler zeichnen
        player.draw(canvas);
    }


    /**
     * Spielinhalt aktualisieren (hier Player und Animation dynamischer Kacheln)
     * @param fracsec Teil einer Sekunde, der seit dem letzten Update des gesamten Spielzustandes vergangen ist
     */
    @Override
    public void update(float fracsec) {
        // 1. Schritt: Auf mögliche Player Bewegung prüfen und ggf. durchführen/anstoßen
        // vorhandenen Player Move einmalig ausführen bzw. anstoßen, falls
        // PlayerDirection nicht IDLE ist und Player aktuell nicht in einer Animation
        //Log.d("updateGameContent", ""+isPlayerDirectionIDLE()+" "+player.isMoving());
        if(!isPlayerDirectionIDLE() && !player.isMoving())
            movePlayer(getPlayerDirection());
        // Dynamisches Ziel vielleicht erzeugen
        if(dynTarget==null) {
            if(random.nextDouble()<0.004)
                createAndMoveDynamicTarget();
        }

        // 2. Schritt: Updates bei allen dynamischen Kacheln durchführen (auch Player)
        for(TileGraphics dynamicTile : dynamicTiles)
            dynamicTile.update(fracsec);

        // 3. Schritt: Animationen auf Ende überprüfen und ggf. wieder freischalten
        // Player Move fertig ausgeführt => Sperre für neues Player Event freischalten
        if(!player.isMoving())
            resetPlayerDirection();
        // Animation des dynamischen Ziels abgeschlossen
        if(dynTarget!= null && !dynTarget.isMoving()) {
            dynamicTiles.remove(dynTarget);
            dynTarget = null;
        }
    }


    /**
     * Level aus Stream laden und Datenstrukturen entsprechend initialisieren
     * @param levelIs InputStream von welchem Leveldaten gelesen werden sollen
     * @throws IOException falls beim Laden etwas schief geht (IO Fehler, Fehler in Leveldatei)
     */
    private void loadLevel(InputStream levelIs) throws IOException {
        // Erster Schritt: Leveldatei zeilenweise lesen und Inhalt zwischenspeichern. Zudem ermitteln, wie breit der Level maximal ist.
        // Spielfeldgröße ermitteln
        ArrayList<String> levelLines = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(levelIs));
        int maxLineLength = 0;
        String currentLine = null;
        while((currentLine = br.readLine()) != null) {
            maxLineLength = Math.max(maxLineLength, currentLine.length());
            levelLines.add(currentLine);
        }
        br.close();
        gameWidth = (int)(maxLineLength * TileGraphics.getTileSize());
        gameHeight = (int)(levelLines.size() * TileGraphics.getTileSize());


        // Zweiter Schritt: basierend auf dem Inhalt der Leveldatei die Datenstrukturen befüllen
        tiles = new TileGraphics[levelLines.size()][];
        targetTiles = new TileGraphics[levelLines.size()][];

        for(int yIndex = 0; yIndex < levelLines.size(); yIndex++) {
            tiles[yIndex] = new TileGraphics[maxLineLength];
            targetTiles[yIndex] = new TileGraphics[maxLineLength];
            String line = levelLines.get(yIndex);
            for(int xIndex = 0; xIndex < maxLineLength && xIndex < line.length(); xIndex++) {
                TileGraphics tg = getTileByCharacter(line.charAt(xIndex), xIndex, yIndex);
                // Floor Tiles sind gleichzeitig Kacheln, auf denen Ziele erscheinen können
                if(tg instanceof Floor) {
                    possibleTargets.add(tg);
                    tiles[yIndex][xIndex] = tg;
                } else if(tg instanceof Player) {   // auch auf der Player Kachel können Ziele erscheinen, zusätzlich ist sie eine Floor Kachel
                    tiles[yIndex][xIndex] = getTileByCharacter('f', xIndex, yIndex);
                    possibleTargets.add(tiles[yIndex][xIndex]);
                    if (player != null)
                        throw new IOException("Invalid level file, contains more than one player!");
                    player = (Player) tg;
                } else {                            // Wall Kacheln
                    tiles[yIndex][xIndex] = tg;
                }
            }
        }

        // Dritter Schritt: erste Ziele erzeugen und platzieren
        createNewTarget(); createNewTarget(); createNewTarget();
    }


    /**
     * Erzeugt ein dynamisches Ziel TODO
     *
     *
     * Ansonsten befindet sich das dynamische Ziel logisch "über" der Ebene der anderen Ziele.
     * Nach erfolgreichem Anlegen wird der Move direkt initiiert.
     * @return dynamisches Ziel, kann null sein, falls es von einer gewählten Source nicht erzeugt werden konnte
     */
    @Nullable
    public void createAndMoveDynamicTarget() {
        // Source zufällig aber gültig auswählen
        TileGraphics sourceTile = possibleTargets.get(random.nextInt(possibleTargets.size()));
        // Sicherstellen, dass das Ziel nicht an der gleichen Position wie der Spieler erzeugt wird
        // und sich dort nicht bereits ein normales Ziel befindet
        while(samePosition(sourceTile, player) || targetTiles[sourceTile.getY()][sourceTile.getX()]!=null)
            sourceTile = possibleTargets.get(random.nextInt(possibleTargets.size()));

        // Destination bestimmen, falls möglich, ansonsten Abbruch
        // 0 left, 1 right, 2 up, 3 down
        ArrayList<Integer> dl = new ArrayList<Integer>();
        dl.add(0); dl.add(1); dl.add(2); dl.add(3);
        Collections.shuffle(dl);

        TileGraphics destinationTile=null;
        Direction destinationDirection=Direction.IDLE;
        int destDir=-1;
        int newX=-1, newY=-1;
        // alle vier Richtungen zufällig durchgehen, bis die erste passt oder eben keine
        for(int i=0; i<4; i++) {
            switch(dl.get(i)) {
                case 0: newX=sourceTile.getX()-1; newY=sourceTile.getY();
                    destinationDirection=Direction.LEFT; destDir=0; break;
                case 1: newX=sourceTile.getX()+1; newY=sourceTile.getY();
                    destinationDirection=Direction.RIGHT; destDir=1; break;
                case 2: newX=sourceTile.getX(); newY=sourceTile.getY()-1;
                    destinationDirection=Direction.UP; destDir=2; break;
                case 3: newX=sourceTile.getX(); newY=sourceTile.getY()+1;
                    destinationDirection=Direction.DOWN; destDir=3; break;
            }
            if ((!(newX >= 0 && newX < gameWidth && newY >= 0 && newY < gameHeight)))
                continue;
            destinationTile = tiles[newY][newX];
            if(destinationTile == null || !destinationTile.isPassable()) {
                destinationTile = null;
                continue;
            }
            break;
        }
        if(destinationTile==null)
            return;

        // Dynamischen Ziel erzeugen und Move einstellen
        dynTarget = new DynamicTarget(sourceTile.getX(), sourceTile.getY(), getGraphicsStream(levelName, "sse"+destDir));  // TODO
        dynTarget.move(newX, newY);
        dynTarget.setSpeed(0.4f);   // TODO
        dynamicTiles.add(dynTarget);
    }


    /**
     * Erzeugt ein neues Ziel und sorgt dafür, dass dieses sich nicht auf der Position des Spielers
     * oder eines vorhandenen Ziels befindet
     * @return neues Ziel
     */
    private void createNewTarget() {
        TileGraphics targetTile = possibleTargets.get(random.nextInt(possibleTargets.size()));
        // Sicherstellen, dass das Ziel nicht an der gleichen Position wie der Spieler erzeugt wird
        // und sich dort nicht bereits ein Ziel befindet
        while(samePosition(targetTile, player) || targetTiles[targetTile.getY()][targetTile.getX()]!=null)
            targetTile = possibleTargets.get(random.nextInt(possibleTargets.size()));

        // Ziel zufällig auswählen
        Target newTarget = chooseTarget(targetTile.getX(), targetTile.getY(), 0);

        targetTiles[newTarget.getY()][newTarget.getX()] = newTarget;
        targets.add(newTarget);
    }


    /**
     * Sucht das neue Ziel aus
     * @param x x-Koordinate
     * @param y y-Koordinate
     * @param targetNumber 0 für zufällige Auswahl, 1-... für explizite Auswahl des Ziels
     * @return Das Ziel
     */
    private Target chooseTarget(int x, int y, int targetNumber) {
        int targetScores [] = {1, 2, 4, 8};    // TODO
        double targetProps [] = {0.6, 0.8,0.95}; // TODO
        int targetIndex;

        // zufällige Auswahl des Targets nach Wahrscheinlichkeiten in targetProps
        if(targetNumber==0) {
            double dice = random.nextDouble();
            targetIndex = targetProps.length;
            while (targetIndex > 0 && dice < targetProps[targetIndex-1])
                targetIndex--;
            targetNumber = targetIndex+1;
        } else  // explizite Wahl der Nummer des Targets
        {
            if(targetNumber<1 || targetNumber>targetScores.length)    // explizit ausgewähltes Target
                targetNumber = 1;
            targetIndex=targetNumber-1;
        }

        return new Target(x, y, getGraphicsStream(levelName, "can"+targetNumber), targetScores[targetIndex]);   // TODO
    }


    /**
     * Prüft ob zwei Kacheln auf den gleichen Koordinaten liegen
     * @param a erste Kachel
     * @param b zweite Kachel
     * @return true wenn Position gleich, andernfalls false
     */
    private boolean samePosition(TileGraphics a, TileGraphics b) {
        if(a.getX() == b.getX() && a.getY() == b.getY())
            return true;
        return false;
    }

    /**
     * Besorgt Inputstream einer Grafikdatei eines bestimmten Levels aus den Assets
     * @param levelName     Levelname
     * @param graphicsName  Grafikname
     * @return Inputstream
     */
    private InputStream getGraphicsStream(String levelName, String graphicsName) {
        try {
            return assetManager.open("levels/" + levelName + "/" + graphicsName + ".png");
        }catch(IOException e){
            try {
                return assetManager.open("levels/default/" + graphicsName + ".png");
            }catch(IOException e2){
                return null;
            }
        }
    }


    @Nullable
    private TileGraphics getTileByCharacter(char c, int xIndex, int yIndex) {
        switch(c) {
            case 'w':
            case 'W': return new Wall(xIndex, yIndex, getGraphicsStream(levelName, "wall"));    // TODO
            case 'f':
            case 'F': return new Floor(xIndex, yIndex, null);
            case 'p':
            case 'P': return new Player(xIndex, yIndex, getGraphicsStream(levelName, "bender"));
        }
        return null;
    }
}
