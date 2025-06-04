package com.example.storythere;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

public class ZoomLayout extends FrameLayout {
    private ScaleGestureDetector scaleDetector;
    private final Matrix matrix = new Matrix();
    private float scale = 1f;
    private final PointF last = new PointF();
    private final PointF start = new PointF();

    public ZoomLayout(Context context) {
        super(context);
        init(context);
    }

    public ZoomLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                start.set(last);
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = curr.x - last.x;
                float deltaY = curr.y - last.y;
                float fixTransX = getFixDragTrans(deltaX, getWidth(), getWidth() * scale);
                float fixTransY = getFixDragTrans(deltaY, getHeight(), getHeight() * scale);
                matrix.postTranslate(fixTransX, fixTransY);
                fixTrans();
                last.set(curr.x, curr.y);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                break;
        }

        setImageMatrix(matrix);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scale *= detector.getScaleFactor();
            float maxScale = 5f;
            float minScale = 0.5f;
            scale = Math.max(minScale, Math.min(scale, maxScale));
            matrix.setScale(scale, scale, detector.getFocusX(), detector.getFocusY());
            fixTrans();
            return true;
        }
    }

    private void fixTrans() {
        float[] values = new float[9];
        matrix.getValues(values);
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float fixTransX = getFixTrans(transX, getWidth(), getWidth() * scale);
        float fixTransY = getFixTrans(transY, getHeight(), getHeight() * scale);
        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;
        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }
        if (trans < minTrans) {
            return -trans + minTrans;
        }
        if (trans > maxTrans) {
            return -trans + maxTrans;
        }
        return 0;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    private void setImageMatrix(Matrix matrix) {
        if (getChildCount() > 0) {
            float[] values = new float[9];
            matrix.getValues(values);
            getChildAt(0).setScaleX(scale);
            getChildAt(0).setScaleY(scale);
            getChildAt(0).setTranslationX(values[Matrix.MTRANS_X]);
            getChildAt(0).setTranslationY(values[Matrix.MTRANS_Y]);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
} 