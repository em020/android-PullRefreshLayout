package com.baoyz.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ImageView;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidParameterException;

/**
 * Created by baoyz on 14/10/30.
 */
public class PullRefreshLayout extends ViewGroup {

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int DRAG_MAX_DISTANCE = 64;
    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = .5f;

    public static final int STYLE_MATERIAL = 0;
    public static final int STYLE_CIRCLES = 1;
    public static final int STYLE_WATER_DROP = 2;
    public static final int STYLE_RING = 3;
    public static final int STYLE_SMARTISAN = 4;
    public static final int STYLE_CUSTOM = 5;

    private View mTarget;
    private ImageView mRefreshView;
    private Interpolator mDecelerateInterpolator;
    private int mTouchSlop;
    private int mSpinnerFinalOffset;
    private int mTotalDragDistance;
    private RefreshDrawable mRefreshDrawable;
    private int mCurrentOffsetTop;
    private boolean mRefreshing;
    private int mActivePointerId;
    private boolean mIsBeingDragged;
    private float mInitialMotionY;
    private int mFrom;
    private boolean mNotify;
    private OnRefreshListener mListener;
    private int[] mColorSchemeColors;

    public int mDurationToStartPosition;
    public int mDurationToCorrectPosition;
    private int mInitialOffsetTop;
    private boolean mDispatchTargetTouchDown;
    private float mDragPercent;
    private String customRefreshDrawableClassName;

