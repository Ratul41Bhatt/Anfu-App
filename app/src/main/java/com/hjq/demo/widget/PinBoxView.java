package com.hjq.demo.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A row of PIN entry boxes that GROWS with the PIN being typed.
 *
 * <p>The Anfu SDK's built-in PIN pad draws a fixed number of boxes - whatever
 * {@code setPasswordLengthLimit[2]} (the maximum) says. That is why passing
 * {@code {0,4,6}} drew six boxes for a four digit PIN, and why passing
 * {@code {0,4,4}} pinned the display at four and made six digit PINs
 * impossible.</p>
 *
 * <p>This view is the display half of the fix. It shows {@link #setMinBoxes
 * minBoxes} (4) boxes when empty and adds one box each time a digit is entered,
 * up to {@link #setMaxBoxes maxBoxes} (12, the ISO 9564 limit). The digit count
 * comes from the SDK's own {@code IPinPadResultListener}, so the PIN itself is
 * never seen by the application - only how many digits are in it.</p>
 *
 * <p>Nothing here is security sensitive: it draws boxes and dots.</p>
 */
public final class PinBoxView extends View {

    /** ISO 9564 allows 4 to 12 PIN digits. */
    public static final int DEFAULT_MIN_BOXES = 4;
    public static final int DEFAULT_MAX_BOXES = 12;

    private static final int BOX_STROKE_COLOR = 0xFF9E9E9E;
    private static final int BOX_FILL_COLOR = 0xFFFFFFFF;
    private static final int BOX_ACTIVE_COLOR = 0xFF1976D2;
    private static final int DOT_COLOR = 0xFF212121;

    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private int mMinBoxes = DEFAULT_MIN_BOXES;
    private int mMaxBoxes = DEFAULT_MAX_BOXES;
    /** Number of digits entered so far. */
    private int mCount = 0;

    private final float mBoxHeight;
    private final float mBoxGap;
    private final float mCorner;
    private final float mDotRadius;

    public PinBoxView(Context context) {
        this(context, null);
    }

    public PinBoxView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PinBoxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float d = context.getResources().getDisplayMetrics().density;
        mBoxHeight = 44 * d;
        mBoxGap = 6 * d;
        mCorner = 4 * d;
        mDotRadius = 6 * d;

        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(2 * d);
        mStroke.setColor(BOX_STROKE_COLOR);

        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(BOX_FILL_COLOR);

        mDot.setStyle(Paint.Style.FILL);
        mDot.setColor(DOT_COLOR);
    }

    /** Boxes shown before anything is typed. Defaults to 4. */
    public void setMinBoxes(int n) {
        mMinBoxes = Math.max(1, n);
        requestLayout();
        invalidate();
    }

    /** Hard ceiling on the number of boxes. Defaults to 12. */
    public void setMaxBoxes(int n) {
        mMaxBoxes = Math.max(mMinBoxes, n);
        requestLayout();
        invalidate();
    }

    /**
     * Set how many digits have been entered.
     *
     * <p>Clamped to {@code [0, maxBoxes]}. The box count follows: it stays at
     * {@code minBoxes} until the PIN is longer than that, then tracks the PIN
     * length exactly.</p>
     */
    public void setCount(int count) {
        int c = Math.max(0, Math.min(mMaxBoxes, count));
        if (c == mCount) {
            return;
        }
        int before = boxCount();
        mCount = c;
        if (boxCount() != before) {
            requestLayout();
        }
        invalidate();
    }

    public int getCount() {
        return mCount;
    }

    /** Digits entered so far, +1. Returns the new count. */
    public int increment() {
        setCount(mCount + 1);
        return mCount;
    }

    /** Backspace. Returns the new count. */
    public int decrement() {
        setCount(mCount - 1);
        return mCount;
    }

    public void clear() {
        setCount(0);
    }

    /** 4 while the PIN is 4 digits or shorter, then one box per digit. */
    public int boxCount() {
        return Math.max(mMinBoxes, Math.min(mMaxBoxes, mCount));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int n = boxCount();
        // Width is driven by the parent when it can be: the boxes then share the
        // available width, so growing from 4 to 12 never overflows the screen.
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (wMode == MeasureSpec.UNSPECIFIED) {
            w = (int) (n * (mBoxHeight + mBoxGap));
        }
        int h = (int) mBoxHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = boxCount();
        if (n <= 0) {
            return;
        }
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float available = getWidth() - getPaddingLeft() - getPaddingRight();
        float boxW = (available - mBoxGap * (n - 1)) / n;
        // Keep the boxes squarish; centre the row when they would be too wide.
        float maxW = mBoxHeight * 1.1f;
        if (boxW > maxW) {
            boxW = maxW;
            float used = boxW * n + mBoxGap * (n - 1);
            left += (available - used) / 2f;
        }
        float bottom = top + mBoxHeight;

        for (int i = 0; i < n; i++) {
            float x = left + i * (boxW + mBoxGap);
            mRect.set(x, top, x + boxW, bottom);
            canvas.drawRoundRect(mRect, mCorner, mCorner, mFill);
            // Highlight the box the next digit will land in.
            mStroke.setColor(i == mCount ? BOX_ACTIVE_COLOR : BOX_STROKE_COLOR);
            canvas.drawRoundRect(mRect, mCorner, mCorner, mStroke);
            if (i < mCount) {
                canvas.drawCircle(mRect.centerX(), mRect.centerY(), mDotRadius, mDot);
            }
        }
    }

    /** Convenience for hosts that want the row on a coloured background. */
    public void setDotColor(int color) {
        mDot.setColor(color == 0 ? DOT_COLOR : color);
        invalidate();
    }

    public void setBoxFillColor(int color) {
        mFill.setColor(color == 0 ? Color.WHITE : color);
        invalidate();
    }
}
