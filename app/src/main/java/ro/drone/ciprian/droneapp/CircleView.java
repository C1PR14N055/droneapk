package ro.drone.ciprian.droneapp;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Created by Admin on 01/07.
 */
public class CircleView extends View {

    private static final String COLOR_RED = "#e74c3c";
    private static final String COLOR_ORANGE = "#e67e22";
    private static final String COLOR_GREEN = "#2ecc71";

    private final Paint drawPaint;
    private float size;
    private Context mContext;

    public CircleView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        drawPaint = new Paint();
        drawPaint.setColor(Color.parseColor(COLOR_RED));
        drawPaint.setAntiAlias(true);
        setOnMeasureCallback();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(size, size, size, drawPaint);
    }

    /**
     * @param status
     * -1 = red
     * 0 = orange
     * 1 = green
     */
    public void setColor(int status) {
        switch (status) {
            case -1: {
                drawPaint.setColor(mContext.getResources().getColor(R.color.FLAT_RED));
                break;
            }
            case 0: {
                drawPaint.setColor(mContext.getResources().getColor(R.color.FLAT_ORANGE));
                break;
            }
            case 1: {
                drawPaint.setColor(mContext.getResources().getColor(R.color.FLAT_GREEN));
                break;
            }
        }
    }

    private void setOnMeasureCallback() {
        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                removeOnGlobalLayoutListener(this);
                size = getMeasuredWidth() / 2;
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void removeOnGlobalLayoutListener(ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        } else {
            getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }
}
