package org.atalk.xryptomail.mail.store.imap;

import org.atalk.xryptomail.mail.Flag;

import java.util.Set;

class UidSearchCommandBuilder
{
    private String queryString;
    private boolean performFullTextSearch;
    private Set<Flag> requiredFlags;
    private Set<Flag> forbiddenFlags;


    public UidSearchCommandBuilder queryString(String queryString)
    {
        this.queryString = queryString;
        return this;
    }

    public UidSearchCommandBuilder performFullTextSearch(boolean performFullTextSearch)
    {
        this.performFullTextSearch = performFullTextSearch;
        return this;
    }

    public UidSearchCommandBuilder requiredFlags(Set<Flag> requiredFlags)
    {
        this.requiredFlags = requiredFlags;
        return this;
    }

    public UidSearchCommandBuilder forbiddenFlags(Set<Flag> forbiddenFlags)
    {
        this.forbiddenFlags = forbiddenFlags;
        return this;
    }

    public String build()
    {
        StringBuilder builder = new StringBuilder(Commands.UID_SEARCH);
        addQueryString(builder);
        addFlags(builder, requiredFlags, false);
        addFlags(builder, forbiddenFlags, true);
        return builder.toString();
    }

    private void addQueryString(StringBuilder builder)
    {
        if (queryString == null) {
            return;
        }

        String encodedQuery = ImapUtility.encodeString(queryString);
        if (performFullTextSearch) {
            builder.append(" TEXT ").append(encodedQuery);
        }
        else {
            builder.append(" OR SUBJECT ").append(encodedQuery).append(" FROM ").append(encodedQuery);
        }
    }

    private void addFlags(StringBuilder builder, Set<Flag> flagSet, boolean addNot)
    {
        if (flagSet == null || flagSet.isEmpty()) {
            return;
        }

        for (Flag flag : flagSet) {
            if (addNot) {
                builder.append(" NOT");
            }

            //noinspection EnumSwitchStatementWhichMissesCases
            switch (flag) {
                case DELETED: {
                    builder.append(" DELETED");
                    break;
                }
                case SEEN: {
                    builder.append(" SEEN");
                    break;
                }
                case ANSWERED: {
                    builder.append(" ANSWERED");
                    break;
                }
                case FLAGGED: {
                    builder.append(" FLAGGED");
                    break;
                }
                case DRAFT: {
                    builder.append(" DRAFT");
                    break;
                }
                case RECENT: {
                    builder.append(" RECENT");
                    break;
                }
                default: {
                    throw new IllegalStateException("Unsupported flag: " + flag);
                }
            }
        }
    }
}
