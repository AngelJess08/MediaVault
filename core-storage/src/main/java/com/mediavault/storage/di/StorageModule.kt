package com.mediavault.storage.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mediavault.storage.db.AppDatabase
import com.mediavault.storage.db.dao.*
import com.mediavault.storage.datastore.SettingsDataStore
import com.mediavault.storage.mediastore.MediaStoreHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediavault_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideQueueDao(database: AppDatabase): QueueDao = database.queueDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun provideUpscaleJobDao(database: AppDatabase): UpscaleJobDao = database.upscaleJobDao()

    @Provides
    fun provideCookieDao(database: AppDatabase): CookieDao = database.cookieDao()

    @Provides
    fun provideOrganizationRuleDao(database: AppDatabase): OrganizationRuleDao = database.organizationRuleDao()

    @Provides
    @Singleton
    fun provideMediaStoreHelper(@ApplicationContext context: Context): MediaStoreHelper {
        return MediaStoreHelper(context)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            "secret_session_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
