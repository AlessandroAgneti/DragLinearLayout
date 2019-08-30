package com.example.testaddviewphoto;

import android.animation.*;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.core.view.MotionEventCompat;

public class DragLinearLayout extends LinearLayout {
    private static final String TAG = DragLinearLayout.class.getSimpleName();
    private static final long NOMINAL_SWITCH_DURATION = 150;
    private static final long MIN_SWITCH_DURATION = NOMINAL_SWITCH_DURATION;
    private static final long MAX_SWITCH_DURATION = NOMINAL_SWITCH_DURATION * 2;
    private static final float NOMINAL_DISTANCE = 20;
    private final float mNominalDistanceScaled;
    private final DragItem mDragItem;
    private final int mSlop;
    private static final int INVALID_POINTER_ID = -1;
    private int mDownY = -1;
    private int mDownX = -1;
    private int mActivePointerId = INVALID_POINTER_ID;
    private LayoutTransition mLayoutTransition;
    private final SparseArray<DraggableChild> mDraggableChildren;
    private boolean mIsLongClickDraggable = false;
    private ILongClickToDragListener mClickToDragListener;
    private boolean mIsEnterLongClick = false;
    private LongClickDragListener mLongClickDragListener = new LongClickDragListener();

    public DragLinearLayout(Context context) {
        this(context, null);
    }

