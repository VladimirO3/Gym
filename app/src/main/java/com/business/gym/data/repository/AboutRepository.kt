package com.business.gym.data.repository

import android.content.Context
import android.util.Log
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.GlobalInfoDao
import com.business.gym.data.local.entity.GlobalInfoEntity
import kotlinx.coroutines.flow.Flow

class AboutRepository(
    private val globalInfoDao: GlobalInfoDao,
    private val context: Context
) {
    private val apiService get() = NewsApiService.create(context)

    val globalInfo: Flow<GlobalInfoEntity?> = globalInfoDao.getGlobalInfo()

    suspend fun refreshInfo() {
        try {
            Log.d("AboutRepository", "Refreshing global info from VPS...")
            val response = apiService.getGlobalInfo()
            val entity = GlobalInfoEntity(
                aboutTitle = response.aboutTitle,
                aboutDescription = response.aboutDescription,
                aboutServices = response.aboutServices,
                aboutFooter = response.aboutFooter,
                contactTitle = response.contactTitle,
                contactPhone = response.contactPhone
            )
            globalInfoDao.insertGlobalInfo(entity)
            Log.d("AboutRepository", "Global info saved to Room")
        } catch (e: Exception) {
            Log.e("AboutRepository", "Failed to refresh global info: ${e.message}")
        }
    }

    suspend fun updateAboutTitle(v: String) = apiService.updateGlobalInfo(aboutTitle = v).also { refreshInfo() }
    suspend fun updateAboutDescription(v: String) = apiService.updateGlobalInfo(aboutDescription = v).also { refreshInfo() }
    suspend fun updateAboutServices(v: String) = apiService.updateGlobalInfo(aboutServices = v).also { refreshInfo() }
    suspend fun updateAboutFooter(v: String) = apiService.updateGlobalInfo(aboutFooter = v).also { refreshInfo() }
    suspend fun updateContactTitle(v: String) = apiService.updateGlobalInfo(contactTitle = v).also { refreshInfo() }
    suspend fun updateContactPhone(v: String) = apiService.updateGlobalInfo(contactPhone = v).also { refreshInfo() }
}
