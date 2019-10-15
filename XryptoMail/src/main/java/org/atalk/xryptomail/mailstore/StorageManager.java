package org.atalk.xryptomail.mailstore;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.atalk.xryptomail.R;
import org.atalk.xryptomail.XryptoMail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import timber.log.Timber;

/**
 * Manager for different {@link StorageProvider} -classes that abstract access
 * to sd-cards, additional internal memory and other storage-locations.
 */
public class StorageManager
{
    private static final String LOG_TAG = "StorageManager";

    /**
     * Provides entry points (File objects) to an underlying storage,
     * alleviating the caller from having to know where that storage is located.
     *
     * Allow checking for the denoted storage availability since its lifecycle
     * can evolving (a storage might become unavailable at some time and be back online later).
     */
    public interface StorageProvider
    {

        /**
         * Retrieve the uniquely identifier for the current implementation.
         *
         * It is expected that the identifier doesn't change over reboots since
         * it'll be used to save settings and retrieve the provider at a later time.
         *
         * The returned identifier doesn't have to be user friendly.
         *
         * @return Never {@code null}.
         */
        String getId();

        /**
         * Hook point for provider initialization.
         *
         * @param context Never {@code null}.
         */
        void init(Context context);

        /**
         * @param context Never {@code null}.
         * @return A user displayable, localized name for this provider. Never {@code null}.
         */
        String getName(Context context);

        /**
         * Some implementations may not be able to return valid File handles
         * because the device doesn't provide the denoted storage. You can check
         * the provider compatibility with this method to prevent from having to
         * invoke this provider ever again.
         *
         * @param context TODO
         * @return Whether this provider supports the current device.
         * @see StorageManager#getAvailableProviders()
         */
        boolean isSupported(Context context);

        /**
         * Return the {@link File} to the chosen email database file. The
         * resulting {@link File} doesn't necessarily match an existing file on the filesystem.
         *
         * @param context Never {@code null}.
         * @param id Never {@code null}.
         * @return Never {@code null}.
         */
        File getDatabase(Context context, String id);

        /**
         * Return the {@link File} to the chosen attachment directory. The
         * resulting {@link File} doesn't necessarily match an existing
         * directory on the filesystem.
         *
         * @param context Never {@code null}.
         * @param id Never {@code null}.
         * @return Never {@code null}.
         */
        File getAttachmentDirectory(Context context, String id);

        /**
         * Check for the underlying storage availability.
         *
         * @param context Never {@code null}.
         * @return Whether the underlying storage returned by this provider is
         * ready for read/write operations at the time of invocation.
         */
        boolean isReady(Context context);

        /**
         * Retrieve the root of the underlying storage.
         *
         * @param context Never {@code null}.
         * @return The root directory of the denoted storage. Never
         * {@code null}.
         */
        File getRoot(Context context);
    }

    /**
     * Interface for components wanting to be notified of storage availability events.
     */
    public interface StorageListener
    {
        /**
         * Invoked on storage mount (with read/write access).
         *
         * @param providerId Identifier (as returned by {@link StorageProvider#getId()}
         * of the newly mounted storage. Never {@code null}.
         */
        void onMount(String providerId);

        /**
         * Invoked when a storage is about to be unmounted.
         *
         * @param providerId Identifier (as returned by {@link StorageProvider#getId()}
         * of the to-be-unmounted storage. Never {@code null}.
         */
        void onUnmount(String providerId);
    }

    /**
     * Base provider class for providers that rely on well-known path to check
     * for storage availability.
     *
     * Since solely checking for paths can be unsafe, this class allows to check
     * for device compatibility using {@link #supportsVendor()}. If the vendor
     * specific check fails, the provider won't be able to provide any valid
     * File handle, regardless of the path existence.
     *
     * Moreover, this class validates the denoted storage path against mount
     * points using {@link StorageManager#isMountPoint(File)}.
     */
    public abstract static class FixedStorageProviderBase implements StorageProvider
    {
        /**
         * The root of the denoted storage. Used for mount points checking.
         */
        private File mRoot;

        /**
         * Chosen base directory
         */
        private File mApplicationDir;

        @Override
        public void init(final Context context)
        {
            mRoot = computeRoot(context);
            // use <STORAGE_ROOT>/XryptoMail
            mApplicationDir = new File(mRoot, "XryptoMail");
        }

        /**
         * Vendor specific checks
         *
         * @return Whether this provider supports the underlying vendor specific storage
         */
        protected abstract boolean supportsVendor();

