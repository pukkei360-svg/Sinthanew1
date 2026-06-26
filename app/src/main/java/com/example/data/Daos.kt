package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun getProfile(id: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getProfileSync(id: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Query("UPDATE user_profiles SET role = :role WHERE id = :id")
    suspend fun updateRole(id: String, role: String)

    @Query("UPDATE user_profiles SET isPro = :isPro, proExpiry = :proExpiry WHERE id = :id")
    suspend fun updatePro(id: String, isPro: Boolean, proExpiry: Long)

    @Query("UPDATE user_profiles SET isVerified = :isVerified WHERE id = :id")
    suspend fun updateVerified(id: String, isVerified: Boolean)

    @Query("UPDATE user_profiles SET isVerified = :isVerified, verificationStatus = :status, rejectionReason = :reason WHERE id = :id")
    suspend fun updateVerifiedWithStatus(id: String, isVerified: Boolean, status: String, reason: String)

    @Query("SELECT * FROM user_profiles")
    fun getAllProfiles(): Flow<List<UserProfile>>

    @Query("UPDATE user_profiles SET verificationStatus = :status, verificationIdDocument = :doc, verificationDocFileUri = :docUri, rejectionReason = :reason WHERE id = :id")
    suspend fun updateVerificationRequest(id: String, status: String, doc: String, docUri: String, reason: String)

    @Query("UPDATE user_profiles SET referralCount = referralCount + 1, balance = balance + 59.7 WHERE id = :id")
    suspend fun incrementReferrals(id: String)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY isPro DESC, rating DESC")
    fun getAllProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE category = :category ORDER BY isPro DESC, rating DESC")
    fun getProvidersByCategory(category: String): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY isPro DESC, rating DESC")
    fun searchProviders(query: String): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    fun getProviderById(id: String): Flow<ProviderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<ProviderEntity>)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE clientId = :clientId ORDER BY timestamp DESC")
    fun getBookingsForClient(clientId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE providerId = :providerId ORDER BY timestamp DESC")
    fun getBookingsForProvider(providerId: String): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity)

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Int, status: String)
}

@Dao
interface SavedProviderDao {
    @Query("SELECT * FROM providers INNER JOIN saved_providers ON providers.id = saved_providers.providerId WHERE saved_providers.clientId = :clientId")
    fun getSavedProvidersForClient(clientId: String): Flow<List<ProviderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProvider(saved: SavedProviderEntity)

    @Query("DELETE FROM saved_providers WHERE clientId = :clientId AND providerId = :providerId")
    suspend fun unsaveProvider(clientId: String, providerId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_providers WHERE clientId = :clientId AND providerId = :providerId)")
    fun isProviderSaved(clientId: String, providerId: String): Flow<Boolean>
}

@Dao
interface SavedAddressDao {
    @Query("SELECT * FROM saved_addresses ORDER BY id DESC")
    fun getAllAddresses(): Flow<List<SavedAddressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: SavedAddressEntity)

    @Query("DELETE FROM saved_addresses WHERE id = :id")
    suspend fun deleteAddress(id: Int)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE (senderId = :user1 AND receiverId = :user2) OR (senderId = :user2 AND receiverId = :user1) ORDER BY timestamp ASC")
    fun getChatMessages(user1: String, user2: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    fun getRecentChats(userId: String): Flow<List<ChatMessageEntity>>
}

@Dao
interface JobPostDao {
    @Query("SELECT * FROM job_posts ORDER BY timestamp DESC")
    fun getAllJobs(): Flow<List<JobPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobPostEntity)

    @Query("DELETE FROM job_posts WHERE id = :id")
    suspend fun deleteJob(id: Int)
}
