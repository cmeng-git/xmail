package org.atalk.xryptomail.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import org.atalk.xryptomail.Account;
import org.atalk.xryptomail.Account.FolderMode;
import org.atalk.xryptomail.Preferences;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;
import org.atalk.xryptomail.controller.MessagingController;
import org.atalk.xryptomail.controller.MessagingListener;
import org.atalk.xryptomail.controller.SimpleMessagingListener;
import org.atalk.xryptomail.mail.Folder;
import org.atalk.xryptomail.mailstore.LocalFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChooseFolder extends XMListActivity
{
    public static final String EXTRA_ACCOUNT = "org.atalk.xryptomail.ChooseFolder_account";
    public static final String EXTRA_CUR_FOLDER = "org.atalk.xryptomail.ChooseFolder_curfolder";
    public static final String EXTRA_SEL_FOLDER = "org.atalk.xryptomail.ChooseFolder_selfolder";
    public static final String EXTRA_NEW_FOLDER = "org.atalk.xryptomail.ChooseFolder_newfolder";
    public static final String EXTRA_MESSAGE = "org.atalk.xryptomail.ChooseFolder_message";
    public static final String EXTRA_SHOW_CURRENT = "org.atalk.xryptomail.ChooseFolder_showcurrent";
    public static final String EXTRA_SHOW_FOLDER_NONE = "org.atalk.xryptomail.ChooseFolder_showOptionNone";
    public static final String EXTRA_SHOW_DISPLAYABLE_ONLY = "org.atalk.xryptomail.ChooseFolder_showDisplayableOnly";

    String mFolder;
    String mSelectFolder;
    Account mAccount;
    MessageReference mMessageReference;
    ArrayAdapter<String> mAdapter;
    private ChooseFolderHandler mHandler = new ChooseFolderHandler();
    String mHeldInbox = null;
    boolean mHideCurrentFolder = true;
    boolean mShowOptionNone = false;
    boolean mShowDisplayableOnly = false;

    /**
     * What folders to display.<br/>
     * Initialized to whatever is configured
     * but can be overridden via {@link #onOptionsItemSelected(MenuItem)}
     * while this activity is showing.
     */
    private Account.FolderMode mMode;

    /**
     * Current filter used by our ArrayAdapter.<br/>
     * Created on the fly and invalidated if a new
     * set of folders is chosen via {@link #onOptionsItemSelected(MenuItem)}
     */
    private FolderListFilter<String> mMyFilter = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.list_content_simple);
        getListView().setFastScrollEnabled(true);
        getListView().setItemsCanFocus(false);
        getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
        Intent intent = getIntent();
        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        if (mAccount == null)
            return;

        if (intent.hasExtra(EXTRA_MESSAGE)) {
            String messageReferenceString = intent.getStringExtra(EXTRA_MESSAGE);
            mMessageReference = MessageReference.parse(messageReferenceString);
        }
        mFolder = intent.getStringExtra(EXTRA_CUR_FOLDER);
        mSelectFolder = intent.getStringExtra(EXTRA_SEL_FOLDER);
        if (intent.getStringExtra(EXTRA_SHOW_CURRENT) != null) {
            mHideCurrentFolder = false;
        }
        if (intent.getStringExtra(EXTRA_SHOW_FOLDER_NONE) != null) {
            mShowOptionNone = true;
        }
        if (intent.getStringExtra(EXTRA_SHOW_DISPLAYABLE_ONLY) != null) {
            mShowDisplayableOnly = true;
        }
        if (mFolder == null)
            mFolder = "";

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        {
            private Filter myFilter = null;

            @Override
            public Filter getFilter()
            {
                if (myFilter == null) {
                    myFilter = new FolderListFilter<String>(this);
                }
                return myFilter;
            }
        };

        setListAdapter(mAdapter);
        mMode = mAccount.getFolderTargetMode();
        MessagingController.getInstance(getApplication()).listFolders(mAccount, false, mListener);

        this.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                Intent result = new Intent();
                result.putExtra(EXTRA_ACCOUNT, mAccount.getUuid());
                result.putExtra(EXTRA_CUR_FOLDER, mFolder);
                String destFolderName = ((TextView) view).getText().toString();
                if (mHeldInbox != null && getString(R.string.special_mailbox_name_inbox).equals(destFolderName)) {
                    destFolderName = mHeldInbox;
                }
                result.putExtra(EXTRA_NEW_FOLDER, destFolderName);
                if (mMessageReference != null) {
                    result.putExtra(EXTRA_MESSAGE, mMessageReference.toIdentityString());
                }
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }

    class ChooseFolderHandler extends Handler
    {
        private static final int MSG_PROGRESS = 1;
        private static final int MSG_SET_SELECTED_FOLDER = 2;

        @Override
        public void handleMessage(android.os.Message msg)
        {
            switch (msg.what) {
                case MSG_PROGRESS: {
                    setProgressBarIndeterminateVisibility(msg.arg1 != 0);
                    break;
                }
                case MSG_SET_SELECTED_FOLDER: {
                    getListView().setSelection(msg.arg1);
                    break;
                }
            }
        }

        public void progress(boolean progress)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_PROGRESS;
            msg.arg1 = progress ? 1 : 0;
            sendMessage(msg);
        }

        public void setSelectedFolder(int position)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_SET_SELECTED_FOLDER;
            msg.arg1 = position;
            sendMessage(msg);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.folder_select_option, menu);
        configureFolderSearchView(menu);
        return true;
    }

    private void configureFolderSearchView(Menu menu)
    {
        final MenuItem folderMenuItem = menu.findItem(R.id.filter_folders);
        final SearchView folderSearchView = (SearchView) folderMenuItem.getActionView();
        folderSearchView.setQueryHint(getString(R.string.folder_list_filter_hint));
        folderSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                folderMenuItem.collapseActionView();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                mAdapter.getFilter().filter(newText);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.display_1st_class: {
                setDisplayMode(FolderMode.FIRST_CLASS);
                return true;
            }
            case R.id.display_1st_and_2nd_class: {
                setDisplayMode(FolderMode.FIRST_AND_SECOND_CLASS);
                return true;
            }
            case R.id.display_not_second_class: {
                setDisplayMode(FolderMode.NOT_SECOND_CLASS);
                return true;
            }
            case R.id.display_all: {
                setDisplayMode(FolderMode.ALL);
                return true;
            }
            case R.id.list_folders: {
                onRefresh();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void onRefresh()
    {
        MessagingController.getInstance(getApplication()).listFolders(mAccount, true, mListener);
    }


    private void setDisplayMode(FolderMode aMode)
    {
        mMode = aMode;
        // invalidate the current filter as it is working on an inval
        if (mMyFilter != null) {
            mMyFilter.invalidate();
        }
        //re-populate the list
        MessagingController.getInstance(getApplication()).listFolders(mAccount, false, mListener);
    }

    private MessagingListener mListener = new SimpleMessagingListener()
    {
        @Override
        public void listFoldersStarted(Account account)
        {
            if (!account.equals(mAccount)) {
                return;
            }
            mHandler.progress(true);
        }

        @Override
        public void listFoldersFailed(Account account, String message)
        {
            if (!account.equals(mAccount)) {
                return;
            }
            mHandler.progress(false);
        }

        @Override
        public void listFoldersFinished(Account account)
        {
            if (!account.equals(mAccount)) {
                return;
            }
            mHandler.progress(false);
        }

        @Override
        public void listFolders(Account account, List<LocalFolder> folders)
        {
            if (!account.equals(mAccount)) {
                return;
            }
            Account.FolderMode aMode = mMode;

            List<String> newFolders = new ArrayList<>();
            List<String> topFolders = new ArrayList<>();

            for (Folder folder : folders) {
                String name = folder.getServerId();

                // Inbox needs to be compared case-insensitively
                if (mHideCurrentFolder && (name.equals(mFolder)
                        || (mAccount.getInboxFolder().equalsIgnoreCase(mFolder)
                        && mAccount.getInboxFolder().equalsIgnoreCase(name)))) {
                    continue;
                }
                Folder.FolderClass fMode = folder.getDisplayClass();

                if ((aMode == FolderMode.FIRST_CLASS &&
                        fMode != Folder.FolderClass.FIRST_CLASS) || (
                        aMode == FolderMode.FIRST_AND_SECOND_CLASS &&
                                fMode != Folder.FolderClass.FIRST_CLASS &&
                                fMode != Folder.FolderClass.SECOND_CLASS) || (
                        aMode == FolderMode.NOT_SECOND_CLASS &&
                                fMode == Folder.FolderClass.SECOND_CLASS)) {
                    continue;
                }

                if (folder.isInTopGroup()) {
                    topFolders.add(name);
                }
                else {
                    newFolders.add(name);
                }
            }

            final Comparator<String> comparator = new Comparator<String>()
            {
                @Override
                public int compare(String s1, String s2)
                {
                    int ret = s1.compareToIgnoreCase(s2);
                    return (ret != 0) ? ret : s1.compareTo(s2);
                }
            };

            Collections.sort(topFolders, comparator);
            Collections.sort(newFolders, comparator);

            List<String> localFolders = new ArrayList<>(newFolders.size() +
                    topFolders.size() + ((mShowOptionNone) ? 1 : 0));

            if (mShowOptionNone) {
                localFolders.add(XryptoMail.FOLDER_NONE);
            }

            localFolders.addAll(topFolders);
            localFolders.addAll(newFolders);

            int selectedFolder = -1;

            /*
             * We're not allowed to change the adapter from a background thread, so we collect the
             * folder names and update the adapter in the UI thread (see finally block).
             */
            final List<String> folderList = new ArrayList<>();
            try {
                int position = 0;
                for (String name : localFolders) {
                    if (mAccount.getInboxFolder().equalsIgnoreCase(name)) {
                        folderList.add(getString(R.string.special_mailbox_name_inbox));
                        mHeldInbox = name;
                    }
                    else if (!account.getOutboxFolderName().equals(name)) {
                        folderList.add(name);
                    }
                    if (mSelectFolder != null) {
                        /*
                         * Never select EXTRA_CUR_FOLDER (mFolder) if EXTRA_SEL_FOLDER
                         * (mSelectedFolder) was provided.
                         */

                        if (name.equals(mSelectFolder)) {
                            selectedFolder = position;
                        }
                    }
                    else if (name.equals(mFolder)
                            || (mAccount.getInboxFolder().equalsIgnoreCase(mFolder)
                            && mAccount.getInboxFolder().equalsIgnoreCase(name))) {
                        selectedFolder = position;
                    }
                    position++;
                }
            } finally {
                runOnUiThread(() -> {
                    // Now we're in the UI-thread, we can safely change the contents of the adapter.
                    mAdapter.clear();
                    for (String folderName : folderList) {
                        mAdapter.add(folderName);
                    }
                    mAdapter.notifyDataSetChanged();

                    /*
                     * Only enable the text filter after the list has been
                     * populated to avoid possible race conditions because our
                     * FolderListFilter isn't really thread-safe.
                     */
                    getListView().setTextFilterEnabled(true);
                });
            }

            if (selectedFolder != -1) {
                mHandler.setSelectedFolder(selectedFolder);
            }
        }
    };
}
