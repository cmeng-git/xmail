package org.atalk.xryptomail.ui.compose;

import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;

import org.atalk.xryptomail.FontSizes;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.activity.MessageCompose;
import org.atalk.xryptomail.mailstore.AttachmentResolver;
import org.atalk.xryptomail.message.QuotedTextMode;
import org.atalk.xryptomail.message.SimpleMessageFormat;
import org.atalk.xryptomail.message.html.HtmlConverter;
import org.atalk.xryptomail.ui.EolConvertingEditText;
import org.atalk.xryptomail.view.MessageWebView;

public class QuotedMessageMvpView
{
    private final Button mQuotedTextShow;
    private final View mQuotedTextBar;
    private final ImageButton mQuotedTextEdit;
    private final EolConvertingEditText mQuotedText;
    private final MessageWebView mQuotedHTML;
    private final EolConvertingEditText mMessageContentView;
    private final ImageButton mQuotedTextDelete;

    public QuotedMessageMvpView(MessageCompose messageCompose)
    {
        mQuotedTextShow = messageCompose.findViewById(R.id.quoted_text_show);
        mQuotedTextBar = messageCompose.findViewById(R.id.quoted_text_bar);
        mQuotedTextEdit = messageCompose.findViewById(R.id.quoted_text_edit);
        mQuotedTextDelete = messageCompose.findViewById(R.id.quoted_text_delete);
        mQuotedText = messageCompose.findViewById(R.id.quoted_text);
        mQuotedText.getInputExtras(true).putBoolean("allowEmoji", true);

        mQuotedHTML = messageCompose.findViewById(R.id.quoted_html);
        mQuotedHTML.configure();
        // Disable the ability to click links in the quoted HTML page. I think this is a nice feature, but if someone
        // feels this should be a preference (or should go away all together), I'm ok with that too. -achen 20101130
        mQuotedHTML.setWebViewClient(new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                return true;
            }
        });

        mMessageContentView = messageCompose.findViewById(R.id.message_content);
        mMessageContentView.setFocusableInTouchMode(true);
    }

    public void setOnClickPresenter(final QuotedMessagePresenter presenter)
    {
        OnClickListener onClickListener = view -> {
            switch (view.getId()) {
                case R.id.quoted_text_show:
                    presenter.onClickShowQuotedText();
                    break;
                case R.id.quoted_text_delete:
                    presenter.onClickDeleteQuotedText();
                    break;
                case R.id.quoted_text_edit:
                    presenter.onClickEditQuotedText();
                    break;
            }
        };

        mQuotedTextShow.setOnClickListener(onClickListener);
        mQuotedTextEdit.setOnClickListener(onClickListener);
        mQuotedTextDelete.setOnClickListener(onClickListener);
    }

    public void addTextChangedListener(TextWatcher draftNeedsChangingTextWatcher)
    {
        mQuotedText.addTextChangedListener(draftNeedsChangingTextWatcher);
    }

    /**
     * Show or hide the quoted text.
     *
     * @param mode The value to set {@link QuotedTextMode} to.
     */
    public void showOrHideQuotedText(QuotedTextMode mode, SimpleMessageFormat quotedTextFormat)
    {
        switch (mode) {
            case NONE: {
                mQuotedTextShow.setVisibility(View.GONE);
                mQuotedTextBar.setVisibility(View.GONE);
                mQuotedText.setVisibility(View.GONE);
                mQuotedHTML.setVisibility(View.GONE);
                mQuotedTextEdit.setVisibility(View.GONE);
                break;
            }
            case HIDE: {
                mQuotedTextShow.setVisibility(View.VISIBLE);
                mQuotedTextBar.setVisibility(View.GONE);
                mQuotedText.setVisibility(View.GONE);
                mQuotedHTML.setVisibility(View.GONE);
                mQuotedTextEdit.setVisibility(View.GONE);
                break;
            }
            case SHOW: {
                mQuotedTextShow.setVisibility(View.GONE);
                mQuotedTextBar.setVisibility(View.VISIBLE);

                if (quotedTextFormat == SimpleMessageFormat.HTML) {
                    mQuotedText.setVisibility(View.GONE);
                    mQuotedHTML.setVisibility(View.VISIBLE);
                    mQuotedTextEdit.setVisibility(View.VISIBLE);
                }
                else {
                    mQuotedText.setVisibility(View.VISIBLE);
                    mQuotedHTML.setVisibility(View.GONE);
                    mQuotedTextEdit.setVisibility(View.GONE);
                }
                break;
            }
        }
    }

    public void setFontSizes(FontSizes mFontSizes, int fontSize)
    {
        mFontSizes.setViewTextSize(mQuotedText, fontSize);
    }

    public void setQuotedHtml(String quotedContent, AttachmentResolver attachmentResolver)
    {
        mQuotedHTML.displayHtmlContentWithInlineAttachments(
                HtmlConverter.wrapMessageContent(quotedContent),
                attachmentResolver, null);
    }

    public void setQuotedText(String quotedText)
    {
        mQuotedText.setCharacters(quotedText);
    }

    // TODO we shouldn't have to retrieve the state from the view here
    public String getQuotedText()
    {
        return mQuotedText.getCharacters();
    }

    public void setMessageContentCharacters(String text)
    {
        mMessageContentView.setCharacters(text);
    }

    public void setMessageContentCursorPosition(int messageContentCursorPosition)
    {
        mMessageContentView.setSelection(messageContentCursorPosition);
    }
}
