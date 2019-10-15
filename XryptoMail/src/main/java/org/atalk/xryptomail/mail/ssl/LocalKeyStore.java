package org.atalk.xryptomail.mail.ssl;

import android.content.Context;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import timber.log.Timber;

public class LocalKeyStore
{
    private static final int KEY_STORE_FILE_VERSION = 1;

    private static LocalKeyStore INSTANCE;
    private static String sKeyStoreLocation;
    private File mKeyStoreFile;
    private KeyStore mKeyStore;

    public static LocalKeyStore createInstance(Context context)
    {
        if (INSTANCE == null) {
            String keyStoreLocation = context.getDir("KeyStore", Context.MODE_PRIVATE).toString();
            INSTANCE = new LocalKeyStore(keyStoreLocation);
        }
        return INSTANCE;
    }

    private LocalKeyStore(String keyStoreLocation)
    {
        sKeyStoreLocation = keyStoreLocation;
        initializeKeyStore();
    }

    // INSTANCE is already initialize on xmail first launch
    public static LocalKeyStore getInstance() {
        return INSTANCE;
    }

    /**
     * Reinitialize the local key store with stored certificates
     */
    private synchronized void initializeKeyStore()
    {
        upgradeKeyStoreFile();

        File file = new File(getKeyStoreFilePath(KEY_STORE_FILE_VERSION));
        if (file.length() == 0) {
            /*
             * The file may be empty (e.g., if it was created with
             * File.createTempFile). We can't pass an empty file to
             * Keystore.load. Instead, we let it be created anew.
             */
            if (file.exists() && !file.delete()) {
                Timber.d("Failed to delete empty keystore file: %s", file.getAbsolutePath());
            }
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            // If the file doesn't exist, that's fine, too
        }

        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(fis, "".toCharArray());
            mKeyStore = store;
            mKeyStoreFile = file;
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize local key store");
            // Use of the local key store is effectively disabled.
            mKeyStore = null;
            mKeyStoreFile = null;
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    private void upgradeKeyStoreFile()
    {
        if (KEY_STORE_FILE_VERSION > 0) {
            // Blow away version "0" because certificate aliases have changed.
            File versionZeroFile = new File(getKeyStoreFilePath(0));
            if (versionZeroFile.exists() && !versionZeroFile.delete()) {
                Timber.d("Failed to delete old key-store file: %s", versionZeroFile.getAbsolutePath());
            }
        }
    }

    public synchronized void addCertificate(String host, int port,
            X509Certificate certificate) throws CertificateException
    {
        if (mKeyStore == null) {
            throw new CertificateException(
                    "Certificate not added because key store not initialized");
        }
        try {
            mKeyStore.setCertificateEntry(getCertKey(host, port), certificate);
        } catch (KeyStoreException e) {
            throw new CertificateException(
                    "Failed to add certificate to local key store", e);
        }
        writeCertificateFile();
    }

    private void writeCertificateFile() throws CertificateException
    {
        java.io.OutputStream keyStoreStream = null;
        try {
            keyStoreStream = new java.io.FileOutputStream(mKeyStoreFile);
            mKeyStore.store(keyStoreStream, "".toCharArray());
        } catch (FileNotFoundException e) {
            throw new CertificateException("Unable to write KeyStore: "
                    + e.getMessage());
        } catch (CertificateException e) {
            throw new CertificateException("Unable to write KeyStore: "
                    + e.getMessage());
        } catch (IOException e) {
            throw new CertificateException("Unable to write KeyStore: "
                    + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException("Unable to write KeyStore: "
                    + e.getMessage());
        } catch (KeyStoreException e) {
            throw new CertificateException("Unable to write KeyStore: "
                    + e.getMessage());
        } finally {
            IOUtils.closeQuietly(keyStoreStream);
        }
    }

    public synchronized boolean isValidCertificate(Certificate certificate, String host, int port)
    {
        if (mKeyStore == null) {
            return false;
        }
        try {
            Certificate storedCert = mKeyStore.getCertificate(getCertKey(host, port));
            return (storedCert != null && storedCert.equals(certificate));
        } catch (KeyStoreException e) {
            return false;
        }
    }

    private static String getCertKey(String host, int port)
    {
        return host + ":" + port;
    }

    public synchronized void deleteCertificate(String oldHost, int oldPort)
    {
        if (mKeyStore == null) {
            return;
        }
        try {
            mKeyStore.deleteEntry(getCertKey(oldHost, oldPort));
            writeCertificateFile();
        } catch (KeyStoreException e) {
            // Ignore: most likely there was no cert. found
        } catch (CertificateException e) {
            Timber.e(e, "Error updating the local key store file");
        }
    }

    private String getKeyStoreFilePath(int version)
    {
        if (version < 1) {
            return sKeyStoreLocation + File.separator + "KeyStore.bks";
        }
        else {
            return sKeyStoreLocation + File.separator + "KeyStore_v" + version + ".bks";
        }
    }
}
