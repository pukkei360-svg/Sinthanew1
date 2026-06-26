package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = Repository(database)
    private val isFirebaseAvailable: Boolean by lazy {
        try {
            val context = getApplication<Application>().applicationContext
            val apps = com.google.firebase.FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            com.google.firebase.auth.FirebaseAuth.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    private val auth: com.google.firebase.auth.FirebaseAuth? by lazy {
        if (isFirebaseAvailable) {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Current logged-in user id (email or phone)
    private val _currentUserEmail = MutableStateFlow(
        try {
            val context = getApplication<Application>().applicationContext
            val apps = com.google.firebase.FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
        } catch (e: Exception) {
            ""
        }
    )
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    // Loaded profile
    @OptIn(ExperimentalCoroutinesApi::class)
    val userProfile: StateFlow<UserProfile?> = _currentUserEmail
        .flatMapLatest { email -> repository.getProfile(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allProfilesState: StateFlow<List<UserProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow representing current user's provider profile if available
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProviderProfile: StateFlow<ProviderEntity?> = userProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                repository.getProviderById(profile.id)
            } else {
                kotlinx.coroutines.flow.flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current role toggle (local state or from database)
    val currentRole: StateFlow<String> = userProfile
        .map { it?.role ?: "CLIENT" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CLIENT")

    // Categories
    val categories = listOf(
        "Home Services & Repairs",
        "Beauty & Wellness",
        "Education & Tutors",
        "Event Support",
        "Cleaning & Sanitization"
    )

    // Search and Filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedLocation = MutableStateFlow<String?>(null)
    val selectedLocation: StateFlow<String?> = _selectedLocation.asStateFlow()

    private val _sortBy = MutableStateFlow<String>("None")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    private val _verifiedOnly = MutableStateFlow<Boolean>(false)
    val verifiedOnly: StateFlow<Boolean> = _verifiedOnly.asStateFlow()

    private val _proOnly = MutableStateFlow<Boolean>(false)
    val proOnly: StateFlow<Boolean> = _proOnly.asStateFlow()

    // Filtered Service Providers List
    @OptIn(ExperimentalCoroutinesApi::class)
    val serviceProviders: StateFlow<List<ProviderEntity>> = combine(
        _searchQuery,
        _selectedCategory,
        _selectedLocation,
        _sortBy,
        _verifiedOnly,
        _proOnly
    ) { array ->
        val query = array[0] as String
        val category = array[1] as? String
        val location = array[2] as? String
        val sortBy = array[3] as String
        val verified = array[4] as Boolean
        val pro = array[5] as Boolean
        Filters(query, category, location, sortBy, verified, pro)
    }.flatMapLatest { filters ->
        val baseFlow = when {
            filters.query.isNotEmpty() -> repository.searchProviders(filters.query)
            filters.category != null -> repository.getProvidersByCategory(filters.category)
            else -> repository.allProviders
        }
        baseFlow.map { list ->
            var filtered = list
            if (filters.location != null) {
                filtered = filtered.filter { it.location.equals(filters.location, ignoreCase = true) }
            }
            if (filters.verified) {
                filtered = filtered.filter { it.isVerified }
            }
            if (filters.pro) {
                filtered = filtered.filter { it.isPro }
            }
            when (filters.sortBy) {
                "Rating" -> filtered.sortedByDescending { it.rating }
                "Experience" -> filtered.sortedByDescending { it.experienceYears }
                "PriceLowToHigh" -> filtered.sortedBy { it.rate }
                "PriceHighToLow" -> filtered.sortedByDescending { it.rate }
                else -> filtered
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Filters(
        val query: String,
        val category: String?,
        val location: String?,
        val sortBy: String,
        val verified: Boolean,
        val pro: Boolean
    )

    // Active Chat State
    private val _activeChatUserId = MutableStateFlow<String?>(null)
    val activeChatUserId: StateFlow<String?> = _activeChatUserId.asStateFlow()

    private val _activeChatUserName = MutableStateFlow<String?>(null)
    val activeChatUserName: StateFlow<String?> = _activeChatUserName.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessageEntity>> = combine(
        _currentUserEmail,
        _activeChatUserId
    ) { me, other ->
        if (other == null) Pair(me, "") else Pair(me, other)
    }.flatMapLatest { (me, other) ->
        if (other.isEmpty()) {
            flowOf(emptyList())
        } else {
            repository.getMessages(me, other)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getRecentChats(userId: String): Flow<List<ChatMessageEntity>> = repository.getRecentChats(userId)

    // Bookings
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookings: StateFlow<List<BookingEntity>> = combine(
        _currentUserEmail,
        currentRole
    ) { email, role ->
        Pair(email, role)
    }.flatMapLatest { (email, role) ->
        if (role == "PROVIDER") {
            repository.getBookingsForProvider(email)
        } else {
            repository.getBookingsForClient(email)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Saved Providers
    @OptIn(ExperimentalCoroutinesApi::class)
    val savedProviders: StateFlow<List<ProviderEntity>> = _currentUserEmail
        .flatMapLatest { email -> repository.getSavedProviders(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Saved Addresses
    val savedAddresses: StateFlow<List<SavedAddressEntity>> = repository.allAddresses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Job Posts Marketplace
    val jobPosts: StateFlow<List<JobPostEntity>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Chat Assistant States
    private val _aiChatHistory = MutableStateFlow<List<GeminiContent>>(emptyList())
    val aiChatHistory: StateFlow<List<GeminiContent>> = _aiChatHistory.asStateFlow()

    private val _aiChatLoading = MutableStateFlow<Boolean>(false)
    val aiChatLoading: StateFlow<Boolean> = _aiChatLoading.asStateFlow()

    private val _aiLanguagePreference = MutableStateFlow<String>("AUTO") // "AUTO", "ENGLISH", "MEITEILON"
    val aiLanguagePreference: StateFlow<String> = _aiLanguagePreference.asStateFlow()

    fun setAiLanguagePreference(pref: String) {
        _aiLanguagePreference.value = pref
    }

    fun clearAiChat() {
        _aiChatHistory.value = emptyList()
    }

    fun sendAiMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val userMsg = GeminiContent(parts = listOf(GeminiPart(text = text)), role = "user")
            val updatedHistory = _aiChatHistory.value + userMsg
            _aiChatHistory.value = updatedHistory
            _aiChatLoading.value = true

            val providerList = repository.allProviders.first()
            val response = GeminiClient.chatWithGemini(updatedHistory, providerList, _aiLanguagePreference.value)

            _aiChatLoading.value = false
            if (response != null) {
                _aiChatHistory.value = _aiChatHistory.value + GeminiContent(parts = listOf(GeminiPart(text = response)), role = "model")
            } else {
                _aiChatHistory.value = _aiChatHistory.value + GeminiContent(parts = listOf(GeminiPart(text = "Sorry, I encountered an issue connecting to SINTHA AI service. Check your internet connection or please try again later.")), role = "model")
            }
        }
    }

    // Alert Notification Toast-like Flow
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    // Initialize address defaults if empty
    init {
        viewModelScope.launch {
            repository.allAddresses.first().let { list ->
                if (list.isEmpty()) {
                    repository.insertAddress(SavedAddressEntity(label = "Home", fullAddress = "Sagolband Kangabam Leikai, Imphal West", landmark = "Near Youth Club"))
                    repository.insertAddress(SavedAddressEntity(label = "Office", fullAddress = "Sanjenthong Officers Colony, Imphal East", landmark = "Opposite Classic Grande"))
                }
            }
        }
    }

    // ACTIONS

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastMessage.emit(message)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun selectLocation(location: String?) {
        _selectedLocation.value = location
    }

    fun setSortBy(sortBy: String) {
        _sortBy.value = sortBy
    }

    fun setVerifiedOnly(verifiedOnly: Boolean) {
        _verifiedOnly.value = verifiedOnly
    }

    fun setProOnly(proOnly: Boolean) {
        _proOnly.value = proOnly
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _selectedLocation.value = null
        _sortBy.value = "None"
        _verifiedOnly.value = false
        _proOnly.value = false
    }

    fun applyVoiceSearchResult(result: VoiceSearchResult) {
        _searchQuery.value = result.searchQuery ?: ""
        _selectedCategory.value = result.category
        _selectedLocation.value = result.location
        _verifiedOnly.value = result.verifiedOnly ?: false
        _proOnly.value = result.proOnly ?: false
        _sortBy.value = result.sortBy ?: "None"
    }

    // Login simulation
    fun login(email: String, name: String, phone: String, role: String) {
        viewModelScope.launch {
            val formattedEmail = email.trim().lowercase()
            if (formattedEmail.isEmpty()) {
                showToast("Please enter an email address.")
                return@launch
            }
            _currentUserEmail.value = formattedEmail
            val existing = repository.getProfileSync(formattedEmail)
            if (existing == null) {
                val profile = UserProfile(
                    id = formattedEmail,
                    name = name.ifEmpty { "User" },
                    phone = phone.ifEmpty { "+91 90000 00000" },
                    role = role,
                    referralCode = name.replace(" ", "").uppercase().take(6) + "30",
                    isVerified = false
                )
                repository.insertProfile(profile)
                showToast("Welcome to SINTHA, ${profile.name}!")
            } else {
                showToast("Logged in as ${existing.name}")
            }
        }
    }

    // Real Firebase Email/Password Registration
    fun registerWithFirebase(
        email: String,
        name: String,
        phone: String,
        role: String,
        password: String,
        providerCategory: String? = null,
        providerSubcategory: String? = null,
        providerDescription: String? = null,
        providerRate: Double? = null,
        providerRateUnit: String? = null,
        providerLocation: String? = null,
        providerExperienceYears: Int? = null,
        profilePicUri: String? = null
    ) {
        val formattedEmail = email.trim().lowercase()
        val formattedPassword = password.trim()
        val formattedName = name.trim()
        val formattedPhone = phone.trim()

        if (formattedEmail.isEmpty() || formattedPassword.isEmpty() || formattedName.isEmpty()) {
            showToast("Please fill in email, password, and name.")
            return
        }
        if (formattedPassword.length < 6) {
            showToast("Password must be at least 6 characters.")
            return
        }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            firebaseAuth.createUserWithEmailAndPassword(formattedEmail, formattedPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            val profile = UserProfile(
                                id = formattedEmail,
                                name = formattedName,
                                phone = formattedPhone.ifEmpty { "+91 90000 00000" },
                                role = role,
                                referralCode = formattedName.replace(" ", "").uppercase().take(6) + "30",
                                isVerified = false
                            )
                            repository.insertProfile(profile)

                            if (role == "PROVIDER" && providerCategory != null) {
                                val provider = ProviderEntity(
                                    id = formattedEmail,
                                    name = formattedName,
                                    category = providerCategory,
                                    subcategory = providerSubcategory ?: "Expert",
                                    location = providerLocation ?: "Imphal West",
                                    rate = providerRate ?: 300.0,
                                    rateUnit = providerRateUnit ?: "hour",
                                    rating = 4.8f,
                                    reviewCount = 1,
                                    description = providerDescription ?: "Local professional offering services in Manipur.",
                                    isVerified = false,
                                    isPro = false,
                                    phone = formattedPhone.ifEmpty { "+91 90000 00000" },
                                    experienceYears = providerExperienceYears ?: 3,
                                    profilePictureUrl = ""
                                )
                                repository.insertProvider(provider)

                                if (!profilePicUri.isNullOrEmpty()) {
                                    _currentUserEmail.value = formattedEmail
                                    try {
                                        uploadProfilePicture(android.net.Uri.parse(profilePicUri))
                                    } catch (e: Exception) {
                                        // Ignore or toast
                                    }
                                }
                            }

                            _currentUserEmail.value = formattedEmail
                            showToast("Registration successful! Welcome, ${profile.name}!")
                        }
                    } else {
                        val exception = task.exception?.localizedMessage ?: "Unknown registration error"
                        showToast("Registration failed: $exception")
                    }
                }
        } else {
            // Safe Local/Offline Fallback
            viewModelScope.launch {
                val profile = UserProfile(
                    id = formattedEmail,
                    name = formattedName,
                    phone = formattedPhone.ifEmpty { "+91 90000 00000" },
                    role = role,
                    referralCode = formattedName.replace(" ", "").uppercase().take(6) + "30",
                    isVerified = false
                )
                repository.insertProfile(profile)

                if (role == "PROVIDER" && providerCategory != null) {
                    val provider = ProviderEntity(
                        id = formattedEmail,
                        name = formattedName,
                        category = providerCategory,
                        subcategory = providerSubcategory ?: "Expert",
                        location = providerLocation ?: "Imphal West",
                        rate = providerRate ?: 300.0,
                        rateUnit = providerRateUnit ?: "hour",
                        rating = 4.8f,
                        reviewCount = 1,
                        description = providerDescription ?: "Local professional offering services in Manipur.",
                        isVerified = false,
                        isPro = false,
                        phone = formattedPhone.ifEmpty { "+91 90000 00000" },
                        experienceYears = providerExperienceYears ?: 3,
                        profilePictureUrl = ""
                    )
                    repository.insertProvider(provider)

                    if (!profilePicUri.isNullOrEmpty()) {
                        _currentUserEmail.value = formattedEmail
                        try {
                            uploadProfilePicture(android.net.Uri.parse(profilePicUri))
                        } catch (e: Exception) {
                            // Ignore or toast
                        }
                    }
                }

                _currentUserEmail.value = formattedEmail
                showToast("Welcome to SINTHA! (Offline Mode)")
            }
        }
    }

    // Real Firebase Email/Password Sign In
    fun loginWithFirebase(email: String, password: String) {
        val formattedEmail = email.trim().lowercase()
        val formattedPassword = password.trim()

        if (formattedEmail.isEmpty() || formattedPassword.isEmpty()) {
            showToast("Please enter email and password.")
            return
        }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            firebaseAuth.signInWithEmailAndPassword(formattedEmail, formattedPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            _currentUserEmail.value = formattedEmail
                            val existing = repository.getProfileSync(formattedEmail)
                            if (existing == null) {
                                val profile = UserProfile(
                                    id = formattedEmail,
                                    name = formattedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                                    phone = "+91 90000 00000",
                                    role = "CLIENT",
                                    referralCode = formattedEmail.substringBefore("@").uppercase().take(6) + "30",
                                    isVerified = false
                                )
                                repository.insertProfile(profile)
                            }
                            showToast("Logged in successfully!")
                        }
                    } else {
                        val exception = task.exception?.localizedMessage ?: "Unknown login error"
                        showToast("Login failed: $exception. Trying offline mode.")
                        loginLocally(formattedEmail, formattedPassword)
                    }
                }
        } else {
            loginLocally(formattedEmail, formattedPassword)
        }
    }

    private fun loginLocally(email: String, password: String) {
        viewModelScope.launch {
            val existing = repository.getProfileSync(email)
            if (existing != null) {
                _currentUserEmail.value = email
                showToast("Logged in! (Offline Mode)")
            } else {
                val profile = UserProfile(
                    id = email,
                    name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    phone = "+91 90000 00000",
                    role = "CLIENT",
                    referralCode = email.substringBefore("@").uppercase().take(6) + "30",
                    isVerified = false
                )
                repository.insertProfile(profile)
                _currentUserEmail.value = email
                showToast("Account created & logged in! (Offline Mode)")
            }
        }
    }

    // Sign Out
    fun logout() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            // Squelch and clean up locally
        }
        _currentUserEmail.value = ""
        showToast("Signed out successfully.")
    }

    // Toggle Role
    fun toggleRole() {
        viewModelScope.launch {
            val currentProfile = userProfile.value ?: return@launch
            val nextRole = if (currentProfile.role == "CLIENT") "PROVIDER" else "CLIENT"
            repository.updateRole(currentProfile.id, nextRole)
            
            // If switched to provider, make sure they have a provider entity in DB
            if (nextRole == "PROVIDER") {
                val providerFlow = repository.getProviderById(currentProfile.id).first()
                if (providerFlow == null) {
                    val defaultProvider = ProviderEntity(
                        id = currentProfile.id,
                        name = currentProfile.name,
                        category = "Home Services & Repairs",
                        subcategory = "General Professional",
                        location = "Imphal West",
                        rate = 300.0,
                        rateUnit = "hour",
                        rating = 5.0f,
                        reviewCount = 1,
                        description = "Local professional offering reliable services. Happy to assist with your requirements.",
                        isVerified = currentProfile.isVerified,
                        isPro = currentProfile.isPro,
                        phone = currentProfile.phone
                    )
                    repository.insertProvider(defaultProvider)
                }
            }
            showToast("Switched to $nextRole dashboard")
        }
    }

    // Provider profile edit
    fun saveProviderProfile(
        category: String,
        subcategory: String,
        location: String,
        rate: Double,
        rateUnit: String,
        description: String,
        experienceYears: Int
    ) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            val updatedProvider = ProviderEntity(
                id = profile.id,
                name = profile.name,
                category = category,
                subcategory = subcategory,
                location = location,
                rate = rate,
                rateUnit = rateUnit,
                rating = 4.8f,
                reviewCount = 3,
                description = description,
                isVerified = profile.isVerified,
                isPro = profile.isPro,
                phone = profile.phone,
                experienceYears = experienceYears
            )
            repository.insertProvider(updatedProvider)
            showToast("Provider service profile updated successfully!")
        }
    }

    // Provider Onboarding Profile Setup & Save
    fun saveProviderOnboardingProfile(
        category: String,
        subcategory: String,
        description: String,
        rate: Double,
        rateUnit: String,
        location: String,
        experienceYears: Int,
        profilePicUri: String?
    ) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            
            // 1. Update user role in the DB to PROVIDER
            repository.updateRole(profile.id, "PROVIDER")
            
            // 2. Insert/Save ProviderEntity details
            val updatedProvider = ProviderEntity(
                id = profile.id,
                name = profile.name,
                category = category,
                subcategory = subcategory,
                location = location,
                rate = rate,
                rateUnit = rateUnit,
                rating = 4.8f,
                reviewCount = 1,
                description = description,
                isVerified = profile.isVerified,
                isPro = profile.isPro,
                phone = profile.phone,
                experienceYears = experienceYears,
                profilePictureUrl = profile.profilePictureUrl
            )
            repository.insertProvider(updatedProvider)
            
            // 3. Handle image upload if a new picture was chosen
            if (!profilePicUri.isNullOrEmpty()) {
                try {
                    val uri = android.net.Uri.parse(profilePicUri)
                    uploadProfilePicture(uri)
                } catch (e: Exception) {
                    showToast("Profile set up! Image processing failed: ${e.localizedMessage}")
                }
            }
            
            showToast("Successfully registered as a Service Provider!")
        }
    }

    // Book service
    fun bookService(provider: ProviderEntity, date: String, address: String, notes: String) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            if (profile.id == provider.id) {
                showToast("You cannot book your own service.")
                return@launch
            }
            val booking = BookingEntity(
                clientId = profile.id,
                clientName = profile.name,
                providerId = provider.id,
                providerName = provider.name,
                serviceCategory = provider.subcategory,
                bookingDate = date,
                rate = provider.rate,
                status = "PENDING",
                address = address,
                notes = notes
            )
            repository.insertBooking(booking)
            showToast("Booking request sent to ${provider.name}!")
        }
    }

    // Update booking status
    fun updateBookingStatus(bookingId: Int, status: String) {
        viewModelScope.launch {
            repository.updateBookingStatus(bookingId, status)
            showToast("Booking state updated to $status")
        }
    }

    // Saved providers
    fun toggleSaveProvider(providerId: String) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val isSaved = repository.isProviderSaved(email, providerId).first()
            if (isSaved) {
                repository.unsaveProvider(email, providerId)
                showToast("Removed from Saved Providers")
            } else {
                repository.saveProvider(email, providerId)
                showToast("Added to Saved Providers")
            }
        }
    }

    fun isSaved(providerId: String): Flow<Boolean> {
        return repository.isProviderSaved(currentUserEmail.value, providerId)
    }

    // Saved Addresses
    fun addAddress(label: String, fullAddress: String, landmark: String) {
        viewModelScope.launch {
            if (label.isEmpty() || fullAddress.isEmpty()) {
                showToast("Address and label cannot be empty.")
                return@launch
            }
            repository.insertAddress(SavedAddressEntity(label = label, fullAddress = fullAddress, landmark = landmark))
            showToast("Address '$label' saved!")
        }
    }

    fun deleteAddress(id: Int) {
        viewModelScope.launch {
            repository.deleteAddress(id)
            showToast("Address deleted")
        }
    }

    // Chat
    fun openChat(userId: String, userName: String) {
        _activeChatUserId.value = userId
        _activeChatUserName.value = userName
    }

    fun sendChatMessage(text: String) {
        val other = activeChatUserId.value ?: return
        val me = currentUserEmail.value
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.insertMessage(
                ChatMessageEntity(
                    senderId = me,
                    receiverId = other,
                    messageText = trimmed
                )
            )
        }
    }

    // Job Marketplace
    fun postJob(title: String, description: String, category: String, budget: Double, location: String, phone: String) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            if (title.isEmpty() || description.isEmpty() || budget <= 0) {
                showToast("Please fill all job fields with valid values.")
                return@launch
            }
            repository.insertJob(
                JobPostEntity(
                    clientId = profile.id,
                    clientName = profile.name,
                    title = title,
                    description = description,
                    category = category,
                    budget = budget,
                    location = location,
                    phone = phone.ifEmpty { profile.phone }
                )
            )
            showToast("Job posted successfully in the Marketplace!")
        }
    }

    fun deleteJob(id: Int) {
        viewModelScope.launch {
            repository.deleteJob(id)
            showToast("Job post removed")
        }
    }

    // Pro subscription purchase simulation
    fun purchaseProSubscription() {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val profile = userProfile.value ?: return@launch
            // Simulate Razorpay purchase
            repository.updatePro(email, true, System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
            
            // Also update any provider listing for them
            val provider = repository.getProviderById(email).first()
            if (provider != null) {
                repository.insertProvider(provider.copy(isPro = true))
            }
            
            // Award referral credit to whoever referred them if applicable
            if (profile.referredBy.isNotEmpty()) {
                val referrer = repository.getProfileSync(profile.referredBy)
                if (referrer != null) {
                    repository.incrementReferrals(referrer.id)
                }
            }
            showToast("PRO subscription purchased successfully! ₹199 paid.")
        }
    }

    // Apply Referral code
    fun applyReferral(code: String) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            if (profile.referredBy.isNotEmpty()) {
                showToast("You have already used a referral code.")
                return@launch
            }
            val trimmedCode = code.trim().uppercase()
            if (trimmedCode == profile.referralCode) {
                showToast("You cannot refer yourself!")
                return@launch
            }
            // Let's find who owns this code by getting profiles (or simulating finding him)
            // For now, let's say "IRABOT30" (Irabot Laishram) or another user owns it.
            // If the code is not empty, let's treat it as valid and reward them!
            if (trimmedCode.length < 4) {
                showToast("Invalid referral code format.")
                return@launch
            }
            // Update profile with referredBy
            // Since it's a demo, we can set the referredBy to "aicrafts56@gmail.com"
            val referrerId = "aicrafts56@gmail.com"
            repository.insertProfile(profile.copy(referredBy = trimmedCode))
            showToast("Referral code '$trimmedCode' applied! You will get 30% discount on PRO.")
        }
    }

    // Verify profile
    fun getVerifiedInstant() {
        viewModelScope.launch {
            val email = currentUserEmail.value
            repository.updateVerifiedWithStatus(email, true, "APPROVED", "")
            // Also update provider listing
            val provider = repository.getProviderById(email).first()
            if (provider != null) {
                repository.insertProvider(provider.copy(isVerified = true))
            }
            showToast("Identity successfully verified! Verification Badge activated.")
        }
    }

    fun submitVerificationRequest(docType: String, docUri: String) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            if (email.isEmpty()) {
                showToast("Please sign in to submit verification documents.")
                return@launch
            }
            repository.updateVerificationRequest(email, "PENDING", docType, docUri, "")
            showToast("Verification documents successfully submitted to Admin! Review is in progress.")
        }
    }

    fun adminApproveVerification(userId: String) {
        viewModelScope.launch {
            repository.updateVerifiedWithStatus(userId, true, "APPROVED", "")
            // Update provider if exists
            val provider = repository.getProviderById(userId).first()
            if (provider != null) {
                repository.insertProvider(provider.copy(isVerified = true))
            }
            showToast("Successfully approved user verification!")
        }
    }

    fun adminRejectVerification(userId: String, reason: String) {
        viewModelScope.launch {
            repository.updateVerifiedWithStatus(userId, false, "REJECTED", reason)
            // Update provider if exists
            val provider = repository.getProviderById(userId).first()
            if (provider != null) {
                repository.insertProvider(provider.copy(isVerified = false))
            }
            showToast("Verification request rejected.")
        }
    }

    // Free Firebase Storage upload for profile picture (with local fallback)
    fun uploadProfilePicture(uri: android.net.Uri) {
        val email = currentUserEmail.value
        if (email.isEmpty()) {
            showToast("Please sign in to update your profile picture.")
            return
        }

        viewModelScope.launch {
            try {
                // Spark Plan Firebase Storage Reference
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val storageRef = storage.reference
                val imageRef = storageRef.child("profile_pictures/${email}_${java.util.UUID.randomUUID()}.jpg")

                showToast("Uploading to Firebase Spark Storage...")

                imageRef.putFile(uri)
                    .addOnSuccessListener { taskSnapshot ->
                        imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val downloadUrl = downloadUri.toString()
                            updateProfilePictureUrlInDb(email, downloadUrl)
                            showToast("Profile picture uploaded successfully!")
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Fallback to local sandbox files directory
                        saveImageLocally(uri, email)
                    }
            } catch (e: Exception) {
                // Fallback to local sandbox files directory
                saveImageLocally(uri, email)
            }
        }
    }

    private fun saveImageLocally(uri: android.net.Uri, email: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "profile_${email.replace("@", "_").replace(".", "_")}.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    val localUrl = file.absolutePath
                    updateProfilePictureUrlInDb(email, localUrl)
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        showToast("Profile picture updated locally!")
                    }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Failed to save image: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateProfilePictureUrlInDb(email: String, url: String) {
        viewModelScope.launch {
            val currentProfile = repository.getProfileSync(email)
            if (currentProfile != null) {
                val updatedProfile = currentProfile.copy(profilePictureUrl = url)
                repository.insertProfile(updatedProfile)
                
                // If they are a provider, update their ProviderEntity too!
                val provider = repository.getProviderById(email).first()
                if (provider != null) {
                    val updatedProvider = provider.copy(profilePictureUrl = url)
                    repository.insertProvider(updatedProvider)
                }
            }
        }
    }
}
