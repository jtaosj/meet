package com.agmbat.pulltorefresh;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 支持各种控件的下拉和上拉的基类
 */
public abstract class PullToRefreshBase<T extends View> extends LinearLayout implements IPullToRefresh<T> {


    static final boolean DEBUG = true;

    static final boolean USE_HW_LAYERS = false;

    static final String LOG_TAG = "PullToRefresh";

    static final float FRICTION = 2.0f;

    public static final int SMOOTH_SCROLL_DURATION_MS = 200;
    public static final int SMOOTH_SCROLL_LONG_DURATION_MS = 325;
    static final int DEMO_SCROLL_INTERVAL = 225;

    static final String STATE_STATE = "ptr_state";
    static final String STATE_MODE = "ptr_mode";
    static final String STATE_CURRENT_MODE = "ptr_current_mode";
    static final String STATE_SCROLLING_REFRESHING_ENABLED = "ptr_disable_scrolling";
    static final String STATE_SHOW_REFRESHING_VIEW = "ptr_show_refreshing_view";
    static final String STATE_SUPER = "ptr_super";


    private int mTouchSlop;
    private float mLastMotionX;
    private float mLastMotionY;
    private float mInitialMotionX;
    private float mInitialMotionY;

    private boolean mIsBeingDragged = false;
    private State mState = State.RESET;
    private Mode mMode = Mode.getDefault();

    private Mode mCurrentMode;
    protected T mRefreshableView;
    private FrameLayout mRefreshableViewWrapper;

    private boolean mShowViewWhileRefreshing = true;
    private boolean mScrollingWhileRefreshingEnabled = false;
    private boolean mFilterTouchEvents = true;
    private boolean mOverScrollEnabled = true;
    private boolean mLayoutVisibilityChangesEnabled = true;

    private Interpolator mScrollAnimationInterpolator;
    private AnimationStyle mLoadingAnimationStyle = AnimationStyle.getDefault();

    private LoadingLayout mHeaderLayout;
    private LoadingLayout mFooterLayout;

    private OnRefreshListener<T> mOnRefreshListener;
    private OnPullEventListener<T> mOnPullEventListener;

    private SmoothScrollRunnable mCurrentSmoothScrollRunnable;


    public PullToRefreshBase(Context context) {
        super(context);
        init(context, null);
    }

    public PullToRefreshBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PullToRefreshBase(Context context, Mode mode) {
        super(context);
        mMode = mode;
        init(context, null);
    }

