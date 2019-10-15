package org.atalk.xryptomail.controller;

import org.apache.commons.io.output.CountingOutputStream;
import org.atalk.xryptomail.mail.DefaultBodyFactory;

import java.io.*;
import java.util.*;

class ProgressBodyFactory extends DefaultBodyFactory {
    private final ProgressListener progressListener;

    ProgressBodyFactory(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    @Override
    protected void copyData(InputStream inputStream, OutputStream outputStream) throws IOException {
        final CountingOutputStream countingOutputStream = new CountingOutputStream(outputStream);

        Timer timer = new Timer();
        try {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    progressListener.updateProgress(countingOutputStream.getCount());
                }
            }, 0, 50);

            super.copyData(inputStream, countingOutputStream);
        } finally {
            timer.cancel();
        }
    }

    interface ProgressListener {
        void updateProgress(int progress);
    }
}
