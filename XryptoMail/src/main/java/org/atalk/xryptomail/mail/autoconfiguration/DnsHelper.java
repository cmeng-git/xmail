package org.atalk.xryptomail.mail.autoconfiguration;


import android.net.TrafficStats;

import java.net.UnknownHostException;

import org.atalk.xryptomail.XryptoMail;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.TextParseException;


public class DnsHelper {
    public static String getMxDomain(String domain) throws UnknownHostException {
        DNSOperation dnsOperation = new DNSOperation();
        MXRecord mxRecord;
        TrafficStats.setThreadStatsTag(XryptoMail.THREAD_ID);
        try {
            mxRecord = dnsOperation.mxLookup(domain);
        } catch (TextParseException e) {
            return null;
        }
        if (mxRecord != null) {
            final String target = mxRecord.getTarget().toString(true);
            return getDomainFromFqdn(target);
        }
        return null;
    }

    private static String getDomainFromFqdn(String fqdn) {
        final String[] parts = fqdn.split("\\.");

        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
