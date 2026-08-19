package com.mediavault.downloader.security

import com.mediavault.storage.db.dao.CookieDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieSecurityManager @Inject constructor(
    private val cookieDao: CookieDao
) {

    /**
     * Purga las cookies que no hayan sido utilizadas o actualizadas en más de [daysInactive] días.
     */
    suspend fun purgeExpiredCookies(daysInactive: Int = 30) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - (daysInactive * 86400_000L)
            val allCookies = cookieDao.getAll()
            var purgedCount = 0

            for (cookie in allCookies) {
                if (cookie.updatedAt < cutoff) {
                    cookieDao.deleteById(cookie.id)
                    purgedCount++
                }
            }
            if (purgedCount > 0) {
                Timber.tag("CookieSecurity").i("Se purgaron $purgedCount cookies inactivas por más de $daysInactive días.")
            }
        } catch (e: Exception) {
            Timber.tag("CookieSecurity").e(e, "Error durante la purga de cookies inactivas")
        }
    }

    /**
     * Limpia un búfer de caracteres sensible en memoria sobrescribiéndolo con ceros.
     */
    fun wipeSensitiveBuffer(buffer: CharArray) {
        Arrays.fill(buffer, '\u0000')
    }
}
