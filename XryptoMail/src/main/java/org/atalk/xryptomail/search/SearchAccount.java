package org.atalk.xryptomail.search;

import android.content.Context;

import org.atalk.xryptomail.BaseAccount;
import org.atalk.xryptomail.R;
import org.atalk.xryptomail.search.SearchSpecification.Attribute;
import org.atalk.xryptomail.search.SearchSpecification.SearchField;

/**
 * This class is basically a wrapper around a LocalSearch. It allows to expose it as
 * an account. This is a meta-account containing all the e-mail that matches the search.
 */
public class SearchAccount implements BaseAccount {
    public static final String ALL_MESSAGES = "all_messages";
    public static final String UNIFIED_INBOX = "unified_inbox";
	
    // create the all messages search ( all accounts is default when none specified )
    public static SearchAccount createAllMessagesAccount(Context context) {
        String name = context.getString(R.string.search_all_messages_title);

        LocalSearch tmpSearch = new LocalSearch(name);
        tmpSearch.and(SearchField.SEARCHABLE, "1", Attribute.EQUALS);
        return new SearchAccount(ALL_MESSAGES, tmpSearch, name,
                context.getString(R.string.search_all_messages_detail));
    }

    /**
     * Create a {@code SearchAccount} instance for the Unified Inbox.
     *
     * @param context
     *         A {@link Context} instance that will be used to get localized strings and will be
     *         passed on to the {@code SearchAccount} instance.
     *
     * @return The {@link SearchAccount} instance for the Unified Inbox.
     */
    public static SearchAccount createUnifiedInboxAccount(Context context) {
        String name = context.getString(R.string.integrated_inbox_title);
        LocalSearch tmpSearch = new LocalSearch(name);
        tmpSearch.and(SearchField.INTEGRATE, "1", Attribute.EQUALS);
        return new SearchAccount(UNIFIED_INBOX, tmpSearch, name,
                context.getString(R.string.integrated_inbox_detail));
    }

    private final String mId;
    private String mEmail;
    private String mDescription;
    private final LocalSearch mSearch;

    public SearchAccount(String id, LocalSearch search, String description, String email)
            throws IllegalArgumentException {

        if (search == null) {
            throw new IllegalArgumentException("Provided LocalSearch was null");
        }

        mId = id;
        mSearch = search;
        mDescription = description;
        mEmail = email;
    }

    public String getId() {
        return mId;
    }

    @Override
    public synchronized String getEmail() {
        return mEmail;
    }

    @Override
    public synchronized void setEmail(String email) {
        this.mEmail = email;
    }

    @Override
    public String getDescription() {
        return mDescription;
    }

    @Override
    public void setDescription(String description) {
        this.mDescription = description;
    }

    public LocalSearch getRelatedSearch() {
        return mSearch;
    }

    /**
     * Returns the ID of this {@code SearchAccount} instance.
     *
     * <p>
     * This isn't really a UUID. But since we don't expose this value to other apps and we only
     * use the account UUID as opaque string (e.g. as key in a {@code Map}) we're fine.<br>
     * Using a constant string is necessary to identify the same search account even when the
     * corresponding {@link SearchAccount} object has been recreated.
     * </p>
     */
    @Override
    public String getUuid() {
        return mId;
    }
}