        @Override
        public boolean isReady(Context context)
        {
            try {
                final File root = mRoot.getCanonicalFile();
                return isMountPoint(root)
                        && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
            } catch (IOException e) {
                Timber.w(e, "Specified root isn't ready: %s", mRoot);
                return false;
            }
        }

        @Override
        public final boolean isSupported(Context context)
        {
            return mRoot.isDirectory() && supportsVendor();
        }

        @Override
        public File getDatabase(Context context, String id)
        {
            return new File(mApplicationDir, id + ".db");
        }

        @Override
        public File getAttachmentDirectory(Context context, String id)
        {
            return new File(mApplicationDir, id + ".db_att");
        }

        @Override
        public final File getRoot(Context context)
        {
            return mRoot;
        }

        /**
         * Retrieve the well-known storage root directory from the actual
         * implementation.
         *
         * @param context Never {@code null}.
         * @return Never {@code null}.
         */
        protected abstract File computeRoot(Context context);
    }

    /**
     * Strategy to access the always available internal storage.
     *
     * This implementation is expected to work on every device since it's based
     * on the regular Android API {@link Context#getDatabasePath(String)} and
     * uses the result to retrieve the DB path and the attachment directory path.
     *
     * The underlying storage has always been used by K-9.
     */
    public static class InternalStorageProvider implements StorageProvider
    {

        public static final String ID = "InternalStorage";

        private File mRoot;

        @Override
        public String getId()
        {
            return ID;
        }

        @Override
        public void init(Context context)
        {
            // XXX
            mRoot = new File("/");
        }

        @Override
        public String getName(Context context)
        {
            return context.getString(R.string.local_storage_provider_internal_label);
        }

        @Override
        public boolean isSupported(Context context)
        {
            return true;
        }

        @Override
        public File getDatabase(Context context, String id)
        {
            return context.getDatabasePath(id + ".db");
        }

        @Override
        public File getAttachmentDirectory(Context context, String id)
        {
            // we store attachments in the database directory
            return context.getDatabasePath(id + ".db_att");
        }

        @Override
        public boolean isReady(Context context)
        {
            return true;
        }

        @Override
        public File getRoot(Context context)
        {
            return mRoot;
        }
    }

    /**
     * Strategy for accessing the storage as returned by
     * {@link Environment#getExternalStorageDirectory()}. In order to be
     * compliant with Android recommendation regarding application uninstalling
     * and to prevent from cluttering the storage root, the chosen directory
     * will be
     * {@code <STORAGE_ROOT>/Android/data/<APPLICATION_PACKAGE_NAME>/files/}
     *
     * The denoted storage is usually a SD card.
     *
     * This provider is expected to work on all devices but the returned
     * underlying storage might not be always available, due to
     * mount/unmount/USB share events.
     */
    public static class ExternalStorageProvider implements StorageProvider
    {

        public static final String ID = "ExternalStorage";

        /**
         * Root of the denoted storage.
         */
        private File mRoot;

        /**
         * Chosen base directory.
         */
        private File mApplicationDirectory;

        @Override
        public String getId()
        {
            return ID;
        }

        @Override
        public void init(Context context)
        {
            mRoot = Environment.getExternalStorageDirectory();
            mApplicationDirectory = new File(new File(new File(new File(mRoot, "Android"), "data"),
                    context.getPackageName()), "files");
        }

        @Override
        public String getName(Context context)
        {
            return context.getString(R.string.local_storage_provider_external_label);
        }

        @Override
        public boolean isSupported(Context context)
        {
            return true;
        }

        @Override
        public File getDatabase(Context context, String id)
        {
            return new File(mApplicationDirectory, id + ".db");
        }

        @Override
        public File getAttachmentDirectory(Context context, String id)
        {
            return new File(mApplicationDirectory, id + ".db_att");
        }

        @Override
        public boolean isReady(Context context)
        {
            return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        }

        @Override
        public File getRoot(Context context)
        {
            return mRoot;
        }
    }

    /**
     * Storage provider to allow access the /emmc directory on a HTC Incredible.
     *
     * <p>
     * This implementation is experimental and _untested_.
     * </p>
     *
     * See http://groups.google.com/group/android-developers/browse_frm/thread/96f15e57150ed173
     *
     * @see FixedStorageProviderBase
     */
    public static class HtcIncredibleStorageProvider extends FixedStorageProviderBase
    {

        public static final String ID = "HtcIncredibleStorage";

        @Override
        public String getId()
        {
            return ID;
        }

        @Override
        public String getName(Context context)
        {
            return context.getString(R.string.local_storage_provider_samsunggalaxy_label,
                    Build.MODEL);
        }