    public PullRefreshLayout(Context context) {
        this(context, null);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.refreshLayoutStyle);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.refresh_PullRefreshLayout, defStyleAttr, R.style.RefreshLayoutDefaultStyle);
        final int type = a.getInteger(R.styleable.refresh_PullRefreshLayout_refreshType, STYLE_MATERIAL);
        final int colorsId = a.getResourceId(R.styleable.refresh_PullRefreshLayout_refreshColors, 0);
        final int colorId = a.getResourceId(R.styleable.refresh_PullRefreshLayout_refreshColor, 0);
        customRefreshDrawableClassName = a.getString(R.styleable.refresh_PullRefreshLayout_refreshDrawableClass);
        a.recycle();

        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        int defaultDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mDurationToStartPosition = defaultDuration;
        mDurationToCorrectPosition = defaultDuration;
        mSpinnerFinalOffset = mTotalDragDistance = dp2px(DRAG_MAX_DISTANCE);

        if (colorsId > 0) {
            mColorSchemeColors = context.getResources().getIntArray(colorsId);
        } else {
            mColorSchemeColors = new int[]{Color.rgb(0xC9, 0x34, 0x37), Color.rgb(0x37, 0x5B, 0xF1), Color.rgb(0xF7, 0xD2, 0x3E), Color.rgb(0x34, 0xA3, 0x50)};
        }

        if (colorId > 0) {
            mColorSchemeColors = new int[]{context.getResources().getColor(colorId)};
        }

        mRefreshView = new ImageView(context);
        setRefreshStyle(type);
        mRefreshView.setVisibility(View.GONE);
        addView(mRefreshView, 0);
        setWillNotDraw(false);
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    }

    public void setColorSchemeColors(int... colorSchemeColors) {
        mColorSchemeColors = colorSchemeColors;
        mRefreshDrawable.setColorSchemeColors(colorSchemeColors);
    }

    public void setColor(int color) {
        setColorSchemeColors(color);
    }

    public void setRefreshStyle(int type) {
        setRefreshing(false);
        switch (type) {
            case STYLE_MATERIAL:
                mRefreshDrawable = new MaterialDrawable(getContext(), this);
                break;
            case STYLE_CIRCLES:
                mRefreshDrawable = new CirclesDrawable(getContext(), this);
                break;
            case STYLE_WATER_DROP:
                mRefreshDrawable = new WaterDropDrawable(getContext(), this);
                break;
            case STYLE_RING:
                mRefreshDrawable = new RingDrawable(getContext(), this);
                break;
            case STYLE_SMARTISAN:
                mRefreshDrawable = new SmartisanDrawable(getContext(), this);
                break;
            case STYLE_CUSTOM:
                if (TextUtils.isEmpty(customRefreshDrawableClassName)) {
                    throw new InvalidParameterException("Selected STYLE_CUSTOM but no drawable class provided");
                } else {
                    mRefreshDrawable = new MaterialDrawable(getContext(), this);
                    try {
                        Class<?> clazz = Class.forName(customRefreshDrawableClassName);
                        Constructor<?> cons = clazz.getConstructor(Context.class, PullRefreshLayout.class);
                        Object obj = cons.newInstance(getContext(), this);
                        if (obj instanceof RefreshDrawable) {
                            mRefreshDrawable = (RefreshDrawable) obj;
                        } else {
                            throw new InvalidParameterException("Wrong refresh drawable type");
                        }

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                throw new InvalidParameterException("Type does not exist");
        }
        mRefreshDrawable.setColorSchemeColors(mColorSchemeColors);
        mRefreshView.setImageDrawable(mRefreshDrawable);
    }

    public void setRefreshDrawable(RefreshDrawable drawable) {
        setRefreshing(false);
        mRefreshDrawable = drawable;
        mRefreshDrawable.setColorSchemeColors(mColorSchemeColors);
        mRefreshView.setImageDrawable(mRefreshDrawable);
    }

    public int getFinalOffset() {
        return mSpinnerFinalOffset;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        ensureTarget();
        if (mTarget == null)
            return;

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingRight() - getPaddingLeft(), MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY);
        mTarget.measure(widthMeasureSpec, heightMeasureSpec);
        mRefreshView.measure(widthMeasureSpec, heightMeasureSpec);
//        mRefreshView.measure(MeasureSpec.makeMeasureSpec(mRefreshViewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mRefreshViewHeight, MeasureSpec.EXACTLY));
    }

    private void ensureTarget() {
        if (mTarget != null)
            return;
        if (getChildCount() > 0) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != mRefreshView)
                    mTarget = child;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (!isEnabled() || (canChildScrollUp() && !mRefreshing)) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!mRefreshing) {
                    setTargetOffsetTop(0, true);
                }
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsBeingDragged = false;
                final float initialMotionY = getMotionEventY(ev, mActivePointerId);
                if (initialMotionY == -1) {
                    return false;
                }
                mInitialMotionY = initialMotionY;
                mInitialOffsetTop = mCurrentOffsetTop;
                mDispatchTargetTouchDown = false;
                mDragPercent = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }
                final float y = getMotionEventY(ev, mActivePointerId);
                if (y == -1) {
                    return false;
                }
                final float yDiff = y - mInitialMotionY;
                if (mRefreshing) {
                    mIsBeingDragged = !(yDiff < 0 && mCurrentOffsetTop <= 0);
                } else if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (!mIsBeingDragged) {
            return super.onTouchEvent(ev);
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = y - mInitialMotionY;
                int targetY;
                if (mRefreshing) {
                    targetY = (int) (mInitialOffsetTop + yDiff);
                    if (canChildScrollUp()) {
                        targetY = -1;
                        mInitialMotionY = y;
                        mInitialOffsetTop = 0;
                        if (mDispatchTargetTouchDown) {
                            mTarget.dispatchTouchEvent(ev);
                        } else {
                            MotionEvent obtain = MotionEvent.obtain(ev);
                            obtain.setAction(MotionEvent.ACTION_DOWN);
                            mDispatchTargetTouchDown = true;
                            mTarget.dispatchTouchEvent(obtain);
                        }
                    } else {
                        if (targetY < 0) {
                            if (mDispatchTargetTouchDown) {
                                mTarget.dispatchTouchEvent(ev);
                            } else {
                                MotionEvent obtain = MotionEvent.obtain(ev);
                                obtain.setAction(MotionEvent.ACTION_DOWN);
                                mDispatchTargetTouchDown = true;
                                mTarget.dispatchTouchEvent(obtain);
                            }
                            targetY = 0;
                        } else if (targetY > mTotalDragDistance) {
                            targetY = mTotalDragDistance;
                        } else {
                            if (mDispatchTargetTouchDown) {
                                MotionEvent obtain = MotionEvent.obtain(ev);
                                obtain.setAction(MotionEvent.ACTION_CANCEL);
                                mDispatchTargetTouchDown = false;
                                mTarget.dispatchTouchEvent(obtain);
                            }
                        }
                    }
                } else {
                    final float scrollTop = yDiff * DRAG_RATE;
                    float originalDragPercent = scrollTop / mTotalDragDistance;
                    if (originalDragPercent < 0) {
                        return false;
                    }
                    mDragPercent = Math.min(1f, Math.abs(originalDragPercent));
                    float extraOS = Math.abs(scrollTop) - mTotalDragDistance;
                    float slingshotDist = mSpinnerFinalOffset;
                    float tensionSlingshotPercent = Math.max(0,
                            Math.min(extraOS, slingshotDist * 2) / slingshotDist);
                    float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                            (tensionSlingshotPercent / 4), 2)) * 2f;
                    float extraMove = (slingshotDist) * tensionPercent * 2;
                    targetY = (int) ((slingshotDist * mDragPercent) + extraMove);
                    if (mRefreshView.getVisibility() != View.VISIBLE) {
                        mRefreshView.setVisibility(View.VISIBLE);
                    }
                    if (scrollTop < mTotalDragDistance) {
                        mRefreshDrawable.setPercent(mDragPercent);
                    }
                }
                setTargetOffsetTop(targetY - mCurrentOffsetTop, true);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN:
                final int index = MotionEventCompat.getActionIndex(ev);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }
                if (mRefreshing) {
                    if (mDispatchTargetTouchDown) {
                        mTarget.dispatchTouchEvent(ev);
                        mDispatchTargetTouchDown = false;
                    }
                    return false;
                }
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                mIsBeingDragged = false;
                if (overscrollTop > mTotalDragDistance) {
                    setRefreshing(true, true);
                } else {
                    mRefreshing = false;
                    animateOffsetToStartPosition();
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
        }

        return true;
    }

    public void setDurations(int durationToStartPosition, int durationToCorrectPosition) {
        mDurationToStartPosition = durationToStartPosition;
        mDurationToCorrectPosition = durationToCorrectPosition;
    }

    private void animateOffsetToStartPosition() {
        mFrom = mCurrentOffsetTop;
        mAnimateToStartPosition.reset();
        mAnimateToStartPosition.setDuration(mDurationToStartPosition);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToStartPosition.setAnimationListener(mToStartListener);
        mRefreshView.clearAnimation();
        mRefreshView.startAnimation(mAnimateToStartPosition);
    }

    private void animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop;
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(mDurationToCorrectPosition);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToCorrectPosition.setAnimationListener(mRefreshListener);
        mRefreshView.clearAnimation();
        mRefreshView.startAnimation(mAnimateToCorrectPosition);
    }

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int endTarget = mSpinnerFinalOffset;
            int targetTop = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            int offset = targetTop - mTarget.getTop();
            setTargetOffsetTop(offset, false /* requires update */);
        }
    };

    private void moveToStart(float interpolatedTime) {
        int targetTop = mFrom - (int) (mFrom * interpolatedTime);
        int offset = targetTop - mTarget.getTop();
        setTargetOffsetTop(offset, false);
        mRefreshDrawable.setPercent(mDragPercent * (1 - interpolatedTime));
    }

    public void setRefreshing(boolean refreshing) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, false /* notify */);
        }
    }

    private void setRefreshing(boolean refreshing, final boolean notify) {
        if (mRefreshing != refreshing) {
            mNotify = notify;
            ensureTarget();
            mRefreshing = refreshing;
            if (mRefreshing) {
                mRefreshDrawable.setPercent(1f);
                animateOffsetToCorrectPosition();
            } else {
                animateOffsetToStartPosition();
            }
        }
    }

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
            mRefreshView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (mRefreshing) {
                mRefreshDrawable.start();
                if (mNotify) {
                    if (mListener != null) {
                        mListener.onRefresh();
                    }
                }
            } else {
                mRefreshDrawable.stop();
                mRefreshView.setVisibility(View.GONE);
                animateOffsetToStartPosition();
            }
            mCurrentOffsetTop = mTarget.getTop();
        }
    };

    private Animation.AnimationListener mToStartListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
            mRefreshDrawable.stop();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
