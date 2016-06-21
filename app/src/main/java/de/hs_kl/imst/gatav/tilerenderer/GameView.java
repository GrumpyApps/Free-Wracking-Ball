package de.hs_kl.imst.gatav.tilerenderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.sax.StartElementListener;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import de.hs_kl.imst.gatav.tilerenderer.drawable.GameContent;
import de.hs_kl.imst.gatav.tilerenderer.drawable.TileGraphics;
import de.hs_kl.imst.gatav.tilerenderer.util.Direction;
import de.hs_kl.imst.gatav.tilerenderer.util.LevelHelper;


/**
 * {@link SurfaceView} welches sich um die Darstellung des Spiels und Interaktion mit diesem kümmert.
 * Erzeugt eine Gameloop ({@link GameView#gameThread}), welcher die Aktualisierung von Spielzustand
 * und -darstellung regelt.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable, GestureDetector.OnGestureListener {

    private SurfaceHolder surfaceHolder;

    private Thread gameThread;
    private boolean runningRenderLoop = false;
    public boolean gameOver=false;

    private String levelName;

    private Thread timeThread;
    private volatile boolean runningTimeThread=false;    // access to elementary data types (not double or long) are atomic and should be volatile to synchronize content
    private volatile double elapsedTime = 0.0;
    synchronized private void resetElapsedTime() { elapsedTime = 0.0;}
    synchronized private double getElapsedTime() { return elapsedTime; }
    synchronized private void increaseElapsedTime(double increment) { elapsedTime += increment; }

    private double maxCollectedTargets = 30;

    private int gameMode=0; // 0 game not startet, 1 game started by first fling gesture, 2 game over

    private float gameWidth = -1;
    private float gameHeight = -1;

    private GestureDetectorCompat gestureDetector;

    private GameContent gameContent;

    private Paint scoreAndTimePaint = new Paint();
    {   scoreAndTimePaint.setColor(Color.WHITE);
        scoreAndTimePaint.setTextSize(20);
    }

    /**
     * Konstruktor, initialisiert surfaceHolder und setzt damit den Lifecycle des SurfaceViews in Gang
     * @param context Kontext
     */
    public GameView(Context context, String level) {
        super(context);
        levelName=level;

        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);

        gestureDetector = new GestureDetectorCompat(context, this);

        scoreAndTimePaint.setTextSize(20f * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Aktualisiert die grafische Darstellung; wird von Gameloop aufgerufen
     * @param canvas Zeichenfläche
     */
    void updateGraphics(Canvas canvas) {
        // Layer 0 (clear background)
        canvas.drawColor(Color.parseColor("#555555"));

        // Layer 1 (Game content)
        if(gameContent == null) return;
        canvas.save();
        canvas.translate((canvas.getWidth() - gameContent.getGameWidth()) / 2,
                (canvas.getHeight() - gameContent.getGameHeight()) / 2);
        gameContent.draw(canvas);
        canvas.restore();

        // Layer 2 (Collected Targets, Score and Elapsed Time)
        String collectedText = String.format("%d gesammelt", gameContent.getCollectedTargets());
        String scoreText = String.format("Punkte: %d", gameContent.getCollectedScore());
        String timeText = "Zeit: " + String.format("%.2f", getElapsedTime()) + " Sekunden";
        String timeTextFake = "Zeit: " + String.format("%.2f", 200.0) + " Sekunden";
        Rect collectedTextBounds = new Rect();
        scoreAndTimePaint.getTextBounds(collectedText, 0, collectedText.length(), collectedTextBounds);
        Rect scoreTextBounds = new Rect();
        scoreAndTimePaint.getTextBounds(scoreText, 0, scoreText.length(), scoreTextBounds);
        Rect timeTextBounds = new Rect();
        scoreAndTimePaint.getTextBounds(timeText, 0, timeText.length(), timeTextBounds);
        float textWidth = Math.max(scoreAndTimePaint.measureText(timeText), scoreAndTimePaint.measureText(timeTextFake))+10;
        textWidth = Math.max(scoreAndTimePaint.measureText(collectedText), textWidth);
        textWidth = Math.max(scoreAndTimePaint.measureText(scoreText), textWidth);
        canvas.save();
        canvas.translate(gameWidth - textWidth, scoreTextBounds.height());
        canvas.drawText(collectedText, 0, 0, scoreAndTimePaint);
        canvas.translate(0, (int) (timeTextBounds.height() * 1.5));
        canvas.drawText(scoreText, 0, 0, scoreAndTimePaint);
        if(gameMode==1) {   // game running
            canvas.translate(0, (int)(timeTextBounds.height()*1.5));
            canvas.drawText(timeText, 0, 0, scoreAndTimePaint);
        }
        if(gameMode==2) {   // game running
            canvas.translate(0, (int)(timeTextBounds.height()*1.5));
            canvas.drawText("Game Over!", 0, 0, scoreAndTimePaint);
        }
        canvas.restore();
    }

    /**
     * Aktualisiert den Spielzustand; wird von Gameloop aufgerufen
     * @param fracsec Teil einer Sekunde, der seit dem letzten Update vergangen ist
     */
    void updateContent(float fracsec) {
        if(gameContent != null)
            gameContent.update(fracsec);
    }

    /**
     * Wird aufgerufen, wenn die Zeichenfläche erzeugt wird
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Gameloop anwerfen
        gameThread = new Thread(this);
        gameThread.start();
    }

    /**
     * Wird aufgerufen, wenn sich die Größe der Zeichenfläche ändert
     * Das initiale Festlegen der Größe bewirkt ebenfalls den Aufruf dieser Funktion
     * @param holder Surface Holder
     * @param format Pixelformat
     * @param width Breite in Pixeln
     * @param height Höhe in Pixeln
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int border = 0;                                                     // darf's ein wenig Rand sein?
        gameWidth = width - border;                                         // hinzufügen
        gameHeight = (int)((float)gameWidth / ((float) width / height));    // Höhe entsprechend anpassen

        // Ermitteln der Größe der einzelnen Elemente
        Pair<Integer, Integer> maxLevelSize = LevelHelper.getLargestLevelDimensions(getContext());
        // minimale Breite hält alle quadratischen Kacheln sichbar im Spielfeld
        TileGraphics.setTileSize(Math.min(gameWidth / maxLevelSize.first,
                gameHeight / maxLevelSize.second));

        gameContent = new GameContent(getContext(), levelName);

        // Reset der Zustände bei "onResume"
        gameOver=false;
        gameMode=0;
    }

    /**
     * Wird am Ende des Lifecycles der Zeichenfläche aufgerufen
     * Ein guter Ort um ggf. Ressourcen freizugeben, Verbindungen
     * zu schließen und die Gameloop und den Time Thread zu beenden
     * @param holder SurfaceHolder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Gameloop and Time Thread beenden
        runningRenderLoop = false;
        runningTimeThread = false;
        gameMode=0;
        gameOver=false;
        gameContent.resetPlayerDirection();

        try {
            gameThread.join();
            if(timeThread != null)  // überhaupt gestartet?
                timeThread.join();
        }catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gameloop, ruft {@link #updateContent(float)} und {@link #updateGraphics(Canvas)} auf
     * und ermittelt die seit dem letzten Schleifendurchlauf vergangene Zeit (wird zum zeitlich
     * korrekten Aktualisieren des Spielzustandes benötigt)
     */
    @Override
    public void run() {
        runningRenderLoop = true;

        long lastTime = System.currentTimeMillis();

        while(runningRenderLoop) {
            long currentTime = System.currentTimeMillis();
            long delta = currentTime - lastTime;
            float fracsec = (float)delta / 1000f;
            lastTime = currentTime;

            Canvas canvas = surfaceHolder.lockCanvas();
            if(canvas == null) continue;

            if(!gameOver)
                updateContent(fracsec); // kompletten Spielzustand aktualisieren

            if(gameContent!=null && gameContent.getCollectedTargets() >= maxCollectedTargets) {
                gameMode = 2;
                gameOver = true; // Game over
            }

            updateGraphics(canvas); // Neu zeichnen

            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    public  void startTimeThread() {
        if(runningTimeThread) return;
        runningTimeThread = true;
        resetElapsedTime();
        timeThread = new Thread(new Runnable() {
            public void run() {
                while (runningTimeThread) {
                    increaseElapsedTime(0.01);

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        runningTimeThread=false;
                    }
                }
            }});
        timeThread.start();
    }


    /**
     * Um den GestureDetector verwenden zu können, müssen die Touch-Events an diesen weitergeleitet werden
     * Hier wäre evtl. eine geeignete Stelle, um Eingaben vorrübergehend
     * (bspw. während Animationen) zu deaktivieren, indem eben dieses Weiterleiten deaktiviert wird
     * @param event Aktuelles {@link MotionEvent}
     * @return true wenn das Event verarbeitet wurde, andernfalls false
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(gestureDetector.onTouchEvent(event))
            return true;
        else
            return super.onTouchEvent(event);
    }

    /**
     * Die Fling-Geste wird genutzt, um die Spielfigur durch den Level zu bewegen.
     * Der eigentliche Move wird dem Gameloop synchron signalisiert und von diesem ausgeführt.
     * @param e1 {@link MotionEvent} welches die Geste gestartet hat (Ursprung)
     * @param e2 {@link MotionEvent} am Ende der Geste (aktuelle Position)
     * @param velocityX Geschwindigkeit der Geste auf der X-Achse (Pixel / Sekunde)
     * @param velocityY Geschwindigkeit der Geste auf der Y-Achse (Pixel / Sekunde)
     * @return true wenn das Event verarbeitet wurde, andernfalls false
     */
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

        // Wird der Player aktuell noch animiert, wird der Fling wegkonsumiert
        // (bei uns nur konzeptionell notwendig, da die laufende Animation in GameContent
        // neue Animationen eh abblockt)
        if(!gameContent.isPlayerDirectionIDLE())
            return true;

        float deg = (float) Math.toDegrees(
                Math.acos(velocityX/Math.sqrt(velocityX * velocityX + velocityY * velocityY))
        );
        if(velocityY > 0)
            deg = 180f + (180f - deg);

        if(deg > 315 || deg < 45)
            gameContent.setPlayerDirection(Direction.RIGHT);
        else if(deg >= 45 && deg <= 135)
            gameContent.setPlayerDirection(Direction.UP);
        else if(deg > 135 && deg < 225)
            gameContent.setPlayerDirection(Direction.LEFT);
        else if(deg >= 225 && deg < 315)
            gameContent.setPlayerDirection(Direction.DOWN);

        // erster Fling startet den Zeitzähler
        gameMode=1;
        startTimeThread();

        return true;
    }

    /**
     * Wird hier nicht true zurück gegeben, erfolgen keine onFling events
     * @param e {@link MotionEvent} aktuelles Event
     * @return true.
     */
    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    // Nicht genutzte Gesten
    @Override public void onShowPress(MotionEvent e) {}
    @Override public boolean onSingleTapUp(MotionEvent e) { return false; }
    @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { return false; }
    @Override public void onLongPress(MotionEvent e) {}

}