        @Override
        protected boolean supportsVendor()
        {
            return "inc".equals(Build.DEVICE);
        }

        @Override
        protected File computeRoot(Context context)
        {
            return new File("/emmc");
        }
    }

    /**
     * Storage provider to allow access the Samsung Galaxy S 'internal SD card'.
     *
     * <p>
     * This implementation is experimental and _untested_.
     * </p>
     *
     * See http://groups.google.com/group/android-developers/browse_frm/thread/a1adf7122a75a657
     *
     * @see FixedStorageProviderBase
     */
    public static class SamsungGalaxySStorageProvider extends FixedStorageProviderBase
    {

        public static final String ID = "SamsungGalaxySStorage";

        @Override
        public String getId()
        {
            return ID;
        }

        @Override
        public String getName(Context context)
        {
            return context.getString(R.string.local_storage_provider_samsunggalaxy_label,
                    Build.MODEL);
        }

        @Override
        protected boolean supportsVendor()
        {
            // FIXME
            return "GT-I5800".equals(Build.DEVICE) || "GT-I9000".equals(Build.DEVICE)
                    || "SGH-T959".equals(Build.DEVICE) || "SGH-I897".equals(Build.DEVICE);
        }

        @Override
        protected File computeRoot(Context context)
        {
            return Environment.getExternalStorageDirectory(); // was: new
            // File("/sdcard")
        }
    }

    /**
     * Stores storage provider locking information
     */
    public static class SynchronizationAid
    {
        /**
         * {@link Lock} has a thread semantic so it can't be released from
         * another thread - this flags act as a holder for the unmount state
         */
        public boolean unmounting = false;
        public final Lock readLock;
        public final Lock writeLock;

        {
            final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
            readLock = readWriteLock.readLock();
            writeLock = readWriteLock.writeLock();
        }
    }

    /**
     * The active storage providers.
     */
    private final Map<String, StorageProvider> mProviders = new LinkedHashMap<>();

    /**
     * Locking data for the active storage providers.
     */
    private final Map<StorageProvider, SynchronizationAid> mProviderLocks = new IdentityHashMap<>();

    protected final Context context;

    /**
     * Listener to be notified for storage related events.
     */
    private List<StorageListener> mListeners = new ArrayList<>();

    private static transient StorageManager instance;