    public PullToRefreshBase(Context context, Mode mode, AnimationStyle animStyle) {
        super(context);
        mMode = mode;
        mLoadingAnimationStyle = animStyle;
        init(context, null);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addView: " + child.getClass().getSimpleName());
        }
        final T refreshableView = getRefreshableView();
        if (refreshableView instanceof ViewGroup) {
            ((ViewGroup) refreshableView).addView(child, index, params);
        } else {
            throw new UnsupportedOperationException("Refreshable View is not a ViewGroup so can't addView");
        }
    }

    @Override
    public final Mode getCurrentMode() {
        return mCurrentMode;
    }

    @Override
    public final boolean getFilterTouchEvents() {
        return mFilterTouchEvents;
    }

    @Override
    public final Mode getMode() {
        return mMode;
    }

    @Override
    public final T getRefreshableView() {
        return mRefreshableView;
    }

    @Override
    public final boolean getShowViewWhileRefreshing() {
        return mShowViewWhileRefreshing;
    }

    @Override
    public final State getState() {
        return mState;
    }

    @Override
    public final boolean isPullToRefreshEnabled() {
        return mMode.permitsPullToRefresh();
    }

    @Override
    public final boolean isPullToRefreshOverScrollEnabled() {
        return VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD && mOverScrollEnabled
                && OverScrollHelper.isAndroidOverScrollEnabled(mRefreshableView);
    }

    @Override
    public final boolean isRefreshing() {
        return mState == State.REFRESHING || mState == State.MANUAL_REFRESHING;
    }

    @Override
    public final boolean isScrollingWhileRefreshingEnabled() {
        return mScrollingWhileRefreshingEnabled;
    }

    @Override
    public final boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isPullToRefreshEnabled()) {
            return false;
        }
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mIsBeingDragged = false;
            return false;
        }
        if (action != MotionEvent.ACTION_DOWN && mIsBeingDragged) {
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                // If we're refreshing, and the flag is set. Eat all MOVE events
                if (!mScrollingWhileRefreshingEnabled && isRefreshing()) {
                    return true;
                }
                if (isReadyForPull()) {
                    final float y = event.getY();
                    final float x = event.getX();
                    final float diff;
                    final float oppositeDiff;
                    final float absDiff;

                    // We need to use the correct values, based on scroll
                    // direction
                    switch (getPullToRefreshScrollDirection()) {
                        case HORIZONTAL:
                            diff = x - mLastMotionX;
                            oppositeDiff = y - mLastMotionY;
                            break;
                        case VERTICAL:
                        default:
                            diff = y - mLastMotionY;
                            oppositeDiff = x - mLastMotionX;
                            break;
                    }
                    absDiff = Math.abs(diff);

                    if (absDiff > mTouchSlop && (!mFilterTouchEvents || absDiff > Math.abs(oppositeDiff))) {
                        if (mMode.showHeaderLoadingLayout() && diff >= 1f && isReadyForPullStart()) {
                            mLastMotionY = y;
                            mLastMotionX = x;
                            mIsBeingDragged = true;
                            if (mMode == Mode.BOTH) {
                                mCurrentMode = Mode.PULL_FROM_START;
                            }
                        } else if (mMode.showFooterLoadingLayout() && diff <= -1f && isReadyForPullEnd()) {
                            mLastMotionY = y;
                            mLastMotionX = x;
                            mIsBeingDragged = true;
                            if (mMode == Mode.BOTH) {
                                mCurrentMode = Mode.PULL_FROM_END;
                            }
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                if (isReadyForPull()) {
                    mLastMotionY = mInitialMotionY = event.getY();
                    mLastMotionX = mInitialMotionX = event.getX();
                    mIsBeingDragged = false;
                }
                break;
            }
            default:
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public final void onRefreshComplete() {
        if (isRefreshing()) {
            setState(State.REFRESH_COMPLETE);
        }
    }

    @Override
    public final boolean onTouchEvent(MotionEvent event) {
        if (!isPullToRefreshEnabled()) {
            return false;
        }
        // If we're refreshing, and the flag is set. Eat the event
        if (!mScrollingWhileRefreshingEnabled && isRefreshing()) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getEdgeFlags() != 0) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                if (mIsBeingDragged) {
                    mLastMotionY = event.getY();
                    mLastMotionX = event.getX();
                    pullEvent();
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                if (isReadyForPull()) {
                    mLastMotionY = mInitialMotionY = event.getY();
                    mLastMotionX = mInitialMotionX = event.getX();
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    if (mState == State.RELEASE_TO_REFRESH && (null != mOnRefreshListener)) {
                        setState(State.REFRESHING, true);
                        return true;
                    }
                    // If we're already refreshing, just scroll back to the top
                    if (isRefreshing()) {
                        smoothScrollTo(0);
                        return true;
                    }
                    // If we haven't returned by here, then we're not in a state
                    // to pull, so just reset
                    setState(State.RESET);
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    public final void setScrollingWhileRefreshingEnabled(boolean allowScrollingWhileRefreshing) {
        mScrollingWhileRefreshingEnabled = allowScrollingWhileRefreshing;
    }

    @Override
    public final void setFilterTouchEvents(boolean filterEvents) {
        mFilterTouchEvents = filterEvents;
    }

    @Override
    public void setLongClickable(boolean longClickable) {
        getRefreshableView().setLongClickable(longClickable);
    }

    @Override
    public final void setMode(Mode mode) {
        if (mode != mMode) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Setting mode to: " + mode);
            }
            mMode = mode;
            updateUIForMode();
        }
    }

    public void setOnPullEventListener(OnPullEventListener<T> listener) {
        mOnPullEventListener = listener;
    }

    @Override
    public final void setOnRefreshListener(OnRefreshListener<T> listener) {
        mOnRefreshListener = listener;
    }

    @Override
    public final void setPullToRefreshOverScrollEnabled(boolean enabled) {
        mOverScrollEnabled = enabled;
    }

    @Override
    public final void setRefreshing() {
        setRefreshing(true);
    }

    @Override
    public final void setRefreshing(boolean doScroll) {
        if (!isRefreshing()) {
            setState(State.MANUAL_REFRESHING, doScroll);
        }
    }

    public void setScrollAnimationInterpolator(Interpolator interpolator) {
        mScrollAnimationInterpolator = interpolator;
    }

    @Override
    public final void setShowViewWhileRefreshing(boolean showView) {
        mShowViewWhileRefreshing = showView;
    }

    /**
     * @return Either {@link Orientation#VERTICAL} or {@link Orientation#HORIZONTAL} depending on the scroll direction.
     */
    public abstract Orientation getPullToRefreshScrollDirection();

    final void setState(State state, final boolean... params) {
        mState = state;
        if (DEBUG) {
            Log.d(LOG_TAG, "State: " + mState.name());
        }
        switch (mState) {
            case RESET:
                onReset();
                break;
            case PULL_TO_REFRESH:
                onPullToRefresh();
                break;
            case RELEASE_TO_REFRESH:
                onReleaseToRefresh();
                break;
            case REFRESHING:
            case MANUAL_REFRESHING:
                onRefreshing(params[0]);
                break;
            case REFRESH_COMPLETE:
                onResetRefreshComplete();
                break;
            case OVERSCROLLING:
                // NO-OP
                break;
            default:
                break;
        }
        // Call OnPullEventListener
        if (null != mOnPullEventListener) {
            mOnPullEventListener.onPullEvent(this, mState, mCurrentMode);
        }
    }

    /**
     * Used internally for adding view. Need because we override addView to pass-through to the Refreshable View
     */
    protected final void addViewInternal(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
    }

    /**
     * Used internally for adding view. Need because we override addView to pass-through to the Refreshable View
     */
    protected final void addViewInternal(View child, ViewGroup.LayoutParams params) {
        super.addView(child, -1, params);
    }

    protected LoadingLayout createLoadingLayout(Context context, Mode mode) {
        LoadingLayout layout =
                mLoadingAnimationStyle.createLoadingLayout(context, mode, getPullToRefreshScrollDirection());
        layout.setVisibility(View.INVISIBLE);
        return layout;
    }

    /**
     * This is implemented by derived classes to return the created View. If you need to use a custom View (such as a
     * custom ListView), override this method and return an instance of your custom class.
     * <p/>
     * Be sure to set the ID of the view in this method, especially if you're using a ListActivity or ListFragment.
     *
     * @param context Context to create view with
     * @param attrs   AttributeSet from wrapped class. Means that anything you include in the XML layout declaration will
     *                be routed to the created View
     * @return New instance of the Refreshable View
     */
    protected abstract T createRefreshableView(Context context, AttributeSet attrs);

    protected final void disableLoadingLayoutVisibilityChanges() {
        mLayoutVisibilityChangesEnabled = false;
    }

    public final LoadingLayout getFooterLayout() {
        return mFooterLayout;
    }

    protected final int getFooterSize() {
        return mFooterLayout.getContentSize();
    }

    public final LoadingLayout getHeaderLayout() {
        return mHeaderLayout;
    }

    protected final int getHeaderSize() {
        return mHeaderLayout.getContentSize();
    }

    protected int getPullToRefreshScrollDuration() {
        return SMOOTH_SCROLL_DURATION_MS;
    }

    protected int getPullToRefreshScrollDurationLonger() {
        return SMOOTH_SCROLL_LONG_DURATION_MS;
    }

    protected FrameLayout getRefreshableViewWrapper() {
        return mRefreshableViewWrapper;
    }

    /**
     * Allows Derivative classes to handle the XML Attrs without creating a TypedArray themsevles
     *
     * @param a - TypedArray of PullToRefresh Attributes
     */
    protected void handleStyledAttributes() {
    }

    /**
     * Implemented by derived class to return whether the View is in a state where the user can Pull to Refresh by
     * scrolling from the end.
     *
     * @return true if the View is currently in the correct state (for example, bottom of a ListView)
     */
    protected abstract boolean isReadyForPullEnd();

    /**
     * Implemented by derived class to return whether the View is in a state where the user can Pull to Refresh by
     * scrolling from the start.
     *
     * @return true if the View is currently the correct state (for example, top of a ListView)
     */
    protected abstract boolean isReadyForPullStart();

    /**
     * Called by {@link #onRestoreInstanceState(Parcelable)} so that derivative classes can handle their saved instance
     * state.
     *
     * @param savedInstanceState - Bundle which contains saved instance state.
     */
    protected void onPtrRestoreInstanceState(Bundle savedInstanceState) {
    }

    /**
     * Called by {@link #onSaveInstanceState()} so that derivative classes can save their instance state.
     *
     * @param saveState - Bundle to be updated with saved state.
     */
    protected void onPtrSaveInstanceState(Bundle saveState) {
    }

    /**
     * Called when the UI has been to be updated to be in the {@link State#PULL_TO_REFRESH} state.
     */
    protected void onPullToRefresh() {
        switch (mCurrentMode) {
            case PULL_FROM_END:
                mFooterLayout.pullToRefresh();
                break;
            case PULL_FROM_START:
                mHeaderLayout.pullToRefresh();
                break;
            default:
                // NO-OP
                break;
        }
    }

    /**
     * Called when the UI has been to be updated to be in the {@link State#REFRESHING} or
     * {@link State#MANUAL_REFRESHING} state.
     *
     * @param doScroll - Whether the UI should scroll for this event.
     */
    protected void onRefreshing(final boolean doScroll) {
        if (mMode.showHeaderLoadingLayout()) {
            mHeaderLayout.refreshing();
        }
        if (mMode.showFooterLoadingLayout()) {
            mFooterLayout.refreshing();
        }

        if (doScroll) {
            if (mShowViewWhileRefreshing) {

                // Call Refresh Listener when the Scroll has finished
                OnSmoothScrollFinishedListener listener = new OnSmoothScrollFinishedListener() {
                    @Override
                    public void onSmoothScrollFinished() {
                        callRefreshListener();
                    }
                };

                switch (mCurrentMode) {
                    case MANUAL_REFRESH_ONLY:
                    case PULL_FROM_END:
                        smoothScrollTo(getFooterSize(), listener);
                        break;
                    default:
                    case PULL_FROM_START:
                        smoothScrollTo(-getHeaderSize(), listener);
                        break;
                }
            } else {
                smoothScrollTo(0);
            }
        } else {
            // We're not scrolling, so just call Refresh Listener now
            callRefreshListener();
        }
    }

    /**
     * Called when the UI has been to be updated to be in the {@link State#RELEASE_TO_REFRESH} state.
     */
    protected void onReleaseToRefresh() {
        switch (mCurrentMode) {
            case PULL_FROM_END:
                mFooterLayout.releaseToRefresh();
                break;
            case PULL_FROM_START:
                mHeaderLayout.releaseToRefresh();
                break;
            default:
                // NO-OP
                break;
        }
    }

    /**
     * Called when the UI has been to be updated to be in the {@link State#RESET} state.
     */
    protected void onReset() {
        mIsBeingDragged = false;
        mLayoutVisibilityChangesEnabled = true;
        // Always reset both layouts, just in case...
        mHeaderLayout.reset();
        mFooterLayout.reset();
        smoothScrollTo(0);
    }

    /**
     * Called when the UI has been to be updated to be in the {@link State#REFRESH_COMPLETE} state.
     */
    protected void onResetRefreshComplete() {
        mIsBeingDragged = false;
        mLayoutVisibilityChangesEnabled = true;
        // Always reset both layouts, just in case...
        mHeaderLayout.resetRefreshComplete();
        mFooterLayout.resetRefreshComplete();
        smoothScrollToDelay(0);
    }

    @Override
    protected final void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            setMode(Mode.mapIntToValue(bundle.getInt(STATE_MODE, 0)));
            mCurrentMode = Mode.mapIntToValue(bundle.getInt(STATE_CURRENT_MODE, 0));
            mScrollingWhileRefreshingEnabled = bundle.getBoolean(STATE_SCROLLING_REFRESHING_ENABLED, false);
            mShowViewWhileRefreshing = bundle.getBoolean(STATE_SHOW_REFRESHING_VIEW, true);
            // Let super Restore Itself
            super.onRestoreInstanceState(bundle.getParcelable(STATE_SUPER));
            State viewState = State.mapIntToValue(bundle.getInt(STATE_STATE, 0));
            if (viewState == State.REFRESHING || viewState == State.MANUAL_REFRESHING) {
                setState(viewState, true);
            }
            // Now let derivative classes restore their state
            onPtrRestoreInstanceState(bundle);
            return;
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    protected final Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        // Let derivative classes get a chance to save state first, that way we
        // can make sure they don't overrite any of our values
        onPtrSaveInstanceState(bundle);
        bundle.putInt(STATE_STATE, mState.getIntValue());
        bundle.putInt(STATE_MODE, mMode.getIntValue());
        bundle.putInt(STATE_CURRENT_MODE, mCurrentMode.getIntValue());
        bundle.putBoolean(STATE_SCROLLING_REFRESHING_ENABLED, mScrollingWhileRefreshingEnabled);
        bundle.putBoolean(STATE_SHOW_REFRESHING_VIEW, mShowViewWhileRefreshing);
        bundle.putParcelable(STATE_SUPER, super.onSaveInstanceState());
        return bundle;
    }

    @Override
    protected final void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("onSizeChanged. W: %d, H: %d", w, h));
        }
        super.onSizeChanged(w, h, oldw, oldh);
        // We need to update the header/footer when our size changes
        refreshLoadingViewsSize();
        // Update the Refreshable View layout
        refreshRefreshableViewSize(w, h);
        /**
         * As we're currently in a Layout Pass, we need to schedule another one to layout any changes we've made here
         */
        post(new Runnable() {
            @Override
            public void run() {
                requestLayout();
            }
        });
    }

    /**
     * Re-measure the Loading Views height, and adjust internal padding as necessary
     */
    protected final void refreshLoadingViewsSize() {
        final int maximumPullScroll = (int) (getMaximumPullScroll() * 1.2f);
        int pLeft = getPaddingLeft();
        int pTop = getPaddingTop();
        int pRight = getPaddingRight();
        int pBottom = getPaddingBottom();
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                if (mMode.showHeaderLoadingLayout()) {
                    mHeaderLayout.setWidth(maximumPullScroll);
                    pLeft = -maximumPullScroll;
                } else {
                    pLeft = 0;
                }
                if (mMode.showFooterLoadingLayout()) {
                    mFooterLayout.setWidth(maximumPullScroll);
                    pRight = -maximumPullScroll;
                } else {
                    pRight = 0;
                }
                break;
            case VERTICAL:
                if (mMode.showHeaderLoadingLayout()) {
                    mHeaderLayout.setHeight(maximumPullScroll);
                    pTop = -maximumPullScroll;
                } else {
                    pTop = 0;
                }
                if (mMode.showFooterLoadingLayout()) {
                    mFooterLayout.setHeight(maximumPullScroll);
                    pBottom = -maximumPullScroll;
                } else {
                    pBottom = 0;
                }
                break;
            default:
                break;
        }
        if (DEBUG) {
            Log.d(LOG_TAG, String.format("Setting Padding. L: %d, T: %d, R: %d, B: %d", pLeft, pTop, pRight, pBottom));
        }
        setPadding(pLeft, pTop, pRight, pBottom);
    }

    protected final void refreshRefreshableViewSize(int width, int height) {
        // We need to set the Height of the Refreshable View to the same as
        // this layout
        LayoutParams lp = (LayoutParams) mRefreshableViewWrapper.getLayoutParams();
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                if (lp.width != width) {
                    lp.width = width;
                    mRefreshableViewWrapper.requestLayout();
                }
                break;
            case VERTICAL:
                if (lp.height != height) {
                    lp.height = height;
                    mRefreshableViewWrapper.requestLayout();
                }
                break;
            default:
                break;
        }
    }

    /**
     * Helper method which just calls scrollTo() in the correct scrolling direction.
     *
     * @param value - New Scroll value
     */
    protected final void setHeaderScroll(int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setHeaderScroll: " + value);
        }
        // Clamp value to with pull scroll range
        final int maximumPullScroll = getMaximumPullScroll();
        value = Math.min(maximumPullScroll, Math.max(-maximumPullScroll, value));
        if (mLayoutVisibilityChangesEnabled) {
            if (value < 0) {
                mHeaderLayout.setVisibility(View.VISIBLE);
            } else if (value > 0) {
                mFooterLayout.setVisibility(View.VISIBLE);
            } else {
                mHeaderLayout.setVisibility(View.INVISIBLE);
                mFooterLayout.setVisibility(View.INVISIBLE);
            }
        }
        if (USE_HW_LAYERS) {
            /**
             * Use a Hardware Layer on the Refreshable View if we've scrolled at all. We don't use them on the
             * Header/Footer Views as they change often, which would negate any HW layer performance boost.
             */
            int layerType = value != 0 ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
            ViewCompat.setLayerType(mRefreshableViewWrapper, layerType);
        }
        switch (getPullToRefreshScrollDirection()) {
            case VERTICAL:
                scrollTo(0, value);
                break;
            case HORIZONTAL:
                scrollTo(value, 0);
                break;
            default:
                break;
        }
    }

    /**
     * Updates the View State when the mode has been set. This does not do any checking that the mode is different to
     * current state so always updates.
     */
    protected void updateUIForMode() {
        // We need to use the correct LayoutParam values, based on scroll
        // direction
        final LayoutParams lp = getLoadingLayoutLayoutParams();

        // Remove Header, and then add Header Loading View again if needed
        if (this == mHeaderLayout.getParent()) {
            removeView(mHeaderLayout);
        }
        if (mMode.showHeaderLoadingLayout()) {
            addViewInternal(mHeaderLayout, 0, lp);
        }

        // Remove Footer, and then add Footer Loading View again if needed
        if (this == mFooterLayout.getParent()) {
            removeView(mFooterLayout);
        }
        if (mMode.showFooterLoadingLayout()) {
            addViewInternal(mFooterLayout, lp);
        }

        // Hide Loading Views
        refreshLoadingViewsSize();

        // If we're not using Mode.BOTH, set mCurrentMode to mMode, otherwise
        // set it to pull down
        mCurrentMode = (mMode != Mode.BOTH) ? mMode : Mode.PULL_FROM_START;
    }

    private void addRefreshableView(Context context, T refreshableView) {
        mRefreshableViewWrapper = new FrameLayout(context);
        mRefreshableViewWrapper.addView(refreshableView, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        addViewInternal(mRefreshableViewWrapper, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    private void callRefreshListener() {
        if (null != mOnRefreshListener) {
            if (mCurrentMode == Mode.PULL_FROM_START) {
                mOnRefreshListener.onPullStartToRefresh(this);
            } else if (mCurrentMode == Mode.PULL_FROM_END) {
                mOnRefreshListener.onPullEndToRefresh(this);
            }
        }
    }

    private void init(Context context, AttributeSet attrs) {
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                setOrientation(LinearLayout.HORIZONTAL);
                break;
            case VERTICAL:
            default:
                setOrientation(LinearLayout.VERTICAL);
                break;
        }

        setGravity(Gravity.CENTER);

        ViewConfiguration config = ViewConfiguration.get(context);
        mTouchSlop = config.getScaledTouchSlop();

        // Refreshable View
        // By passing the attrs, we can add ListView/GridView params via XML
        mRefreshableView = createRefreshableView(context, attrs);
        addRefreshableView(context, mRefreshableView);

        // We need to create now layouts now
        mHeaderLayout = createLoadingLayout(context, Mode.PULL_FROM_START);
        mFooterLayout = createLoadingLayout(context, Mode.PULL_FROM_END);

        handleStyledAttributes();

        // Finally update the UI for the modes
        updateUIForMode();
    }

    private boolean isReadyForPull() {
        switch (mMode) {
            case PULL_FROM_START:
                return isReadyForPullStart();
            case PULL_FROM_END:
                return isReadyForPullEnd();
            case BOTH:
                return isReadyForPullEnd() || isReadyForPullStart();
            default:
                return false;
        }
    }

    /**
     * Actions a Pull Event
     *
     * @return true if the Event has been handled, false if there has been no change
     */
    private void pullEvent() {
        final int newScrollValue;
        final int itemDimension;
        final float initialMotionValue;
        final float lastMotionValue;

        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                initialMotionValue = mInitialMotionX;
                lastMotionValue = mLastMotionX;
                break;
            case VERTICAL:
            default:
                initialMotionValue = mInitialMotionY;
                lastMotionValue = mLastMotionY;
                break;
        }

        switch (mCurrentMode) {
            case PULL_FROM_END:
                newScrollValue = Math.round(Math.max(initialMotionValue - lastMotionValue, 0) / FRICTION);
                itemDimension = getFooterSize();
                break;
            case PULL_FROM_START:
            default:
                newScrollValue = Math.round(Math.min(initialMotionValue - lastMotionValue, 0) / FRICTION);
                itemDimension = getHeaderSize();
                break;
        }

        setHeaderScroll(newScrollValue);

        if (newScrollValue != 0 && !isRefreshing()) {
            float scale = Math.abs(newScrollValue) / (float) itemDimension;
            switch (mCurrentMode) {
                case PULL_FROM_END:
                    mFooterLayout.onPull(scale);
                    break;
                case PULL_FROM_START:
                default:
                    mHeaderLayout.onPull(scale);
                    break;
            }

            if (mState != State.PULL_TO_REFRESH && itemDimension >= Math.abs(newScrollValue)) {
                setState(State.PULL_TO_REFRESH);
            } else if (mState == State.PULL_TO_REFRESH && itemDimension < Math.abs(newScrollValue)) {
                setState(State.RELEASE_TO_REFRESH);
            }
        }
    }

    private LayoutParams getLoadingLayoutLayoutParams() {
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                return new LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT);
            case VERTICAL:
            default:
                return new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT);
        }
    }

    private int getMaximumPullScroll() {
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                return Math.round(getWidth() / FRICTION);
            case VERTICAL:
            default:
                return Math.round(getHeight() / FRICTION);
        }
    }

    /**
     * Smooth Scroll to position using the delay 300 ms.
     *
     * @param scrollValue - Position to scroll to
     */
    protected final void smoothScrollToDelay(int scrollValue) {
        smoothScrollTo(scrollValue, getPullToRefreshScrollDuration(), 300, null);
    }

    /**
     * Smooth Scroll to position using the longer default duration of {@value #SMOOTH_SCROLL_LONG_DURATION_MS} ms.
     *
     * @param scrollValue - Position to scroll to
     */
    protected final void smoothScrollToLonger(int scrollValue) {
        smoothScrollTo(scrollValue, getPullToRefreshScrollDurationLonger());
    }

    /**
     * Smooth Scroll to position using the default duration of {@value #SMOOTH_SCROLL_DURATION_MS} ms.
     *
     * @param scrollValue - Position to scroll to
     */
    protected final void smoothScrollTo(int scrollValue) {
        smoothScrollTo(scrollValue, getPullToRefreshScrollDuration());
    }

    /**
     * Smooth Scroll to position using the default duration of {@value #SMOOTH_SCROLL_DURATION_MS} ms.
     *
     * @param scrollValue - Position to scroll to
     * @param listener    - Listener for scroll
     */
    protected final void smoothScrollTo(int scrollValue, OnSmoothScrollFinishedListener listener) {
        smoothScrollTo(scrollValue, getPullToRefreshScrollDuration(), 0, listener);
    }

    /**
     * Smooth Scroll to position using the specific duration
     *
     * @param scrollValue - Position to scroll to
     * @param duration    - Duration of animation in milliseconds
     */
    private final void smoothScrollTo(int scrollValue, long duration) {
        smoothScrollTo(scrollValue, duration, 0, null);
    }

    private final void smoothScrollTo(int newScrollValue, long duration, long delayMillis,
                                      OnSmoothScrollFinishedListener listener) {
        if (null != mCurrentSmoothScrollRunnable) {
            mCurrentSmoothScrollRunnable.stop();
        }

        final int oldScrollValue;
        switch (getPullToRefreshScrollDirection()) {
            case HORIZONTAL:
                oldScrollValue = getScrollX();
                break;
            case VERTICAL:
            default:
                oldScrollValue = getScrollY();
                break;
        }

        if (oldScrollValue != newScrollValue) {
            if (null == mScrollAnimationInterpolator) {
                // Default interpolator is a Decelerate Interpolator
                mScrollAnimationInterpolator = new DecelerateInterpolator();
            }
            mCurrentSmoothScrollRunnable = new SmoothScrollRunnable(oldScrollValue, newScrollValue, duration, listener);

            if (delayMillis > 0) {
                postDelayed(mCurrentSmoothScrollRunnable, delayMillis);
            } else {
                post(mCurrentSmoothScrollRunnable);
            }
        }
    }

    private final void smoothScrollToAndBack(int y) {
        smoothScrollTo(y, SMOOTH_SCROLL_DURATION_MS, 0, new OnSmoothScrollFinishedListener() {

            @Override
            public void onSmoothScrollFinished() {
                smoothScrollTo(0, SMOOTH_SCROLL_DURATION_MS, DEMO_SCROLL_INTERVAL, null);
            }
        });
    }

    public static enum AnimationStyle {
        /**
         * This is the default for Android-PullToRefresh. Allows you to use any drawable, which is automatically rotated
         * and used as a Progress Bar.
         */
        ROTATE,

        /**
         * This is the old default, and what is commonly used on iOS. Uses an arrow image which flips depending on where
         * the user has scrolled.
         */
        FLIP;

        static AnimationStyle getDefault() {
            return ROTATE;
        }

        /**
         * Maps an int to a specific mode. This is needed when saving state, or inflating the view from XML where the
         * mode is given through a attr int.
         *
         * @param modeInt - int to map a Mode to
         * @return Mode that modeInt maps to, or ROTATE by default.
         */
        static AnimationStyle mapIntToValue(int modeInt) {
            switch (modeInt) {
                case 0x0:
                default:
                    return ROTATE;
                case 0x1:
                    return FLIP;
            }
        }

        LoadingLayout createLoadingLayout(Context context, Mode mode, Orientation scrollDirection) {
            switch (this) {
                case ROTATE:
                default:
                    return new RotateLoadingLayout(context, mode, scrollDirection);
                case FLIP:
                    return new FlipLoadingLayout(context, mode, scrollDirection);
            }
        }
    }

    public static enum Mode {

        /**
         * Disable all Pull-to-Refresh gesture and Refreshing handling
         */
        DISABLED(0x0),

        /**
         * Only allow the user to Pull from the start of the Refreshable View to refresh. The start is either the Top or
         * Left, depending on the scrolling direction.
         */
        PULL_FROM_START(0x1),

        /**
         * Only allow the user to Pull from the end of the Refreshable View to refresh. The start is either the Bottom
         * or Right, depending on the scrolling direction.
         */
        PULL_FROM_END(0x2),

        /**
         * Allow the user to both Pull from the start, from the end to refresh.
         */
        BOTH(0x3),

        /**
         * Disables Pull-to-Refresh gesture handling, but allows manually setting the Refresh state via
         * {@link PullToRefreshBase#setRefreshing() setRefreshing()}.
         */
        MANUAL_REFRESH_ONLY(0x4);

        /**
         * Maps an int to a specific mode. This is needed when saving state, or inflating the view from XML where the
         * mode is given through a attr int.
         *
         * @param modeInt - int to map a Mode to
         * @return Mode that modeInt maps to, or PULL_FROM_START by default.
         */
        public static Mode mapIntToValue(final int modeInt) {
            for (Mode value : Mode.values()) {
                if (modeInt == value.getIntValue()) {
                    return value;
                }
            }

            // If not, return default
            return getDefault();
        }

        public static Mode getDefault() {
            return PULL_FROM_START;
        }

        private int mIntValue;

        // The modeInt values need to match those from attrs.xml
        Mode(int modeInt) {
            mIntValue = modeInt;
        }

        /**
         * @return true if the mode permits Pull-to-Refresh
         */
        public boolean permitsPullToRefresh() {
            return !(this == DISABLED || this == MANUAL_REFRESH_ONLY);
        }

        /**
         * @return true if this mode wants the Loading Layout Header to be shown
         */
        public boolean showHeaderLoadingLayout() {
            return this == PULL_FROM_START || this == BOTH;
        }

        /**
         * @return true if this mode wants the Loading Layout Footer to be shown
         */
        public boolean showFooterLoadingLayout() {
            return this == PULL_FROM_END || this == BOTH || this == MANUAL_REFRESH_ONLY;
        }

        public int getIntValue() {
            return mIntValue;
        }

    }

    /**
     * Simple Listener that allows you to be notified when the user has scrolled to the end of the AdapterView. See (
     * {@link PullToRefreshAdapterViewBase#setOnLastItemVisibleListener}.
     */
    public static interface OnLastItemVisibleListener {

        /**
         * Called when the user has scrolled to the end of the list
         */
        public void onLastItemVisible();

    }

    /**
     * Listener that allows you to be notified when the user has started or finished a touch event. Useful when you want
     * to append extra UI events (such as sounds). See ( {@link PullToRefreshAdapterViewBase#setOnPullEventListener}.
     */
    public static interface OnPullEventListener<V extends View> {

        /**
         * Called when the internal state has been changed, usually by the user pulling.
         *
         * @param refreshView - View which has had it's state change.
         * @param state       - The new state of View.
         * @param direction   - One of {@link Mode#PULL_FROM_START} or {@link Mode#PULL_FROM_END} depending on which
         *                    direction the user is pulling. Only useful when <var>state</var> is {@link State#PULL_TO_REFRESH}
         *                    or {@link State#RELEASE_TO_REFRESH}.
         */
        public void onPullEvent(final PullToRefreshBase<V> refreshView, State state, Mode direction);

    }

    public static interface OnRefreshListener<V extends View> {
        /**
         * onPullDownToRefresh will be called only when the user has Pulled from the start, and released.
         */
        public void onPullStartToRefresh(final PullToRefreshBase<V> refreshView);

        /**
         * onPullUpToRefresh will be called only when the user has Pulled from the end, and released.
         */
        public void onPullEndToRefresh(final PullToRefreshBase<V> refreshView);

    }

    public static enum Orientation {
        VERTICAL, HORIZONTAL;
    }

    public static enum State {

        /**
         * When the UI is in a state which means that user is not interacting with the Pull-to-Refresh function.
         */
        RESET(0x0),

        /**
         * When the UI is being pulled by the user, but has not been pulled far enough so that it refreshes when
         * released.
         */
        PULL_TO_REFRESH(0x1),

        /**
         * When the UI is being pulled by the user, and <strong>has</strong> been pulled far enough so that it will
         * refresh when released.
         */
        RELEASE_TO_REFRESH(0x2),

        /**
         * When the UI is currently refreshing, caused by a pull gesture.
         */
        REFRESHING(0x8),

        /**
         * When the UI is currently refreshing, caused by a call to {@link PullToRefreshBase#setRefreshing()
         * setRefreshing()}.
         */
        MANUAL_REFRESHING(0x9),

        /**
         * When the UI is currently overscrolling, caused by a fling on the Refreshable View.
         */
        OVERSCROLLING(0x10),

        /**
         * When the UI is current refresh complete.
         */
        REFRESH_COMPLETE(0x11);

        /**
         * Maps an int to a specific state. This is needed when saving state.
         *
         * @param stateInt - int to map a State to
         * @return State that stateInt maps to
         */
        public static State mapIntToValue(final int stateInt) {
            for (State value : State.values()) {
                if (stateInt == value.getIntValue()) {
                    return value;
                }
            }

            // If not, return default
            return RESET;
        }

        private int mIntValue;

        State(int intValue) {
            mIntValue = intValue;
        }

        public int getIntValue() {
            return mIntValue;
        }
    }

    final class SmoothScrollRunnable implements Runnable {
        private final Interpolator mInterpolator;
        private final int mScrollToY;
        private final int mScrollFromY;
        private final long mDuration;
        private OnSmoothScrollFinishedListener mListener;

        private boolean mContinueRunning = true;
        private long mStartTime = -1;
        private int mCurrentY = -1;

        public SmoothScrollRunnable(int fromY, int toY, long duration, OnSmoothScrollFinishedListener listener) {
            mScrollFromY = fromY;
            mScrollToY = toY;
            mInterpolator = mScrollAnimationInterpolator;
            mDuration = duration;
            mListener = listener;
        }

        @Override
        public void run() {

            /**
             * Only set mStartTime if this is the first time we're starting, else actually calculate the Y delta
             */
            if (mStartTime == -1) {
                mStartTime = System.currentTimeMillis();
            } else {

                /**
                 * We do do all calculations in long to reduce software float calculations. We use 1000 as it gives us
                 * good accuracy and small rounding errors
                 */
                long normalizedTime = (1000 * (System.currentTimeMillis() - mStartTime)) / mDuration;
                normalizedTime = Math.max(Math.min(normalizedTime, 1000), 0);
                float value = (mScrollFromY - mScrollToY) * mInterpolator.getInterpolation(normalizedTime / 1000f);
                final int deltaY = Math.round(value);
                mCurrentY = mScrollFromY - deltaY;
                setHeaderScroll(mCurrentY);
            }

            // If we're not at the target Y, keep going...
            if (mContinueRunning && mScrollToY != mCurrentY) {
                ViewCompat.postOnAnimation(PullToRefreshBase.this, this);
            } else {
                if (null != mListener) {
                    mListener.onSmoothScrollFinished();
                }
            }
        }

        public void stop() {
            mContinueRunning = false;
            removeCallbacks(this);
        }
    }

    static interface OnSmoothScrollFinishedListener {
        void onSmoothScrollFinished();
    }

}