package org.atalk.xryptomail.view;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.UiThread;


public class ThemeUtils
{
    private static final TypedValue TYPED_VALUE = new TypedValue();

    @ColorInt
    public static int getStyledColor(Context context, @AttrRes int attr)
    {
        return getStyledColor(context.getTheme(), attr);
    }

    @ColorInt
    @UiThread
    public static int getStyledColor(Theme theme, @AttrRes int attr)
    {
        theme.resolveAttribute(attr, TYPED_VALUE, true);
        return TYPED_VALUE.data;
    }
}
