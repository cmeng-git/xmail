/* Sourced from http://code.google.com/p/android-color-picker/source/browse/trunk/AmbilWarna/src/yuku/ambilwarna/AmbilWarnaBox.java?r=1
 * On 2010-11-07
 * Translated to English, Ported to use the same (inferior) API as the more standard "ColorPickerDialog" and imported into the K-9 namespace by Jesse Vincent
 * In an ideal world, we should move to using AmbilWarna as an Android Library Project in the future
 * License: Apache 2.0
 * Author: yukuku@code.google.com
 */

package org.atalk.xryptomail.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import org.atalk.xryptomail.R;

public class ColorPicker extends View {
    Paint paint;
    Shader dalam;
    Shader luar;
    float hue;
    float onedp;
    float sizeUiDp = 240.f;
    float sizeUiPx; // diset di constructor
    float[] tmp00 = new float[3];

	/**
	 * Colors to construct the color wheel using {@link SweepGradient}.
	 *
	 * <p>
	 * Note: The algorithm in {@link #normalizeColor(int)} highly depends on these exact values. Be
	 * aware that {@link #setColor(int)} might break if you change this array.
	 * </p>
	 */
	private static final int[] COLORS = new int[] { 0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF,
		0xFF00FF00, 0xFFFFFF00, 0xFFFF0000 };

    public ColorPicker(Context context) {
        this(context, null);
    }

    public ColorPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        onedp = context.getResources().getDimension(R.dimen.colorpicker_onedp);
        sizeUiPx = sizeUiDp * onedp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (paint == null) {
            paint = new Paint();
            luar = new LinearGradient(0.f, 0.f, 0.f, sizeUiPx, 0xffffffff, 0xff000000, TileMode.CLAMP);
        }
        tmp00[1] = tmp00[2] = 1.f;
        tmp00[0] = hue;
        int rgb = Color.HSVToColor(tmp00);

        dalam = new LinearGradient(0.f, 0.f, sizeUiPx, 0.f, 0xffffffff, rgb, TileMode.CLAMP);
        ComposeShader shader = new ComposeShader(luar, dalam, PorterDuff.Mode.MULTIPLY);

        paint.setShader(shader);
        canvas.drawRect(0.f, 0.f, sizeUiPx, sizeUiPx, paint);
    }

    public void setHue(float hue) {
        this.hue = hue;
        invalidate();
    }
    
	/**
	 * Get a random color.
	 *
	 * @return The ARGB value of a randomly selected color.
	 */
	public static int getRandomColor() {
		return calculateColor((float) (Math.random() * 2 * Math.PI));
	}

	private static int ave(int s, int d, float p) {
		return s + java.lang.Math.round(p * (d - s));
	}

	/**
	 * Calculate the color using the supplied angle.
	 *
	 * @param angle
	 *         The selected color's position expressed as angle (in rad).
	 *
	 * @return The ARGB value of the color on the color wheel at the specified angle.
	 */
	private static int calculateColor(float angle) {
		float unit = (float) (angle / (2 * Math.PI));
		if (unit < 0) {
			unit += 1;
		}

		if (unit <= 0) {
			return COLORS[0];
		}
		if (unit >= 1) {
			return COLORS[COLORS.length - 1];
		}

		float p = unit * (COLORS.length - 1);
		int i = (int) p;
		p -= i;

		int c0 = COLORS[i];
		int c1 = COLORS[i + 1];
		int a = ave(Color.alpha(c0), Color.alpha(c1), p);
		int r = ave(Color.red(c0), Color.red(c1), p);
		int g = ave(Color.green(c0), Color.green(c1), p);
		int b = ave(Color.blue(c0), Color.blue(c1), p);

		return Color.argb(a, r, g, b);
	}    
}
