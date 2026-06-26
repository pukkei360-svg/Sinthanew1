package com.example.data

import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase) {

    // User Profile
    fun getProfile(id: String): Flow<UserProfile?> = db.userProfileDao().getProfile(id)
    suspend fun getProfileSync(id: String): UserProfile? = db.userProfileDao().getProfileSync(id)
    suspend fun insertProfile(profile: UserProfile) = db.userProfileDao().insertProfile(profile)
    suspend fun updateRole(id: String, role: String) = db.userProfileDao().updateRole(id, role)
    suspend fun updatePro(id: String, isPro: Boolean, proExpiry: Long) = db.userProfileDao().updatePro(id, isPro, proExpiry)
    suspend fun updateVerified(id: String, isVerified: Boolean) = db.userProfileDao().updateVerified(id, isVerified)
    suspend fun updateVerifiedWithStatus(id: String, isVerified: Boolean, status: String, reason: String) = db.userProfileDao().updateVerifiedWithStatus(id, isVerified, status, reason)
    val allProfiles: Flow<List<UserProfile>> = db.userProfileDao().getAllProfiles()
    suspend fun updateVerificationRequest(id: String, status: String, doc: String, docUri: String, reason: String) = db.userProfileDao().updateVerificationRequest(id, status, doc, docUri, reason)
    suspend fun incrementReferrals(id: String) = db.userProfileDao().incrementReferrals(id)

    // Service Providers
    val allProviders: Flow<List<ProviderEntity>> = db.providerDao().getAllProviders()
    fun getProvidersByCategory(category: String): Flow<List<ProviderEntity>> = db.providerDao().getProvidersByCategory(category)
    fun searchProviders(query: String): Flow<List<ProviderEntity>> = db.providerDao().searchProviders(query)
    fun getProviderById(id: String): Flow<ProviderEntity?> = db.providerDao().getProviderById(id)
    suspend fun insertProvider(provider: ProviderEntity) = db.providerDao().insertProvider(provider)

    // Bookings
    fun getBookingsForClient(clientId: String): Flow<List<BookingEntity>> = db.bookingDao().getBookingsForClient(clientId)
    fun getBookingsForProvider(providerId: String): Flow<List<BookingEntity>> = db.bookingDao().getBookingsForProvider(providerId)
    suspend fun insertBooking(booking: BookingEntity) = db.bookingDao().insertBooking(booking)
    suspend fun updateBookingStatus(id: Int, status: String) = db.bookingDao().updateBookingStatus(id, status)

    // Saved Providers
    fun getSavedProviders(clientId: String): Flow<List<ProviderEntity>> = db.savedProviderDao().getSavedProvidersForClient(clientId)
    suspend fun saveProvider(clientId: String, providerId: String) = db.savedProviderDao().saveProvider(SavedProviderEntity(providerId, clientId))
    suspend fun unsaveProvider(clientId: String, providerId: String) = db.savedProviderDao().unsaveProvider(clientId, providerId)
    fun isProviderSaved(clientId: String, providerId: String): Flow<Boolean> = db.savedProviderDao().isProviderSaved(clientId, providerId)

    // Saved Addresses
    val allAddresses: Flow<List<SavedAddressEntity>> = db.savedAddressDao().getAllAddresses()
    suspend fun insertAddress(address: SavedAddressEntity) = db.savedAddressDao().insertAddress(address)
    suspend fun deleteAddress(id: Int) = db.savedAddressDao().deleteAddress(id)

    // Chats
    fun getMessages(user1: String, user2: String): Flow<List<ChatMessageEntity>> = db.chatDao().getChatMessages(user1, user2)
    suspend fun insertMessage(message: ChatMessageEntity) = db.chatDao().insertMessage(message)
    fun getRecentChats(userId: String): Flow<List<ChatMessageEntity>> = db.chatDao().getRecentChats(userId)

    // Jobs
    val allJobs: Flow<List<JobPostEntity>> = db.jobPostDao().getAllJobs()
    suspend fun insertJob(job: JobPostEntity) = db.jobPostDao().insertJob(job)
    suspend fun deleteJob(id: Int) = db.jobPostDao().deleteJob(id)
}
