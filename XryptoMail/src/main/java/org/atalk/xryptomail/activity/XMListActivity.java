package org.atalk.xryptomail.activity;

//import android.app.ListActivity;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.activity.XMActivityCommon.XMActivityMagic;
import org.atalk.xryptomail.activity.misc.SwipeGestureDetector.OnSwipeGestureListener;

public abstract class XMListActivity extends ListActivity implements XMActivityMagic {
    private XMActivityCommon mBase;
    protected ListAdapter adapter;
    protected ListView list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mBase = XMActivityCommon.newInstance(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mBase.preDispatchTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public Activity getActivity() {
        return mBase.mActivity;
    }

    @Override
    public void setupGestureDetector(OnSwipeGestureListener listener) {
        mBase.setupGestureDetector(listener);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Shortcuts that work no matter what is selected
        if (XryptoMail.useVolumeKeysForListNavigationEnabled()
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {

            final ListView listView = getListView();
            int currentPosition = listView.getSelectedItemPosition();
            if (currentPosition == AdapterView.INVALID_POSITION || listView.isInTouchMode()) {
                currentPosition = listView.getFirstVisiblePosition();
            }

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && currentPosition > 0) {
                listView.setSelection(currentPosition - 1);
            }
            else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    && currentPosition < listView.getCount()) {
                listView.setSelection(currentPosition + 1);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Swallow these events too to avoid the audible notification of a volume change
        if (XryptoMail.useVolumeKeysForListNavigationEnabled()
                && ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

}
