package pos.finestar.barion.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "barion_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
    }

    val tokenFlow: Flow<String?> = context.sessionDataStore.data.map { it[Keys.TOKEN] }

    suspend fun currentToken(): String? = tokenFlow.first()

    suspend fun saveSession(token: String, userId: Long, username: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USERNAME] = username
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
        }
    }
}