    public static synchronized StorageManager getInstance(final Context context)
    {
        if (instance == null) {
            instance = new StorageManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * @param file Canonical file to match. Never {@code null}.
     * @return Whether the specified file matches a filesystem root.
     */
    public static boolean isMountPoint(final File file)
    {
        for (final File root : File.listRoots()) {
            if (root.equals(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param context Never {@code null}.
     * @throws NullPointerException If <tt>context</tt> is {@code null}.
     */
    protected StorageManager(final Context context) throws NullPointerException
    {
        if (context == null) {
            throw new NullPointerException("No Context given");
        }

        this.context = context;

        /*
         * 20101113/fiouzy:
         *
         * Here is where we define which providers are used, currently we only
         * allow the internal storage and the regular external storage.
         *
         * HTC Incredible storage and Samsung Galaxy S are omitted on purpose
         * (they're experimental and I don't have those devices to test).
         *
         *
         * !!! Make sure InternalStorageProvider is the first provider as it'll
         * be considered as the default provider !!!
         */
        final List<StorageProvider> allProviders = Arrays.asList(new InternalStorageProvider(),
                new ExternalStorageProvider());
        for (final StorageProvider provider : allProviders) {
            // check for provider compatibility
            if (provider.isSupported(context)) {
                // provider is compatible! proceeding

                provider.init(context);
                mProviders.put(provider.getId(), provider);
                mProviderLocks.put(provider, new SynchronizationAid());
            }
        }
    }

    /**
     * @return Never {@code null}.
     */
    public String getDefaultProviderId()
    {
        // assume there is at least 1 provider defined
        return mProviders.keySet().iterator().next();
    }

    /**
     * @param providerId Never {@code null}.
     * @return {@code null} if not found.
     */
    protected StorageProvider getProvider(final String providerId)
    {
        return mProviders.get(providerId);
    }

    /**
     * @param dbName Never {@code null}.
     * @param providerId Never {@code null}.
     * @return The resolved database file for the given provider ID.
     */
    public File getDatabase(final String dbName, final String providerId)
    {
        StorageProvider provider = getProvider(providerId);
        // TODO fallback to internal storage if no provider
        return provider.getDatabase(context, dbName);
    }

    /**
     * @param dbName Never {@code null}.
     * @param providerId Never {@code null}.
     * @return The resolved attachment directory for the given provider ID.
     */
    public File getAttachmentDirectory(final String dbName, final String providerId)
    {
        StorageProvider provider = getProvider(providerId);
        // TODO fallback to internal storage if no provider
        return provider.getAttachmentDirectory(context, dbName);
    }

    /**
     * @param providerId Never {@code null}.
     * @return Whether the specified provider is ready for read/write operations
     */
    public boolean isReady(final String providerId)
    {
        StorageProvider provider = getProvider(providerId);
        if (provider == null) {
            Timber.w("Storage-Provider \"%s\" does not exist", providerId);
            return false;
        }
        return provider.isReady(context);
    }

    /**
     * @return A map of available providers names, indexed by their ID. Never {@code null}.
     * @see StorageManager
     * @see StorageProvider#isSupported(Context)
     */
    public Map<String, String> getAvailableProviders()
    {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<String, StorageProvider> entry : mProviders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getName(context));
        }
        return result;
    }

    /**
     * @param path
     */
    public void onBeforeUnmount(final String path)
    {
        Timber.i("storage path \"%s\" unmounting", path);
        final StorageProvider provider = resolveProvider(path);
        if (provider == null) {
            return;
        }
        for (final StorageListener listener : mListeners) {
            try {
                listener.onUnmount(provider.getId());
            } catch (Exception e) {
                Timber.w(e, "Error while notifying StorageListener");
            }
        }
        final SynchronizationAid sync = mProviderLocks.get(resolveProvider(path));
        sync.writeLock.lock();
        sync.unmounting = true;
        sync.writeLock.unlock();
    }

    public void onAfterUnmount(final String path)
    {
        Timber.i("storage path \"%s\" unmounted", path);
        final StorageProvider provider = resolveProvider(path);
        if (provider == null) {
            return;
        }
        final SynchronizationAid sync = mProviderLocks.get(resolveProvider(path));
        sync.writeLock.lock();
        sync.unmounting = false;
        sync.writeLock.unlock();

        XryptoMail.setServicesEnabled(context);
    }

    /**
     * @param path
     * @param readOnly
     */
    public void onMount(final String path, final boolean readOnly)
    {
        Timber.i("storage path \"%s\" mounted readOnly=%s", path, readOnly);
        if (readOnly) {
            return;
        }

        final StorageProvider provider = resolveProvider(path);
        if (provider == null) {
            return;
        }
        for (final StorageListener listener : mListeners) {
            try {
                listener.onMount(provider.getId());
            } catch (Exception e) {
                Timber.w(e, "Error while notifying StorageListener");
            }
        }

        // XXX we should reset mail service ONLY if there are accounts using the storage
        // (this is not done in a regular listener because it has to be invoked afterward)
        XryptoMail.setServicesEnabled(context);
    }

    /**
     * @param path Never {@code null}.
     * @return The corresponding provider. {@code null} if no match.
     */
    protected StorageProvider resolveProvider(final String path)
    {
        for (final StorageProvider provider : mProviders.values()) {
            if (path.equals(provider.getRoot(context).getAbsolutePath())) {
                return provider;
            }
        }
        return null;
    }

    public void addListener(final StorageListener listener)
    {
        mListeners.add(listener);
    }

    public void removeListener(final StorageListener listener)
    {
        mListeners.remove(listener);
    }

    /**
     * Try to lock the underlying storage to prevent concurrent unmount.
     *
     * You must invoke {@link #unlockProvider(String)} when you're done with the storage.
     *
     * @param providerId
     * @throws UnavailableStorageException If the storage can't be locked.
     */
    public void lockProvider(final String providerId) throws UnavailableStorageException
    {
        final StorageProvider provider = getProvider(providerId);
        if (provider == null) {
            throw new UnavailableStorageException("StorageProvider not found: " + providerId);
        }
        // lock provider
        final SynchronizationAid sync = mProviderLocks.get(provider);
        final boolean locked = sync.readLock.tryLock();
        if (!locked || (locked && sync.unmounting)) {
            if (locked) {
                sync.readLock.unlock();
            }
            throw new UnavailableStorageException("StorageProvider is unmounting");
        }
        else if (locked && !provider.isReady(context)) {
            sync.readLock.unlock();
            throw new UnavailableStorageException("StorageProvider not ready");
        }
    }

    public void unlockProvider(final String providerId)
    {
        final StorageProvider provider = getProvider(providerId);
        final SynchronizationAid sync = mProviderLocks.get(provider);
        sync.readLock.unlock();
    }
}
