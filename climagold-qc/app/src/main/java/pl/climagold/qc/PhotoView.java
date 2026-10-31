package pl.climagold.qc;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Pinch, drag and double-tap; only the preview is downsampled. */
// This app intentionally uses the platform Material theme, not AppCompat.
@android.annotation.SuppressLint("AppCompatCustomView")
public final class PhotoView extends ImageView {
  private final Matrix transform = new Matrix();
  private float scale = 1, x, y;
  private final ScaleGestureDetector pinch;
  private final GestureDetector gestures;

  public PhotoView(Context c) {
    super(c);
    setScaleType(ScaleType.MATRIX);
    pinch =
        new ScaleGestureDetector(
            c,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              public boolean onScale(ScaleGestureDetector d) {
                float next = Math.max(.25f, Math.min(8f, scale * d.getScaleFactor()));
                transform.postScale(next / scale, next / scale, d.getFocusX(), d.getFocusY());
                scale = next;
                setImageMatrix(transform);
                return true;
              }
            });
    gestures =
        new GestureDetector(
            c,
            new GestureDetector.SimpleOnGestureListener() {
              public boolean onDown(MotionEvent e) {
                return true;
              }

              public boolean onDoubleTap(MotionEvent e) {
                fit();
                return true;
              }
            });
  }

  public void fit() {
    if (getDrawable() == null || getWidth() == 0) return;
    float w = getDrawable().getIntrinsicWidth(), h = getDrawable().getIntrinsicHeight();
    scale = Math.min(getWidth() / w, getHeight() / h);
    transform.setScale(scale, scale);
    transform.postTranslate((getWidth() - w * scale) / 2, (getHeight() - h * scale) / 2);
    setImageMatrix(transform);
  }

  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    fit();
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }

  public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_UP) performClick();
    pinch.onTouchEvent(e);
    gestures.onTouchEvent(e);
    if (e.getActionMasked() == MotionEvent.ACTION_MOVE
        && e.getPointerCount() == 1
        && !pinch.isInProgress()) {
      transform.postTranslate(e.getX() - x, e.getY() - y);
      setImageMatrix(transform);
    }
    x = e.getX();
    y = e.getY();
    return true;
  }
}
