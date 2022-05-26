package org.atalk.xryptomail.activity;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.fragment.app.FragmentActivity;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.misc.SwipeGestureDetector;
import org.atalk.xryptomail.activity.misc.SwipeGestureDetector.OnSwipeGestureListener;

import java.util.Locale;

/**
 * This class implements functionality common to most activities used in K-9 Mail.
 *
 * @see XMActivity
 * @see XMListActivity
 */
public class XMActivityCommon
{
    /**
     * Creates a new instance of {@link XMActivityCommon} bound to the specified activity.
     *
     * @param activity The {@link Activity} the returned {@code XMActivityCommon} instance will be bound to.
     * @return The {@link XMActivityCommon} instance that will provide the base functionality of the
     * "XryptoMail" activities.
     */
    public static XMActivityCommon newInstance(Activity activity)
    {
        return new XMActivityCommon(activity);
    }

    public static void setLanguage(Context context, String language)
    {
        Locale locale;
        if (TextUtils.isEmpty(language)) {
            locale = Resources.getSystem().getConfiguration().locale;
        }
        else if (language.length() == 5 && language.charAt(2) == '_') {
            // language is in the form: en_US
            locale = new Locale(language.substring(0, 2), language.substring(3));
        }
        else {
            locale = new Locale(language);
        }

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.locale = locale;
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * Base activities need to implement this interface.
     *
     * <p>The implementing class simply has to call through to the implementation of these methods
     * in {@link XMActivityCommon}.</p>
     */
    public interface XMActivityMagic
    {
        void setupGestureDetector(OnSwipeGestureListener listener);
    }

    private final Activity mActivity;
    private GestureDetector mGestureDetector;

    private XMActivityCommon(Activity activity)
    {
        mActivity = activity;
        setLanguage(mActivity, XryptoMail.getXMLanguage());
        mActivity.setTheme(XryptoMail.getXMThemeResourceId());
    }

    /**
     * Call this before calling {@code super.dispatchTouchEvent(MotionEvent)}.
     */
    public void preDispatchTouchEvent(MotionEvent event)
    {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }
    }

    /**
     * Get the background color of the theme used for this activity.
     *
     * @return The background color of the current theme.
     */
    public int getThemeBackgroundColor()
    {
        TypedArray array = mActivity.getTheme().obtainStyledAttributes(new int[]{android.R.attr.colorBackground});

        int backgroundColor = array.getColor(0, 0xFF00FF);
        array.recycle();
        return backgroundColor;
    }

    /**
     * Call this if you wish to use the swipe gesture detector.
     *
     * @param listener A listener that will be notified if a left to right or right to left swipe has been detected.
     */
    public void setupGestureDetector(OnSwipeGestureListener listener)
    {
        mGestureDetector = new GestureDetector(mActivity, new SwipeGestureDetector(mActivity, listener));
    }
}
