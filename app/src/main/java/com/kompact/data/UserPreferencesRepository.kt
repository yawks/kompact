package com.kompact.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val defaultDestinationFolderKey = stringPreferencesKey("default_destination_folder")

    val defaultDestinationFolder: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[defaultDestinationFolderKey]
    }

    suspend fun setDefaultDestinationFolder(uriString: String?) {
        context.dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[defaultDestinationFolderKey] = uriString
            } else {
                preferences.remove(defaultDestinationFolderKey)
            }
        }
    }
}