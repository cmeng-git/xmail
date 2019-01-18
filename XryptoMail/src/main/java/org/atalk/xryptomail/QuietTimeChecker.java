package org.atalk.xryptomail;

import java.util.Calendar;

class QuietTimeChecker {
    private final Clock clock;
    private final int quietTimeStart;
    private final int quietTimeEnd;

    QuietTimeChecker(Clock clock, String quietTimeStart, String quietTimeEnd) {
        this.clock = clock;
        this.quietTimeStart = parseTime(quietTimeStart);
        this.quietTimeEnd = parseTime(quietTimeEnd);
    }

    private static int parseTime(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        return hour * 60 + minute;
    }

    public boolean isQuietTime() {
        // If start and end times are the same, we're never quiet
        if (quietTimeStart == quietTimeEnd) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(clock.getTime());

        int minutesSinceMidnight = (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE);

        if (quietTimeStart > quietTimeEnd) {
            if (minutesSinceMidnight >= quietTimeStart || minutesSinceMidnight <= quietTimeEnd) {
                return true;
            }
        } else {
            if (minutesSinceMidnight >= quietTimeStart && minutesSinceMidnight <= quietTimeEnd) {
                return true;
            }
        }

        return false;
    }
}
