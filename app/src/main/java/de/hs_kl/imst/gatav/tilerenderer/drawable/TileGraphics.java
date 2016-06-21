package de.hs_kl.imst.gatav.tilerenderer.drawable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.io.InputStream;

/**
 * Basisklasse für Elemente unseres Spielfeldes. Da diese Blöcke in einem Gitter angeordnet werden,
 * ist es sinnvoll, sie mit ihren Koordinaten im Gitter, und nicht mit x und y Pixelkoordinaten zu
 * positionieren. Dementsprechend beziehen sich {@link TileGraphics#x} und {@link TileGraphics#y}
 * auf diese Koordinaten im Gitter.
 *
 * Die endgültige (Pixel-)Position des Blocks ergibt sich durch Multiplikation der Gitterkoordinaten
 * mit dem vor Instanziierung (hoffentlich) sinnvoll gesetzten Attribut {@link TileGraphics#tileSize}
 */
public abstract class TileGraphics implements Drawable {
    protected int x;
    protected int y;

    protected static float tileSize = 45f;

    protected Paint tilePaint = new Paint();
    protected Bitmap tileBitmap = null;

    /**
     * Liefert Auskunft darüber, ob ein Block für den Spieler passierbar ist
     * @return <code>true</code> wenn passierbar, andernfalls <code>false</code>
     */
    public abstract boolean isPassable();

    public TileGraphics(int x, int y, InputStream is) {
        this.x = x;
        this.y = y;

        if(is != null) {
            tileBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is), (int)tileSize, (int)tileSize, true);
        }
    }


    /**
     * Verschieben des Blockes an neue Gitterkoordinaten
     * @param x neue X-Koordinate
     * @param y neue Y-Koordinate
     */
    public void move(int x, int y) {
        this.x = x;
        this.y = y;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void update(float fracsec) {}


    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(Canvas canvas) {
        // Aktuelle Transformationsmatrix speichern
        canvas.save();
        // Transformationsmatrix an Pixel-Koordinate von Block verschieben
        canvas.translate(x * tileSize, y * tileSize);
        // An der aktuellen Position ein Rechteck entsprechender Größe oder die existierende Bitmap
        if(tileBitmap == null)
            canvas.drawRect(0, 0, tileSize, tileSize, tilePaint);
        else
            canvas.drawBitmap(tileBitmap, 0, 0, null);
        // Transformationsmatrix auf den Stand von vorherigem canvas.save() zurücksetzen
        canvas.restore();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public static void setTileSize(float blockSize) {
        TileGraphics.tileSize = blockSize;
    }

    public static float getTileSize() {
        return tileSize;
    }
}
