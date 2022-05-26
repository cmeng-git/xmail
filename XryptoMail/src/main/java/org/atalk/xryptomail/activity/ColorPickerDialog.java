/* Sourced from http://code.google.com/p/android-color-picker/source/browse/trunk/AmbilWarna/src/yuku/ambilwarna/AmbilWarnaDialog.java?r=1
 * On 2010-11-07
 * Translated to English, Ported to use the same (inferior) API as the more standard "ColorPickerDialog" and imported into the K-9 namespace by Jesse Vincent
 * In an ideal world, we should move to using AmbilWarna as an Android Library Project in the future
 * License: Apache 2.0
 * Author: yukuku@code.google.com
 */

package org.atalk.xryptomail.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.ImageView;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.view.ColorPicker;

import timber.log.Timber;

/**
 * Dialog displaying a color picker.
 */
public class ColorPickerDialog extends AlertDialog {
    private static final String BUNDLE_KEY_PARENT_BUNDLE = "parent";
    private static final String BUNDLE_KEY_COLOR_OLD = "color_old";
    private static final String BUNDLE_KEY_COLOR_NEW = "color_new";

    /**
     * The interface users of {@link ColorPickerDialog} have to implement to learn the selected
     * color.
     */
    public interface OnColorChangedListener {
        /**
         * This is called after the user pressed the "OK" button of the dialog.
         *
         * @param color The ARGB value of the selected color.
         */
        void colorChanged(int color);
    }

    OnColorChangedListener mColorChangedListener;
    ColorPicker mColorPicker;
    ImageView arrow;
    ImageView viewSpyglass;
    View viewHue;
    View viewColorOld;
    View viewColorNew;
    int colorOld;
    int colorNew;
    float onedp;
    float hue;
    float sat;
    float val;
    float sizeUiDp = 240.f;
    float sizeUiPx; // diset di constructor

    public ColorPickerDialog(Context context, OnColorChangedListener listener, int color) {
        super(context);
        mColorChangedListener = listener;
        initColor(color);

        onedp = context.getResources().getDimension(R.dimen.colorpicker_onedp);
        sizeUiPx = sizeUiDp * onedp;
        Timber.d("onedp = %s, sizeUiPx = %s", onedp, sizeUiPx);  //$NON-NLS-1$//$NON-NLS-2$

        View view = LayoutInflater.from(context).inflate(R.layout.colorpicker_dialog, null);
        viewHue = view.findViewById(R.id.colorpicker_viewHue);
        mColorPicker = view.findViewById(R.id.colorpicker_viewBox);
        arrow = view.findViewById(R.id.colorpicker_arrow);
        viewColorOld = view.findViewById(R.id.colorpicker_colorOld);
        viewColorNew = view.findViewById(R.id.colorpicker_colorNew);
        viewSpyglass = view.findViewById(R.id.colorpicker_spyglass);

        updateView();
        viewHue.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_UP) {

                float y = event.getY(); // dalam px, bukan dp
                if (y < 0.f)
                    y = 0.f;
                if (y > sizeUiPx)
                    y = sizeUiPx - 0.001f;

                hue = 360.f - 360.f / sizeUiPx * y;
                if (hue == 360.f)
                    hue = 0.f;

                colorNew = calculateColor();
                // update view
                mColorPicker.setHue(hue);
                placeArrow();
                viewColorNew.setBackgroundColor(colorNew);
                return true;
            }
            return false;
        });

        mColorPicker.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_UP) {

                float x = event.getX(); // dalam px, bukan dp
                float y = event.getY(); // dalam px, bukan dp

                if (x < 0.f)
                    x = 0.f;
                if (x > sizeUiPx)
                    x = sizeUiPx;
                if (y < 0.f)
                    y = 0.f;
                if (y > sizeUiPx)
                    y = sizeUiPx;

                sat = (1.f / sizeUiPx * x);
                val = 1.f - (1.f / sizeUiPx * y);

                colorNew = calculateColor();
                // update view
                placeSpyglass();
                viewColorNew.setBackgroundColor(colorNew);
                return true;
            }
            return false;
        });

        this.setView(view);
        this.setButton(BUTTON_POSITIVE, context.getString(R.string.okay_action), (dialog, which) -> {
            if (mColorChangedListener != null) {
                mColorChangedListener.colorChanged(colorNew);
            }
        });
        this.setButton(BUTTON_NEGATIVE, context.getString(R.string.cancel_action), (OnClickListener) null);
    }

    private void updateView() {
        placeArrow();
        placeSpyglass();
        mColorPicker.setHue(hue);
        viewColorOld.setBackgroundColor(colorOld);
        viewColorNew.setBackgroundColor(colorNew);
    }

    private void initColor(int color) {
        colorNew = color;
        colorOld = color;

        Color.colorToHSV(color, tmp01);
        hue = tmp01[0];
        sat = tmp01[1];
        val = tmp01[2];
    }

    public void setColor(int color) {
        initColor(color);
        updateView();
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle parentBundle = super.onSaveInstanceState();

        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putBundle(BUNDLE_KEY_PARENT_BUNDLE, parentBundle);
        savedInstanceState.putInt(BUNDLE_KEY_COLOR_OLD, colorOld);
        savedInstanceState.putInt(BUNDLE_KEY_COLOR_NEW, colorNew);
        return savedInstanceState;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        Bundle parentBundle = savedInstanceState.getBundle(BUNDLE_KEY_PARENT_BUNDLE);
        super.onRestoreInstanceState(parentBundle);

        int color = savedInstanceState.getInt(BUNDLE_KEY_COLOR_NEW);
        // Sets colorOld, colorNew to color and initializes hue, sat, val from color
        initColor(color);

        // Now restore the real colorOld value
        colorOld = savedInstanceState.getInt(BUNDLE_KEY_COLOR_OLD);
        updateView();
    }

    @SuppressWarnings("deprecation")
    protected void placeArrow() {
        float y = sizeUiPx - (hue * sizeUiPx / 360.f);
        if (y == sizeUiPx)
            y = 0.f;

        AbsoluteLayout.LayoutParams layoutParams = (AbsoluteLayout.LayoutParams) arrow.getLayoutParams();
        layoutParams.y = (int) (y + 4);
        arrow.setLayoutParams(layoutParams);
    }

    @SuppressWarnings("deprecation")
    protected void placeSpyglass() {
        float x = sat * sizeUiPx;
        float y = (1.f - val) * sizeUiPx;

        AbsoluteLayout.LayoutParams layoutParams = (AbsoluteLayout.LayoutParams) viewSpyglass.getLayoutParams();
        layoutParams.x = (int) (x + 3);
        layoutParams.y = (int) (y + 3);
        viewSpyglass.setLayoutParams(layoutParams);
    }

    float[] tmp01 = new float[3];

    private int calculateColor() {
        tmp01[0] = hue;
        tmp01[1] = sat;
        tmp01[2] = val;
        return Color.HSVToColor(tmp01);
    }
}
