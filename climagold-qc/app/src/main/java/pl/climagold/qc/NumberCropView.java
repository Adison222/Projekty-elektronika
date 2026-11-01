package pl.climagold.qc;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Drag a rectangle around one identifier. Coordinates are kept in bitmap space. */
final class NumberCropView extends View {
  private final Bitmap bitmap;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF image = new RectF(), selection = new RectF();
  private float anchorX, anchorY;

  NumberCropView(Context context, Bitmap bitmap) {
    super(context);
    this.bitmap = bitmap;
    selection.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    setContentDescription(
        "Przeciągnij palcem od lewego górnego do prawego dolnego rogu oznaczenia, aby zaznaczyć"
            + " jeden wiersz.");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float scale =
        Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
    float left = (getWidth() - bitmap.getWidth() * scale) / 2,
        top = (getHeight() - bitmap.getHeight() * scale) / 2;
    image.set(left, top, left + bitmap.getWidth() * scale, top + bitmap.getHeight() * scale);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.WHITE);
    canvas.drawBitmap(bitmap, null, image, paint);
    RectF box =
        new RectF(
            left + selection.left * scale,
            top + selection.top * scale,
            left + selection.right * scale,
            top + selection.bottom * scale);
    paint.setColor(0x8807121E);
    canvas.drawRect(image.left, image.top, image.right, box.top, paint);
    canvas.drawRect(image.left, box.bottom, image.right, image.bottom, paint);
    canvas.drawRect(image.left, box.top, box.left, box.bottom, paint);
    canvas.drawRect(box.right, box.top, image.right, box.bottom, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
    paint.setColor(MainActivity.GOLD);
    canvas.drawRect(box, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (image.width() <= 0 || event.getPointerCount() != 1) return true;
    float x =
        Math.max(
            0,
            Math.min(
                bitmap.getWidth(),
                (event.getX() - image.left) * bitmap.getWidth() / image.width()));
    float y =
        Math.max(
            0,
            Math.min(
                bitmap.getHeight(),
                (event.getY() - image.top) * bitmap.getHeight() / image.height()));
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      anchorX = x;
      anchorY = y;
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE
        || event.getActionMasked() == MotionEvent.ACTION_UP) {
      selection.set(
          Math.min(anchorX, x), Math.min(anchorY, y), Math.max(anchorX, x), Math.max(anchorY, y));
      invalidate();
      if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  void reset() {
    selection.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    invalidate();
  }

  Bitmap crop() {
    int x = Math.max(0, (int) selection.left), y = Math.max(0, (int) selection.top);
    int w = Math.min(bitmap.getWidth() - x, (int) selection.width()),
        h = Math.min(bitmap.getHeight() - y, (int) selection.height());
    if (w < 10 || h < 10)
      throw new IllegalArgumentException("Zaznacz większy obszar z całym oznaczeniem.");
    return Bitmap.createBitmap(bitmap, x, y, w, h);
  }
}
