package org.atalk.xryptomail.ui;

import android.content.Context;
import androidx.appcompat.widget.AppCompatEditText;
import android.util.AttributeSet;

/**
 * An {@link android.widget.EditText} extension with methods that convert line endings from
 * {@code \r\n} to {@code \n} and back again when setting and getting text.
 *
 */
public class EolConvertingEditText extends AppCompatEditText //ContentEditText
{

    public EolConvertingEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Return the text the EolConvertingEditText is displaying.
     *
     * @return A string with any line endings converted to {@code \r\n}.
     */
    public String getCharacters() {
        return getText().toString().replace("\n", "\r\n");
    }

    /**
     * Sets the string value of the EolConvertingEditText. Any line endings
     * in the string will be converted to {@code \n}.
     *
     * @param text String
     */
    public void  setCharacters(CharSequence text) {
        setText(text.toString().replace("\r\n", "\n"));
    }
}
