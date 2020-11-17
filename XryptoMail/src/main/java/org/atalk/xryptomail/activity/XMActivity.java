package org.atalk.xryptomail.activity;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.fragment.app.FragmentActivity;

import org.atalk.xryptomail.activity.XMActivityCommon.XMActivityMagic;
import org.atalk.xryptomail.activity.misc.SwipeGestureDetector.OnSwipeGestureListener;

public abstract class XMActivity extends FragmentActivity implements XMActivityMagic
{
    private XMActivityCommon mBase;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        mBase = XMActivityCommon.newInstance(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event)
    {
        mBase.preDispatchTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void setupGestureDetector(OnSwipeGestureListener listener)
    {
        mBase.setupGestureDetector(listener);
    }
}
