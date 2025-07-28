package org.atalk.xryptomail.view;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListenerAdapter;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.TextView;

import org.atalk.xryptomail.R;

/**
 * TextInputLayout temporary workaround for helper text showing
 */
@SuppressWarnings("unused")
public class TextInputLayoutWithHelperText extends TextInputLayout {

    static final Interpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();

    private CharSequence mHelperText;
    private ColorStateList mHelperTextColor;
    private boolean mHelperTextEnabled = false;
    private boolean mErrorEnabled = false;
    private TextView mHelperView;
    private int mHelperTextAppearance = R.style.HelperTextAppearance;

    public TextInputLayoutWithHelperText(Context _context) {
        super(_context);
    }

    public TextInputLayoutWithHelperText(Context _context, AttributeSet _attrs) {
        super(_context, _attrs);

        final TypedArray a = getContext().obtainStyledAttributes(
                _attrs, R.styleable.TextInputLayoutWithHelperText,0,0);
        try {
            mHelperTextColor = a.getColorStateList(R.styleable.TextInputLayoutWithHelperText_helperTextColor);
            mHelperText = a.getText(R.styleable.TextInputLayoutWithHelperText_helperText);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void addView(@NonNull View child, int index, @NonNull ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child instanceof EditText) {
            if (!TextUtils.isEmpty(mHelperText)) {
                setHelperText(mHelperText);
            }
        }
    }

    public int getHelperTextAppearance() {
        return mHelperTextAppearance;
    }

    public void setHelperTextAppearance(int _helperTextAppearanceResId) {
        mHelperTextAppearance = _helperTextAppearanceResId;
    }

    public void setHelperTextColor(ColorStateList _helperTextColor) {
        mHelperTextColor = _helperTextColor;
    }

    public void setHelperTextEnabled(boolean _enabled) {
        if (mHelperTextEnabled == _enabled) return;
        if (_enabled && mErrorEnabled) {
            setErrorEnabled(false);
        }
        if (mHelperTextEnabled != _enabled) {
            if (_enabled) {
                mHelperView = new TextView(getContext());
                mHelperView.setTextAppearance(mHelperTextAppearance);
                if (mHelperTextColor != null){
                    mHelperView.setTextColor(mHelperTextColor);
                }
                mHelperView.setVisibility(INVISIBLE);
                addView(mHelperView);
                // getEditText() can be null and crash
                if ((mHelperView != null) && (getEditText() != null)) {
                    mHelperView.setPaddingRelative(
                            getEditText().getPaddingStart(),
                            0, getEditText().getPaddingEnd(),
                            getEditText().getPaddingBottom());
                }
            } else {
                removeView(mHelperView);
                mHelperView = null;
            }

            mHelperTextEnabled = _enabled;
        }
    }

    public void setHelperText(CharSequence _helperText) {
        mHelperText = _helperText;
        if (!mHelperTextEnabled) {
            if (TextUtils.isEmpty(mHelperText)) {
                return;
            }
            setHelperTextEnabled(true);
        }

        if (!TextUtils.isEmpty(mHelperText)) {
            mHelperView.setText(mHelperText);
            mHelperView.setVisibility(VISIBLE);
            mHelperView.setAlpha(0.0f);
            mHelperView.animate()
                    .alpha(1.0f).setDuration(200L)
                    .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                    .setListener(null).start();
        } else if (mHelperView.getVisibility() == VISIBLE) {
            mHelperView.animate()
                    .alpha(0.0f).setDuration(200L)
                    .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        public void onAnimationEnd(View view) {
                            mHelperView.setText(null);
                            mHelperView.setVisibility(INVISIBLE);
                        }
                    }).start();
        }
        sendAccessibilityEvent(2048);
    }

    @Override
    public void setErrorEnabled(boolean _enabled) {
        if (mErrorEnabled == _enabled) return;
        mErrorEnabled = _enabled;
        if (_enabled && mHelperTextEnabled) {
            setHelperTextEnabled(false);
        }

        super.setErrorEnabled(_enabled);

        if (!(_enabled || TextUtils.isEmpty(mHelperText))) {
            setHelperText(mHelperText);
        }
    }

}


