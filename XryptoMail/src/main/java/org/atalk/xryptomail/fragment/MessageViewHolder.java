package org.atalk.xryptomail.fragment;

import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.ui.ContactBadge;

public class MessageViewHolder implements View.OnClickListener {
    private final MessageListFragment fragment;
    public TextView subject;
    public TextView preview;
    public TextView from;
    public TextView time;
    public TextView date;
    public View chip;
    public TextView threadCount;
    public CheckBox flagged;
    public CheckBox selected;
    public int position = -1;
    public ContactBadge contactBadge;

    public MessageViewHolder(MessageListFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public void onClick(View view) {
        if (position != -1) {
            switch (view.getId()) {
                case R.id.selected_checkbox:
                    fragment.toggleMessageSelectWithAdapterPosition(position);
                    break;
                case R.id.flagged_bottom_right:
                case R.id.flagged_center_right:
                    fragment.toggleMessageFlagWithAdapterPosition(position);
                    break;
            }
        }
    }
}