//            mRefreshDrawable.stop();
            mRefreshView.setVisibility(View.GONE);
            mCurrentOffsetTop = mTarget.getTop();
        }
    };

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
        }
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
        if (index < 0) {
            return -1;
        }
        return MotionEventCompat.getY(ev, index);
    }

    private void setTargetOffsetTop(int offset, boolean requiresUpdate) {
//        mRefreshView.bringToFront();
        mTarget.offsetTopAndBottom(offset);
        mCurrentOffsetTop = mTarget.getTop();
        mRefreshDrawable.offsetTopAndBottom(offset);
        if (requiresUpdate && android.os.Build.VERSION.SDK_INT < 11) {
            invalidate();
        }
    }

    // scroll up means scroll upwards
    private boolean canChildScrollUp() {

        if (onSearchScrollingChild != null) {
            mUpScrollTestView = onSearchScrollingChild.searchScrollChild();
        }

        boolean canViewScrollup = canViewScrollUp(mUpScrollTestView == null ? mTarget : mUpScrollTestView);

        if (auxiliaryUpScrollTester != null) {

            boolean additionalCanScrollUp = auxiliaryUpScrollTester.canScrollUp();
            //Log.d("pull", "canChildScrollUp返回值:" + (canViewScrollup || additionalCanScrollUp));
            return canViewScrollup || additionalCanScrollUp;
        } else {
            //Log.d("pull", "canChildScrollUp返回值:" + (canViewScrollup));
            return canViewScrollup;
        }
    }

    private boolean canViewScrollUp(View view) {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (view instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) view;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return view.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(view, -1);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        ensureTarget();
        if (mTarget == null)
            return;

        int height = getMeasuredHeight();
        int width = getMeasuredWidth();
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getPaddingRight();
        int bottom = getPaddingBottom();

        mTarget.layout(left, top + mTarget.getTop(), left + width - right, top + height - bottom + mTarget.getTop());
        mRefreshView.layout(left, top, left + width - right, top + height - bottom);
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getContext().getResources().getDisplayMetrics());
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public static interface OnRefreshListener {
        public void onRefresh();
    }



    // --------------------------------added by yimin-----------------------------------------

    public boolean isRefreshing() {
        return mRefreshing;
    }

    /**
     * 辅助上滑判定条件
     */
    public interface AuxiliaryUpScrollTester {
        boolean canScrollUp();
    }

    /**
     * 在每次滑动时，给出一次指定滚动主体的机会
     */
    public interface OnSearchScrollingChild {
        View searchScrollChild();
    }

    /**
     * 辅助的上滑判定条件
     * @param tester
     */
    public void setAuxiliaryUpScrollTester(AuxiliaryUpScrollTester tester) {
        auxiliaryUpScrollTester = tester;
    }

    /**
     * PullRefresh在每次需要判断是否能够上滑时(canScrollUp)，先调用searchScrollChild获取滚动主体，
     * 适用于滚动主体动态改变的情景，例如PullRefresh包了一个ViewPager，ViewPager包了N个recyclerView
     *
     * @param onSearchScrollingChild
     */
    public void setOnSearchScrollingChild(OnSearchScrollingChild onSearchScrollingChild) {
        this.onSearchScrollingChild = onSearchScrollingChild;
    }

    /**
     * Set the view to test for up scrolling availability, this is used
     * for cases where the scrolling view is not a direct child of pull refresh layout
     *
     * 适用于滚动主体不是直接child view，但是滚动主体不会动态改变的情况
     *
     * @param mUpScrollTestView the actual scrolling view
     */
    public void setUpScrollTestView(View mUpScrollTestView) {
        this.mUpScrollTestView = mUpScrollTestView;
    }

    // custom scrolling up checkers
    private View mUpScrollTestView;
    private AuxiliaryUpScrollTester auxiliaryUpScrollTester;
    private OnSearchScrollingChild onSearchScrollingChild;

}