    public DragLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDraggableChildren = new SparseArray<>();
        mDragItem = new DragItem();
        ViewConfiguration vc = ViewConfiguration.get(context);
        mSlop = vc.getScaledTouchSlop();
        final Resources resources = getResources();
        mNominalDistanceScaled = (int) (NOMINAL_DISTANCE * resources.getDisplayMetrics().density + 0.5f);
    }

    private class DragItem {
        private View mView;
        private int mStartVisibility;
        private BitmapDrawable mBitmapDrawable;
        private int mPosition;
        private int mStartTop;
        private int mHeight;
        private int mTotalDragOffset;
        private int mTargetTopOffset;
        private int mStartLeft;
        private int mWidth;
        private int mTargetLeftOffset;
        private ValueAnimator mSettleAnimation;
        private boolean mDetecting;
        private boolean mDragging;

        DragItem() {
            stopDetecting();
        }

        void startDetectingOnPossibleDrag(final View view, final int position) {
            this.mView = view;
            this.mStartVisibility = view.getVisibility();
            this.mBitmapDrawable = getDragDrawable(view);
            this.mPosition = position;
            this.mStartTop = view.getTop();
            this.mHeight = view.getHeight();
            mStartLeft = view.getLeft();
            mWidth = view.getWidth();
            this.mTotalDragOffset = 0;
            this.mTargetTopOffset = 0;
            this.mTargetLeftOffset = 0;
            this.mSettleAnimation = null;
            this.mDetecting = true;
        }

        void onDragStart() {
            mView.setVisibility(View.INVISIBLE);
            this.mDragging = true;
        }

        void setTotalOffset(int offset) {
            mTotalDragOffset = offset;
            updateTargetLocation();
        }

        void updateTargetLocation() {
            if (getOrientation() == VERTICAL) {
                updateTargetTop();
            } else {
                updateTargetLeft();
            }
        }

        private void updateTargetLeft() {
            mTargetLeftOffset = mStartLeft - mView.getLeft() + mTotalDragOffset;
        }

        private void updateTargetTop() {
            mTargetTopOffset = mStartTop - mView.getTop() + mTotalDragOffset;
        }

        void onDragStop() {
            this.mDragging = false;
        }

        boolean settling() {
            return null != mSettleAnimation;
        }

        void stopDetecting() {
            this.mDetecting = false;
            if (null != mView) mView.setVisibility(mStartVisibility);
            mView = null;
            mStartVisibility = -1;
            mBitmapDrawable = null;
            mPosition = -1;
            mStartTop = -1;
            mHeight = -1;
            mStartLeft = -1;
            mWidth = -1;
            mTotalDragOffset = 0;
            mTargetTopOffset = 0;
            mTargetLeftOffset = 0;
            if (null != mSettleAnimation) mSettleAnimation.end();
            mSettleAnimation = null;
        }
    }

    private class DraggableChild {
        private ValueAnimator mValueAnimator;
        void endExistingAnimation() {
            if (null != mValueAnimator) mValueAnimator.end();
        }
        void cancelExistingAnimation() {
            if (null != mValueAnimator) mValueAnimator.cancel();
        }
    }

    private class DragHandleOnTouchListener implements OnTouchListener {
        private final View view;

        DragHandleOnTouchListener(final View view) {
            this.view = view;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (MotionEvent.ACTION_DOWN == MotionEventCompat.getActionMasked(event)) {
                startDetectingDrag(view);
            }
            return false;
        }
    }

    public class LongClickDragListener implements OnLongClickListener {

        @Override
        public boolean onLongClick(View v) {
            if (!mIsLongClickDraggable) {
                return false;
            }
            mIsEnterLongClick = true;
            if (mClickToDragListener != null) {
                mClickToDragListener.onLongClickToDrag(v);
            }
            startDetectingDrag(v);
            return true;
        }
    }

    public void addDragView(View child, View dragHandle) {
        addView(child);
        setViewDraggable(child, dragHandle);
    }

    public void setViewDraggable(View child, View dragHandle) {
        if (null == child || null == dragHandle) {
            throw new IllegalArgumentException(
                    "Draggable children and their drag handles must not be null.");
        }

        if (this == child.getParent()) {
            dragHandle.setOnTouchListener(new DragHandleOnTouchListener(child));
            dragHandle.setOnLongClickListener(mLongClickDragListener);
            mDraggableChildren.put(indexOfChild(child), new DraggableChild());
        } else {
            Log.e(TAG, child + " is not a child, cannot make draggable.");
        }
    }

    @Override
    public void removeAllViews() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).setOnLongClickListener(null);
            getChildAt(i).setOnTouchListener(null);
        }
        super.removeAllViews();
        mDraggableChildren.clear();
    }

    private long getTranslateAnimationDuration(float distance) {
        return Math.min(MAX_SWITCH_DURATION, Math.max(MIN_SWITCH_DURATION,
                (long) (NOMINAL_SWITCH_DURATION * Math.abs(distance) / mNominalDistanceScaled)));
    }

    public void startDetectingDrag(View child) {
        if (mDragItem.mDetecting)
            return;

        final int position = indexOfChild(child);
        if (position>=0) {
            mDraggableChildren.get(position).endExistingAnimation();
            mDragItem.startDetectingOnPossibleDrag(child, position);
        }
    }

    private void startDrag() {
        mLayoutTransition = getLayoutTransition();
        if (mLayoutTransition != null) {
            setLayoutTransition(null);
        }
        mDragItem.onDragStart();
        requestDisallowInterceptTouchEvent(true);
    }

    private void onDragStop() {
        if (getOrientation() == VERTICAL) {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(mDragItem.mTotalDragOffset,
                    mDragItem.mTotalDragOffset - mDragItem.mTargetTopOffset)
                    .setDuration(getTranslateAnimationDuration(mDragItem.mTargetTopOffset));
        } else {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(mDragItem.mTotalDragOffset,
                    mDragItem.mTotalDragOffset - mDragItem.mTargetLeftOffset)
                    .setDuration(getTranslateAnimationDuration(mDragItem.mTargetLeftOffset));
        }

        mDragItem.mSettleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!mDragItem.mDetecting) return;
                mDragItem.setTotalOffset(((Float) animation.getAnimatedValue()).intValue());
                invalidate();
            }
        });

        mDragItem.mSettleAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDragItem.onDragStop();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mDragItem.mDetecting) {
                    return;
                }

                mDragItem.mSettleAnimation = null;
                mDragItem.stopDetecting();

                if (mLayoutTransition != null && getLayoutTransition() == null) {
                    setLayoutTransition(mLayoutTransition);
                }
            }
        });
        mDragItem.mSettleAnimation.start();
    }

    private void onDrag(final int offset) {
        if (getOrientation() == VERTICAL) {
            mDragItem.setTotalOffset(offset);
            invalidate();
            int currentTop = mDragItem.mStartTop + mDragItem.mTotalDragOffset;
            int belowPosition = nextDraggablePosition(mDragItem.mPosition);
            int abovePosition = previousDraggablePosition(mDragItem.mPosition);
            View belowView = getChildAt(belowPosition);
            View aboveView = getChildAt(abovePosition);
            final boolean isBelow = (belowView != null) &&
                    (currentTop + mDragItem.mHeight > belowView.getTop() + belowView.getHeight() / 2);
            final boolean isAbove = (aboveView != null) &&
                    (currentTop < aboveView.getTop() + aboveView.getHeight() / 2);
            if (isBelow || isAbove) {
                final View switchView = isBelow ? belowView : aboveView;
                final int originalPosition = mDragItem.mPosition;
                final int switchPosition = isBelow ? belowPosition : abovePosition;
                mDraggableChildren.get(switchPosition).cancelExistingAnimation();
                final float switchViewStartY = switchView.getY();

                if (isBelow) {
                    removeViewAt(originalPosition);
                    removeViewAt(switchPosition - 1);
                    addView(belowView, originalPosition);
                    addView(mDragItem.mView, switchPosition);
                } else {
                    removeViewAt(switchPosition);
                    removeViewAt(originalPosition - 1);
                    addView(mDragItem.mView, switchPosition);
                    addView(aboveView, originalPosition);
                }
                mDragItem.mPosition = switchPosition;
                final ViewTreeObserver switchViewObserver = switchView.getViewTreeObserver();
                switchViewObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        switchViewObserver.removeOnPreDrawListener(this);
                        final ObjectAnimator switchAnimator = ObjectAnimator.ofFloat(switchView, "y",
                                switchViewStartY, switchView.getTop())
                                .setDuration(getTranslateAnimationDuration(switchView.getTop() - switchViewStartY));
                        switchAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                mDraggableChildren.get(originalPosition).mValueAnimator = switchAnimator;
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mDraggableChildren.get(originalPosition).mValueAnimator = null;
                            }
                        });
                        switchAnimator.start();

                        return true;
                    }
                });

                final ViewTreeObserver observer = mDragItem.mView.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        observer.removeOnPreDrawListener(this);
                        mDragItem.updateTargetLocation();
                        if (mDragItem.settling()) {
                            Log.d(TAG, "Updating settle animation");
                            mDragItem.mSettleAnimation.removeAllListeners();
                            mDragItem.mSettleAnimation.cancel();
                            onDragStop();
                        }
                        return true;
                    }
                });
            }
        } else {
            mDragItem.setTotalOffset(offset);
            invalidate();
            int currentLeft = mDragItem.mStartLeft + mDragItem.mTotalDragOffset;
            int nextPosition = nextDraggablePosition(mDragItem.mPosition);
            int prePosition = previousDraggablePosition(mDragItem.mPosition);
            View nextView = getChildAt(nextPosition);
            View preView = getChildAt(prePosition);
            final boolean isToNext = (nextView != null) &&
                    (currentLeft + mDragItem.mWidth > nextView.getLeft() + nextView.getWidth() / 2);
            final boolean isToPre = (preView != null) &&
                    (currentLeft < preView.getLeft() + preView.getWidth() / 2);
            if (isToNext || isToPre) {
                final View switchView = isToNext ? nextView : preView;
                final int originalPosition = mDragItem.mPosition;
                final int switchPosition = isToNext ? nextPosition : prePosition;
                mDraggableChildren.get(switchPosition).cancelExistingAnimation();
                final float switchViewStartX = switchView.getX();
                if (isToNext) {
                    removeViewAt(originalPosition);
                    removeViewAt(switchPosition - 1);
                    addView(nextView, originalPosition);
                    addView(mDragItem.mView, switchPosition);
                } else {
                    removeViewAt(switchPosition);
                    removeViewAt(originalPosition - 1);
                    addView(mDragItem.mView, switchPosition);
                    addView(preView, originalPosition);
                }
                mDragItem.mPosition = switchPosition;
                final ViewTreeObserver switchViewObserver = switchView.getViewTreeObserver();
                switchViewObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        switchViewObserver.removeOnPreDrawListener(this);
                        final ObjectAnimator switchAnimator = ObjectAnimator.ofFloat(switchView, "x",
                                switchViewStartX, switchView.getLeft())
                                .setDuration(getTranslateAnimationDuration(switchView.getLeft() - switchViewStartX));
                        switchAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                mDraggableChildren.get(originalPosition).mValueAnimator = switchAnimator;
                            }
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mDraggableChildren.get(originalPosition).mValueAnimator = null;
                            }
                        });
                        switchAnimator.start();
                        return true;
                    }
                });

                final ViewTreeObserver observer = mDragItem.mView.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        observer.removeOnPreDrawListener(this);
                        mDragItem.updateTargetLocation();
                        if (mDragItem.settling()) {
                            mDragItem.mSettleAnimation.removeAllListeners();
                            mDragItem.mSettleAnimation.cancel();
                            onDragStop();
                        }
                        return true;
                    }
                });
            }
        }
    }

    private int previousDraggablePosition(int position) {
        int startIndex = mDraggableChildren.indexOfKey(position);
        if (startIndex < 1 || startIndex > mDraggableChildren.size()) return -1;
        return mDraggableChildren.keyAt(startIndex - 1);
    }

    private int nextDraggablePosition(int position) {
        int startIndex = mDraggableChildren.indexOfKey(position);
        if (startIndex < -1 || startIndex > mDraggableChildren.size() - 2) return -1;
        return mDraggableChildren.keyAt(startIndex + 1);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mDragItem.mDetecting && (mDragItem.mDragging || mDragItem.settling())) {
            canvas.save();
            if (getOrientation() == VERTICAL) {
                canvas.translate(0, mDragItem.mTotalDragOffset);
            } else {
                canvas.translate(mDragItem.mTotalDragOffset, 0);
            }
            mDragItem.mBitmapDrawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!mIsLongClickDraggable) {
            return super.onInterceptTouchEvent(event);
        }
        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (mDragItem.mDetecting) return false;
                mDownY = (int) MotionEventCompat.getY(event, 0);
                mDownX = (int) MotionEventCompat.getX(event, 0);
                mActivePointerId = MotionEventCompat.getPointerId(event, 0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mIsLongClickDraggable) {
                    return super.onInterceptTouchEvent(event);
                }
                 if (!mDragItem.mDetecting) return false;
                if (INVALID_POINTER_ID == mActivePointerId) break;
                final int pointerIndex = event.findPointerIndex(mActivePointerId);
                final float y = MotionEventCompat.getY(event, pointerIndex);
                final float x = MotionEventCompat.getX(event, pointerIndex);
                final float dy = y - mDownY;
                final float dx = x - mDownX;
                if (getOrientation() == VERTICAL) {
                    if (Math.abs(dy) > mSlop) {
                        startDrag();
                        return true;
                    }
                } else {
                    if (Math.abs(dx) > mSlop) {
                        startDrag();
                        return true;
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = MotionEventCompat.getActionIndex(event);
                final int pointerId = MotionEventCompat.getPointerId(event, pointerIndex);

                if (pointerId != mActivePointerId)
                    break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                getParent().requestDisallowInterceptTouchEvent(false);
                onTouchEnd();
                if (mDragItem.mDetecting) mDragItem.stopDetecting();
                break;
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!mIsLongClickDraggable) {
            return super.onTouchEvent(event);
        }
        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN: {
                if (!mDragItem.mDetecting || mDragItem.settling()) return false;
                startDrag();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mIsEnterLongClick) {
                    return super.onTouchEvent(event);
                }
                if (!mDragItem.mDragging) break;
                if (INVALID_POINTER_ID == mActivePointerId) break;
                int pointerIndex = event.findPointerIndex(mActivePointerId);
                int lastEventY = (int) MotionEventCompat.getY(event, pointerIndex);
                int lastEventX = (int) MotionEventCompat.getX(event, pointerIndex);
                if (getOrientation() == VERTICAL) {
                    int deltaY = lastEventY - mDownY;
                    onDrag(deltaY);
                } else {
                    int deltaX = lastEventX - mDownX;
                    onDrag(deltaX);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = MotionEventCompat.getActionIndex(event);
                final int pointerId = MotionEventCompat.getPointerId(event, pointerIndex);
                if (pointerId != mActivePointerId)
                    break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                onTouchEnd();

                if (mDragItem.mDragging) {
                    onDragStop();
                } else if (mDragItem.mDetecting) {
                    mDragItem.stopDetecting();
                }
                return true;
            }
        }
        return false;
    }

    private void onTouchEnd() {
        mDownY = -1;
        mDownX = -1;
        mIsEnterLongClick = false;
        mActivePointerId = INVALID_POINTER_ID;
    }

    private BitmapDrawable getDragDrawable(View view) {
        int top = view.getTop();
        int left = view.getLeft();
        Bitmap bitmap = getBitmapFromView(view);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setBounds(new Rect(left, top, left + view.getWidth(), top + view.getHeight()));
        return drawable;
    }

    private static Bitmap getBitmapFromView(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    public void setClickToDragListener(ILongClickToDragListener clickToDragListener) {
        mClickToDragListener = clickToDragListener;
    }

    public void setLongClickDrag(boolean longClickDrag) {
        if (mIsLongClickDraggable != longClickDrag) {
            mIsLongClickDraggable = longClickDrag;
        }
    }

    public interface ILongClickToDragListener {

        void onLongClickToDrag(View dragableView);
    }

}