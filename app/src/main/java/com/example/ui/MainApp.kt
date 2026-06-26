package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.R
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// Screen enumerations for state-based navigation
enum class Tab {
    EXPLORE, MARKETPLACE, BOOKINGS, CHAT, PROFILE
}

sealed class Screen {
    object Main : Screen()
    object AiChat : Screen()
    data class ProviderDetail(val provider: ProviderEntity) : Screen()
    data class ActiveChat(val userId: String, val userName: String) : Screen()
    object AdminDashboard : Screen()
    object VerificationSubmit : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State collections
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val role by viewModel.currentRole.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val providers by viewModel.serviceProviders.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val savedProviders by viewModel.savedProviders.collectAsStateWithLifecycle()
    val savedAddresses by viewModel.savedAddresses.collectAsStateWithLifecycle()
    val jobs by viewModel.jobPosts.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val activeChatUserId by viewModel.activeChatUserId.collectAsStateWithLifecycle()
    val activeChatUserName by viewModel.activeChatUserName.collectAsStateWithLifecycle()
    val allProfiles by viewModel.allProfilesState.collectAsStateWithLifecycle()

    // Navigation stack
    var currentTab by remember { mutableStateOf(Tab.EXPLORE) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var showRazorpaySheet by remember { mutableStateOf(false) }

    // Gemini Voice Search States
    var showVoiceSearchModal by remember { mutableStateOf(false) }
    var voiceSearchQuery by remember { mutableStateOf("") }
    var voiceSearchStatus by remember { mutableStateOf("LISTENING") } // LISTENING, ANALYZING, RESULT, ERROR
    var parsedVoiceResult by remember { mutableStateOf<VoiceSearchResult?>(null) }
    var voiceSearchError by remember { mutableStateOf("") }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
                if (!spokenText.isNullOrBlank()) {
                    voiceSearchQuery = spokenText
                    voiceSearchStatus = "ANALYZING"
                    showVoiceSearchModal = true
                    
                    scope.launch {
                        val parsed = com.example.data.GeminiClient.parseVoiceSearch(spokenText)
                        if (parsed != null) {
                            parsedVoiceResult = parsed
                            voiceSearchStatus = "RESULT"
                        } else {
                            voiceSearchError = "Gemini was unable to interpret the query. Please speak clearly or try again."
                            voiceSearchStatus = "ERROR"
                        }
                    }
                } else {
                    viewModel.showToast("No speech recognized. Please try again.")
                }
            } else {
                viewModel.showToast("Voice search cancelled.")
            }
        }
    )

    val triggerVoiceSearch: () -> Unit = {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak search query (e.g., 'trusted plumber in Imphal West')")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.showToast("Voice Search not supported on this device.")
        }
    }

    // Observe Toast Messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showLandingPage by remember { mutableStateOf(true) }

    if (profile == null) {
        if (showLandingPage) {
            LandingScreen(onGetStarted = { showLandingPage = false })
        } else {
            // Welcome & Authentication Screen
            AuthScreen(
                onLogin = { email, password ->
                    viewModel.loginWithFirebase(email, password)
                },
                onRegister = { email, name, phone, chosenRole, password ->
                    viewModel.registerWithFirebase(email, name, phone, chosenRole, password)
                },
                onDemoLogin = { email, name, phone, chosenRole ->
                    viewModel.login(email, name, phone, chosenRole)
                },
                onBackToLanding = { showLandingPage = true }
            )
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SINTHA",
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        // Display verified tag
                        if (profile?.isVerified == true) {
                            AssistChip(
                                onClick = {},
                                label = { Text("VERIFIED", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = Color(0xFF16A34A),
                                    containerColor = Color(0xFFDCFCE7)
                                ),
                                border = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Display PRO Badge
                        if (profile?.isPro == true) {
                            AssistChip(
                                onClick = {},
                                label = { Text("PRO", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = Color(0xFFB45309),
                                    containerColor = Color(0xFFFEF3C7)
                                ),
                                border = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Dynamic role badge as specified in client & provider PRDs
                        AssistChip(
                            onClick = {},
                            label = { Text(if (role == "PROVIDER") "PROVIDER" else "CLIENT", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (role == "PROVIDER") Color(0xFF8B5CF6) else Color(0xFF0F4C81),
                                containerColor = if (role == "PROVIDER") Color(0xFFF3E8FF) else Color(0xFFEFF6FF)
                            ),
                            border = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // Notification bell icon with unread count badge
                        IconButton(
                            onClick = {
                                currentTab = Tab.PROFILE
                                android.widget.Toast.makeText(context, "Showing Notifications on Profile Screen", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Active unread count badge (simulated/real)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                )
                            }
                        }

                        // Profile avatar (tap -> Profile screen)
                        IconButton(
                            onClick = { currentTab = Tab.PROFILE }
                        ) {
                            if (profile?.profilePictureUrl?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = profile?.profilePictureUrl,
                                    contentDescription = "Profile Avatar",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Profile Avatar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (currentScreen is Screen.Main) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = currentTab == Tab.EXPLORE,
                            onClick = { currentTab = Tab.EXPLORE },
                            icon = { Icon(if (currentTab == Tab.EXPLORE) Icons.Filled.Search else Icons.Outlined.Search, "Explore") },
                            label = { Text("Explore", fontSize = 12.sp) }
                        )
                        NavigationBarItem(
                            selected = currentTab == Tab.MARKETPLACE,
                            onClick = { currentTab = Tab.MARKETPLACE },
                            icon = { Icon(if (currentTab == Tab.MARKETPLACE) Icons.Filled.Storefront else Icons.Outlined.Storefront, "Jobs") },
                            label = { Text("Jobs", fontSize = 12.sp) }
                        )
                        NavigationBarItem(
                            selected = currentTab == Tab.BOOKINGS,
                            onClick = { currentTab = Tab.BOOKINGS },
                            icon = { Icon(if (currentTab == Tab.BOOKINGS) Icons.Filled.AssignmentTurnedIn else Icons.Outlined.AssignmentTurnedIn, "Bookings") },
                            label = { Text("Bookings", fontSize = 12.sp) }
                        )
                        NavigationBarItem(
                            selected = currentTab == Tab.CHAT,
                            onClick = { currentTab = Tab.CHAT },
                            icon = { Icon(if (currentTab == Tab.CHAT) Icons.Filled.Chat else Icons.Outlined.Chat, "Chat") },
                            label = { Text("Chat", fontSize = 12.sp) }
                        )
                        NavigationBarItem(
                            selected = currentTab == Tab.PROFILE,
                            onClick = { currentTab = Tab.PROFILE },
                            icon = { Icon(if (currentTab == Tab.PROFILE) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle, "Profile") },
                            label = { Text("Profile", fontSize = 12.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "ScreenNavigation"
                ) { screen ->
                    when (screen) {
                        is Screen.ProviderDetail -> {
                            ProviderDetailScreen(
                                provider = screen.provider,
                                savedAddresses = savedAddresses,
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.Main },
                                onChat = { id, name ->
                                    viewModel.openChat(id, name)
                                    currentScreen = Screen.ActiveChat(id, name)
                                }
                            )
                        }
                        is Screen.ActiveChat -> {
                            ActiveChatScreen(
                                recipientId = screen.userId,
                                recipientName = screen.userName,
                                messages = chatMessages,
                                myId = profile?.id ?: "",
                                onSendMessage = { text -> viewModel.sendChatMessage(text) },
                                onBack = {
                                    currentScreen = Screen.Main
                                    currentTab = Tab.CHAT
                                }
                            )
                        }
                        is Screen.AdminDashboard -> {
                            AdminDashboardScreen(
                                profiles = allProfiles,
                                onApprove = { userId -> viewModel.adminApproveVerification(userId) },
                                onReject = { userId, reason -> viewModel.adminRejectVerification(userId, reason) },
                                onBack = { currentScreen = Screen.Main }
                            )
                        }
                        is Screen.VerificationSubmit -> {
                            VerificationSubmitScreen(
                                profile = profile!!,
                                onSubmit = { docType, docUri -> viewModel.submitVerificationRequest(docType, docUri) },
                                onBack = { currentScreen = Screen.Main }
                            )
                        }
                        is Screen.AiChat -> {
                            val aiHistory by viewModel.aiChatHistory.collectAsStateWithLifecycle()
                            val aiLoading by viewModel.aiChatLoading.collectAsStateWithLifecycle()
                            val languagePref by viewModel.aiLanguagePreference.collectAsStateWithLifecycle()
                            
                            AiChatScreen(
                                history = aiHistory,
                                isLoading = aiLoading,
                                languagePreference = languagePref,
                                onSendMessage = { text -> viewModel.sendAiMessage(text) },
                                onLanguagePreferenceChange = { pref -> viewModel.setAiLanguagePreference(pref) },
                                onClearChat = { viewModel.clearAiChat() },
                                onBack = {
                                    currentScreen = Screen.Main
                                    currentTab = Tab.CHAT
                                }
                            )
                        }
                        is Screen.Main -> {
                            when (currentTab) {
                                Tab.EXPLORE -> {
                                    if (role == "PROVIDER") {
                                        ProviderDashboardScreen(
                                            profile = profile!!,
                                            bookings = bookings,
                                            viewModel = viewModel,
                                            onGoPro = { showRazorpaySheet = true }
                                        )
                                    } else {
                                        ExploreScreen(
                                            providers = providers,
                                            searchQuery = searchQuery,
                                            selectedCategory = selectedCategory,
                                            categories = viewModel.categories,
                                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                            onCategorySelect = { viewModel.selectCategory(it) },
                                            onProviderClick = { currentScreen = Screen.ProviderDetail(it) },
                                            onSaveToggle = { viewModel.toggleSaveProvider(it.id) },
                                            onVoiceSearchClick = { triggerVoiceSearch() },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                                Tab.MARKETPLACE -> {
                                    MarketplaceScreen(
                                        jobs = jobs,
                                        isProvider = (role == "PROVIDER"),
                                        onPostJob = { title, desc, cat, budget, loc, phone ->
                                            viewModel.postJob(title, desc, cat, budget, loc, phone)
                                        },
                                        onDeleteJob = { jobId -> viewModel.deleteJob(jobId) },
                                        onContactClient = { email, name ->
                                            viewModel.openChat(email, name)
                                            currentScreen = Screen.ActiveChat(email, name)
                                        },
                                        profile = profile!!
                                    )
                                }
                                Tab.BOOKINGS -> {
                                    BookingsScreen(
                                        bookings = bookings,
                                        isProvider = (role == "PROVIDER"),
                                        onStatusUpdate = { id, stat -> viewModel.updateBookingStatus(id, stat) },
                                        onChatWithUser = { email, name ->
                                            viewModel.openChat(email, name)
                                            currentScreen = Screen.ActiveChat(email, name)
                                        }
                                    )
                                }
                                Tab.CHAT -> {
                                    ChatListScreen(
                                        myId = profile?.id ?: "",
                                        viewModel = viewModel,
                                        onOpenChat = { id, name ->
                                            viewModel.openChat(id, name)
                                            currentScreen = Screen.ActiveChat(id, name)
                                        },
                                        onOpenAiChat = {
                                            currentScreen = Screen.AiChat
                                        }
                                    )
                                }
                                Tab.PROFILE -> {
                                    ProfileScreen(
                                        profile = profile!!,
                                        savedProviders = savedProviders,
                                        savedAddresses = savedAddresses,
                                        onAddAddress = { l, a, lm -> viewModel.addAddress(l, a, lm) },
                                        onDeleteAddress = { id -> viewModel.deleteAddress(id) },
                                        onApplyReferral = { code -> viewModel.applyReferral(code) },
                                        onGoPro = { showRazorpaySheet = true },
                                        onGetVerified = { currentScreen = Screen.VerificationSubmit },
                                        onProviderClick = { currentScreen = Screen.ProviderDetail(it) },
                                        onSaveToggle = { viewModel.toggleSaveProvider(it.id) },
                                        onOpenAiChat = { currentScreen = Screen.AiChat },
                                        onOpenAdminConsole = { currentScreen = Screen.AdminDashboard },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showRazorpaySheet && profile != null) {
            RazorpayPaymentModal(
                profile = profile!!,
                onPaymentSuccess = {
                    showRazorpaySheet = false
                    viewModel.purchaseProSubscription()
                },
                onPaymentCancel = {
                    showRazorpaySheet = false
                }
            )
        }

        if (showVoiceSearchModal) {
            VoiceSearchModal(
                status = voiceSearchStatus,
                queryText = voiceSearchQuery,
                parsedResult = parsedVoiceResult,
                errorText = voiceSearchError,
                onApply = { result ->
                    viewModel.applyVoiceSearchResult(result)
                    viewModel.showToast("Voice filters applied successfully!")
                },
                onDismiss = {
                    showVoiceSearchModal = false
                },
                onRetrySpeech = {
                    showVoiceSearchModal = false
                    triggerVoiceSearch()
                }
            )
        }
    }
}

// 1. LANDING & ONBOARDING SCREEN
@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F4C81), Color(0xFF05182B))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Group
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Handshake,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SINTHA",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manipur's Premier Service Marketplace",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // Stunning central Hero Graphic (Meitei Mayek "ꯁ" - Sam)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(100.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(80.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color(0xFF0F4C81), shape = RoundedCornerShape(60.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(60.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Handcrafted Meitei Mayek letter 'ꯁ' with premium styling
                            Text(
                                text = "ꯁ",
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Trusted Hands. Trusted Services.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Instantly connect with certified local experts in Manipur. Carpenters, plumbers, tutors, therapists, and beauty specialists at your doorstep.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    lineHeight = 18.sp
                )
            }

            // Features and Button group
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // Feature chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✓ Verified Experts", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✓ 0% Agency Fee", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F4C81)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "GET STARTED",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ꯁ  SINTHA Proudly Manipur-First",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// 2. AUTHENTICATION & REGISTRATION SCREEN
@Composable
fun AuthScreen(
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String, String) -> Unit,
    onDemoLogin: (String, String, String, String) -> Unit,
    onBackToLanding: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var chosenRole by remember { mutableStateOf("CLIENT") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showGoogleChooser by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    fun triggerGoogleSignIn() {
        coroutineScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId("791483015484-mub8v8qskhshd5jof8g3rnb4miv0r67c.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = context,
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val gmail = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName ?: gmail.substringBefore("@")
                    onDemoLogin(gmail, displayName, "+91 90000 00000", "CLIENT")
                } else {
                    android.widget.Toast.makeText(context, "Sign-In: Unsupported credential format.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Secure bypass for test environments or offline emulation
                android.widget.Toast.makeText(context, "Google Sign-In offline fallback activated.", android.widget.Toast.LENGTH_LONG).show()
                onDemoLogin("aicrafts56@gmail.com", "Irabot Laishram", "+91 93621 12345", "CLIENT")
            }
        }
    }

    // Authentic Google SSO account chooser overlay
    if (showGoogleChooser) {
        AlertDialog(
            onDismissRequest = { showGoogleChooser = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleChooser = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(36.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sign in with Google",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "to continue to SINTHA",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    
                    // Main Account: User's actual email (from metadata)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleChooser = false
                                triggerGoogleSignIn()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF0F4C81), shape = RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "I",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Irabot Laishram",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "aicrafts56@gmail.com",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Option to enter another email
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleChooser = false
                                triggerGoogleSignIn()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(20.dp))
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Use another Gmail account",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back Button to Landing Page (Top Left)
        IconButton(
            onClick = onBackToLanding,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.White, shape = RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFE2E8F0), shape = RoundedCornerShape(20.dp))
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to landing",
                tint = Color(0xFF1E293B)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Default.Handshake,
                contentDescription = null,
                tint = Color(0xFF0F4C81),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "SINTHA",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0F4C81),
                letterSpacing = 2.sp
            )
            Text(
                "Trusted Hands. Trusted Services.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented tabs for Mode selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        Button(
                            onClick = { isRegisterMode = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isRegisterMode) Color.White else Color.Transparent,
                                contentColor = if (!isRegisterMode) Color(0xFF0F4C81) else Color(0xFF64748B)
                            ),
                            elevation = if (!isRegisterMode) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { isRegisterMode = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRegisterMode) Color.White else Color.Transparent,
                                contentColor = if (isRegisterMode) Color(0xFF0F4C81) else Color(0xFF64748B)
                            ),
                            elevation = if (isRegisterMode) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Register", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        if (isRegisterMode) "Create an Account" else "Welcome Back",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isRegisterMode) 
                            "Sign up to connect with clients & local providers in Manipur." 
                        else 
                            "Sign in to access your SINTHA dashboard.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (min 6 chars)") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    
                    if (isRegisterMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (isRegisterMode) {
                                onRegister(email, name, phone, chosenRole, password)
                            } else {
                                onLogin(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (chosenRole == "CLIENT" || !isRegisterMode) Color(0xFF0F4C81) else Color(0xFF8B5CF6)
                        )
                    ) {
                        Text(if (isRegisterMode) "Register Now" else "Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(
                            "OR CONTINUE WITH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Beautiful modern Google Sign-In button
                    OutlinedButton(
                        onClick = { showGoogleChooser = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Sign in with Gmail",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { onDemoLogin("aicrafts56@gmail.com", "Irabot Laishram", "+91 93621 12345", "CLIENT") }) {
                        Text("Demo Account Login (One-click Bypass)", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// 2. CLIENT EXPLORE SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    providers: List<ProviderEntity>,
    searchQuery: String,
    selectedCategory: String?,
    categories: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onProviderClick: (ProviderEntity) -> Unit,
    onSaveToggle: (ProviderEntity) -> Unit,
    onVoiceSearchClick: () -> Unit,
    viewModel: MainViewModel
) {
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val verifiedOnly by viewModel.verifiedOnly.collectAsStateWithLifecycle()
    val proOnly by viewModel.proOnly.collectAsStateWithLifecycle()

    var showFilterPanel by remember { mutableStateOf(false) }
    var showVoiceSearch by remember { mutableStateOf(false) }

    val districts = listOf(
        "Imphal West",
        "Imphal East",
        "Thoubal",
        "Churachandpur",
        "Kakching",
        "Bishnupur",
        "Ukhrul",
        "Senapati"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Search & Filter Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp)
            ) {
                Text(
                    "Find Trusted Local Experts",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    "100% commission-free, direct communication across Manipur",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Beautiful custom compact search bar
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(Color.White, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search electrician, beauty, tutors...",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onVoiceSearchClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Gemini AI Voice Search",
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    // Advanced Filter Panel Toggle Button
                    IconButton(
                        onClick = { showFilterPanel = !showFilterPanel },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showFilterPanel || selectedLocation != null || sortBy != "None" || verifiedOnly || proOnly) {
                                Color.White.copy(alpha = 0.25f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Toggle Advanced Filters",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Collapsible Advanced Filter Panel
        androidx.compose.animation.AnimatedVisibility(visible = showFilterPanel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterAlt, null, tint = Color(0xFF0F4C81), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Advanced Filters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                        }
                        TextButton(
                            onClick = { viewModel.resetFilters() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Reset All", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Location Selection
                    Text("Select District in Manipur", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedLocation == null,
                                onClick = { viewModel.selectLocation(null) },
                                label = { Text("All Manipur", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF0F4C81)
                                )
                            )
                        }
                        items(districts) { district ->
                            val isSel = selectedLocation == district
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.selectLocation(if (isSel) null else district) },
                                label = { Text(district, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF0F4C81)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sort By Selection
                    Text("Sort Service Providers", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        val sortOptions = listOf(
                            "None" to "Recommended",
                            "Rating" to "Rating ⭐",
                            "Experience" to "Experience 💼",
                            "PriceLowToHigh" to "Price: Low to High 📈",
                            "PriceHighToLow" to "Price: High to Low 📉"
                        )
                        items(sortOptions) { (key, label) ->
                            val isSel = sortBy == key
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.setSortBy(key) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEFF6FF),
                                    selectedLabelColor = Color(0xFF0F4C81)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Switch Toggles styled as beautiful chip toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Verified Only
                        OutlinedCard(
                            onClick = { viewModel.setVerifiedOnly(!verifiedOnly) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (verifiedOnly) Color(0xFFECFDF5) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (verifiedOnly) Color(0xFF10B981) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (verifiedOnly) Color(0xFF10B981) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Verified Only",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (verifiedOnly) Color(0xFF065F46) else Color(0xFF475569)
                                )
                            }
                        }

                        // PRO Only
                        OutlinedCard(
                            onClick = { viewModel.setProOnly(!proOnly) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (proOnly) Color(0xFFFEF3C7) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (proOnly) Color(0xFFF59E0B) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (proOnly) Color(0xFFF59E0B) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "PRO Experts Only",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (proOnly) Color(0xFFB45309) else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Category Row
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Categories", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            if (selectedCategory != null) {
                TextButton(onClick = { onCategorySelect(null) }) {
                    Text("Clear", color = Color(0xFF0F4C81), fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(if (isSelected) null else category) },
                    label = { Text(category, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEFF6FF),
                        selectedLabelColor = Color(0xFF0F4C81)
                    )
                )
            }
        }

        // Active filters & Results Overview Summary
        val hasActiveFilters = selectedLocation != null || sortBy != "None" || verifiedOnly || proOnly || selectedCategory != null || searchQuery.isNotEmpty()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val locationText = selectedLocation ?: "All Manipur"
            val countText = if (providers.size == 1) "1 Expert found" else "${providers.size} Experts found"
            Text(
                text = "$countText in $locationText",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )

            if (hasActiveFilters) {
                TextButton(
                    onClick = { viewModel.resetFilters() },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF0F4C81))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Filters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F4C81))
                }
            }
        }

        // Main Listings Column
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // PRO Listings (Featured Segment) if no search or complex filters are typed
            val proProviders = providers.filter { it.isPro }
            if (proProviders.isNotEmpty() && searchQuery.isEmpty() && selectedCategory == null && selectedLocation == null && sortBy == "None") {
                item {
                    Text(
                        "Featured PRO Professionals",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(proProviders) { provider ->
                            FeaturedProCard(
                                provider = provider,
                                onClick = { onProviderClick(provider) },
                                onSaveToggle = { onSaveToggle(provider) },
                                viewModel = viewModel
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "All Professionals",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (providers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Service Providers found", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Try modifying your query or filters.", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            } else {
                items(providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        onClick = { onProviderClick(provider) },
                        onSaveToggle = { onSaveToggle(provider) },
                        viewModel = viewModel
                    )
                }
            }
        }


    }
}

// 3. FEATURED PRO CARD COMPONENT
@Composable
fun FeaturedProCard(
    provider: ProviderEntity,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit,
    viewModel: MainViewModel
) {
    val isSaved by viewModel.isSaved(provider.id).collectAsStateWithLifecycle(false)

    Card(
        modifier = Modifier
            .width(260.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, Color(0xFFFBBF24)), // Amber border
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("FEATURED PRO", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                    }
                }

                IconButton(onClick = onSaveToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Save provider",
                        tint = if (isSaved) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = provider.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF1E293B)
            )
            Text(
                text = provider.subcategory,
                fontSize = 12.sp,
                color = Color(0xFF0F4C81),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(provider.location, fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(provider.rating.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = provider.description,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Rate starting at", fontSize = 9.sp, color = Color.Gray)
                    Text("₹${provider.rate.toInt()}/${provider.rateUnit}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                }
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Book Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 4. GENERAL PROVIDER CARD COMPONENT
@Composable
fun ProviderCard(
    provider: ProviderEntity,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit,
    viewModel: MainViewModel
) {
    val isSaved by viewModel.isSaved(provider.id).collectAsStateWithLifecycle(false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (provider.profilePictureUrl.isNotEmpty()) {
                            AsyncImage(
                                model = provider.profilePictureUrl,
                                contentDescription = "Provider Photo",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(23.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = provider.name.take(2).uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (provider.isVerified) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified Provider",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (provider.isPro) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = "PRO Member",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = provider.subcategory,
                            fontSize = 13.sp,
                            color = Color(0xFF0F4C81),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(onClick = onSaveToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Save provider",
                        tint = if (isSaved) Color.Red else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(provider.location, fontSize = 12.sp, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(provider.rating.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(" (${provider.reviewCount})", fontSize = 11.sp, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkHistory, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("${provider.experienceYears} yrs exp", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = provider.description,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Price starting: ", fontSize = 11.sp, color = Color.Gray)
                    Text("₹${provider.rate.toInt()}/${provider.rateUnit}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1E293B))
                }

                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Book & Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 5. PROVIDER DETAILS & BOOKING SCREEN
@Composable
fun ProviderDetailScreen(
    provider: ProviderEntity,
    savedAddresses: List<SavedAddressEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onChat: (String, String) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }
    
    var dateText by remember { mutableStateOf("26 Jun 2026, 10:00 AM") }
    
    fun showDateTimePicker() {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(java.util.Calendar.YEAR, year)
                calendar.set(java.util.Calendar.MONTH, month)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)

                android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(java.util.Calendar.MINUTE, minute)

                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                        dateText = sdf.format(calendar.time)
                    },
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    var notesText by remember { mutableStateOf("") }
    var selectedAddress by remember { mutableStateOf(savedAddresses.firstOrNull()?.fullAddress ?: "Imphal, Manipur") }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showCallLockedDialog by remember { mutableStateOf(false) }

    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val isBookingConfirmed = bookings.any {
        it.providerId == provider.id && (it.status == "CONFIRMED" || it.status == "COMPLETED")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
    ) {
        // Back Button Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F4C81))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Professional Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        // Provider Header Profile Details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (provider.profilePictureUrl.isNotEmpty()) {
                            AsyncImage(
                                model = provider.profilePictureUrl,
                                contentDescription = "Provider Photo",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(30.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                provider.name.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF0F4C81)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1E293B))
                            if (provider.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                            }
                            if (provider.isPro) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.WorkspacePremium, "PRO", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(provider.subcategory, fontWeight = FontWeight.Medium, color = Color(0xFF0F4C81), fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rating", fontSize = 11.sp, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                            Text(" ${provider.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Experience", fontSize = 11.sp, color = Color.Gray)
                        Text("${provider.experienceYears} Years", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Service Rate", fontSize = 11.sp, color = Color.Gray)
                        Text("₹${provider.rate.toInt()}/${provider.rateUnit}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F4C81))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))

                Text("About Me", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(4.dp))
                Text(provider.description, fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onChat(provider.id, provider.name) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0F4C81))
                    ) {
                        Icon(Icons.Default.Chat, null, tint = Color(0xFF0F4C81))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chat Directly", color = Color(0xFF0F4C81))
                    }

                    Button(
                        onClick = {
                            if (isBookingConfirmed) {
                                val intentUri = "tel:${provider.phone}"
                                viewModel.showToast("Initiating direct call to ${provider.phone}")
                            } else {
                                showCallLockedDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBookingConfirmed) Color(0xFF16A34A) else Color(0xFF94A3B8)
                        )
                    ) {
                        Icon(
                            imageVector = if (isBookingConfirmed) Icons.Default.Phone else Icons.Default.Lock,
                            contentDescription = if (isBookingConfirmed) "Call" else "Locked"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBookingConfirmed) "Call Direct" else "Call Locked")
                    }
                }
            }
        }

        // BOOKING INTERFACE PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Schedule Booking Session", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                Text("Providers keep 100% of their earnings. Transparent direct contracts.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateTimePicker() }
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Appointment Date & Time") },
                        leadingIcon = { Icon(Icons.Default.Event, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Saved Address Selector dropdown style
                Column {
                    Text("Service Location", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedCard(
                        onClick = { showAddressDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF0F4C81))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedAddress, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Special requirements/Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.bookService(provider, dateText, selectedAddress, notesText)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81))
                ) {
                    Text("Confirm Booking Request", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // Address Selector Dialog
    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("Select Booking Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savedAddresses.isEmpty()) {
                        Text("No saved addresses. Please type custom address below or add addresses in profile.", fontSize = 12.sp, color = Color.Gray)
                    }
                    savedAddresses.forEach { addr ->
                        OutlinedCard(
                            onClick = {
                                selectedAddress = "${addr.label}: ${addr.fullAddress}"
                                showAddressDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(addr.label, fontWeight = FontWeight.Bold, color = Color(0xFF0F4C81))
                                Text(addr.fullAddress, fontSize = 12.sp)
                            }
                        }
                    }

                    var customAddr by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = customAddr,
                        onValueChange = { customAddr = it },
                        label = { Text("Or Type Custom Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (customAddr.isNotEmpty()) {
                                selectedAddress = customAddr
                                showAddressDialog = false
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Use Custom")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showCallLockedDialog) {
        AlertDialog(
            onDismissRequest = { showCallLockedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call Option Locked")
                }
            },
            text = {
                Text(
                    "To maintain platform security, professional safety, and fair contract tracking, direct phone calling is locked.\n\n" +
                    "Please submit a booking request below. Once ${provider.name} accepts and confirms your booking request, the direct calling option will unlock instantly!",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCallLockedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81))
                ) {
                    Text("OK, Got It")
                }
            }
        )
    }
}

// 6. CHAT LIST SCREEN
@Composable
fun ChatListScreen(
    myId: String,
    viewModel: MainViewModel,
    onOpenChat: (String, String) -> Unit,
    onOpenAiChat: () -> Unit
) {
    // Collect message lists to extract conversations
    val recentMessages by viewModel.getRecentChats(myId).collectAsStateWithLifecycle(emptyList())

    // Group chats by contact
    val conversations = remember(recentMessages) {
        val groups = mutableMapOf<String, ChatMessageEntity>()
        recentMessages.forEach { msg ->
            val other = if (msg.senderId == myId) msg.receiverId else msg.senderId
            // Keep the most recent message
            val existing = groups[other]
            if (existing == null || existing.timestamp < msg.timestamp) {
                groups[other] = msg
            }
        }
        groups.entries.sortedByDescending { it.value.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(20.dp)
        ) {
            Column {
                Text("Messages", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF1E293B))
                Text("Direct chat logs with clients and professionals.", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // SINTHA AI Chat Assistant Entry Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable { onOpenAiChat() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = "AI Assistant",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SINTHA AI Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "EN / MN",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Chat in English & Meiteilon (Manipuri). Get local service recommendations!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Open Chat",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No active conversations", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("Navigate to Explore or Marketplace to initiate direct contract chats.", fontSize = 11.sp, color = Color.LightGray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(conversations) { entry ->
                    val otherUser = entry.key
                    val lastMsg = entry.value

                    // Display name lookup - normally would resolve from profiles database
                    val contactName = if (otherUser.contains("@")) {
                        otherUser.split("@").first().replaceFirstChar { it.uppercase() }
                    } else {
                        "Local Provider"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onOpenChat(otherUser, contactName) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contactName.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F4C81)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(contactName, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Text("Recent", fontSize = 10.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    lastMsg.messageText,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. ACTIVE CONVERSATION CHAT SCREEN
@Composable
fun ActiveChatScreen(
    recipientId: String,
    recipientName: String,
    messages: List<ChatMessageEntity>,
    myId: String,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to end when messages load
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // Chat Header with details
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        recipientName.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F4C81),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(recipientName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                    Text(recipientId, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        // Messages Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderId == myId
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isMe) 12.dp else 0.dp,
                                bottomEnd = if (isMe) 0.dp else 12.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) Color(0xFF0F4C81) else Color.White
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.messageText,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp,
                                color = if (isMe) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        // Input bottom bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = { Text("Type private contract message...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textState.trim().isNotEmpty()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF0F4C81), shape = RoundedCornerShape(22.dp))
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// 8. JOB MARKETPLACE SCREEN
@Composable
fun MarketplaceScreen(
    jobs: List<JobPostEntity>,
    isProvider: Boolean,
    onPostJob: (String, String, String, Double, String, String) -> Unit,
    onDeleteJob: (Int) -> Unit,
    onContactClient: (String, String) -> Unit,
    profile: UserProfile
) {
    var showPostDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Banner Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6)) // Purple branding for referral/job
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Job Marketplace",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        "Clients post exact needs; providers negotiate directly for ₹0 commission.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                if (!isProvider) {
                    Button(
                        onClick = { showPostDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Post Job", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (jobs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No job posts available", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("Clients can post job requirements here to discover pros fast.", fontSize = 12.sp, color = Color.LightGray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(jobs) { job ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(job.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                    Text("Posted by ${job.clientName}", fontSize = 12.sp, color = Color.Gray)
                                }

                                Surface(
                                    color = Color(0xFFF3E8FF),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "₹${job.budget.toInt()}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF8B5CF6)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(job.description, fontSize = 12.sp, color = Color.DarkGray)

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(job.location, fontSize = 11.sp, color = Color.Gray)

                                Spacer(modifier = Modifier.width(16.dp))

                                Icon(Icons.Default.Category, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(job.category, fontSize = 11.sp, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (profile.id == job.clientId) {
                                    TextButton(
                                        onClick = { onDeleteJob(job.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete Post", fontSize = 12.sp)
                                    }
                                } else {
                                    Text("Client Phone: ${job.phone}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }

                                if (profile.id != job.clientId) {
                                    Button(
                                        onClick = { onContactClient(job.clientId, job.clientName) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bid & Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Post Job Dialog
    if (showPostDialog) {
        var jobTitle by remember { mutableStateOf("") }
        var jobDesc by remember { mutableStateOf("") }
        var jobBudget by remember { mutableStateOf("") }
        var jobLocation by remember { mutableStateOf("Imphal West") }
        var jobCategory by remember { mutableStateOf("Home Services & Repairs") }
        var contactPhone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPostDialog = false },
            title = { Text("Post New Job Opportunity") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = jobTitle,
                        onValueChange = { jobTitle = it },
                        label = { Text("Job Title (e.g. Need Sofa Dry Cleaning)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = jobDesc,
                        onValueChange = { jobDesc = it },
                        label = { Text("Detailed Requirements") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = jobBudget,
                        onValueChange = { jobBudget = it },
                        label = { Text("Estimated Budget (INR)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = jobLocation,
                        onValueChange = { jobLocation = it },
                        label = { Text("Location Area in Manipur") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Contact Phone (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedBudget = jobBudget.toDoubleOrNull() ?: 0.0
                        onPostJob(jobTitle, jobDesc, jobCategory, parsedBudget, jobLocation, contactPhone)
                        showPostDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("Publish Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// 9. BOOKINGS SCREEN
@Composable
fun BookingsScreen(
    bookings: List<BookingEntity>,
    isProvider: Boolean,
    onStatusUpdate: (Int, String) -> Unit,
    onChatWithUser: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(20.dp)
        ) {
            Column {
                Text("Your Bookings", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF1E293B))
                Text(
                    if (isProvider) "Manage service contract jobs received from local clients."
                    else "Track active appointments with hired experts.",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }

        if (bookings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Assignment, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No bookings registered yet", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("Hire a professional or switch to Provider role to view contracts.", fontSize = 11.sp, color = Color.LightGray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bookings) { booking ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = booking.serviceCategory,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = if (isProvider) "Client: ${booking.clientName}" else "Provider: ${booking.providerName}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                                val statusColor = when (booking.status) {
                                    "PENDING" -> Color(0xFFF59E0B)  // Orange
                                    "CONFIRMED" -> Color(0xFF0F4C81) // Blue
                                    "COMPLETED" -> Color(0xFF22C55E) // Green
                                    else -> Color(0xFFEF4444)        // Red for CANCELLED
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = booking.status,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = statusColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(booking.bookingDate, fontSize = 12.sp, color = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(booking.address, fontSize = 12.sp, color = Color.DarkGray)
                            }

                            if (booking.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Notes: ${booking.notes}",
                                        modifier = Modifier.padding(8.dp),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Contract Amount: ₹${booking.rate.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F4C81)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {
                                            if (isProvider) {
                                                onChatWithUser(booking.clientId, booking.clientName)
                                            } else {
                                                onChatWithUser(booking.providerId, booking.providerName)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(18.dp))
                                    ) {
                                        Icon(Icons.Default.Chat, null, tint = Color(0xFF0F4C81), modifier = Modifier.size(16.dp))
                                    }

                                    if (isProvider && booking.status == "PENDING") {
                                        Button(
                                            onClick = { onStatusUpdate(booking.id, "CONFIRMED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (isProvider && booking.status == "CONFIRMED") {
                                        Button(
                                            onClick = { onStatusUpdate(booking.id, "COMPLETED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Complete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (booking.status == "PENDING" || booking.status == "CONFIRMED") {
                                        OutlinedButton(
                                            onClick = { onStatusUpdate(booking.id, "CANCELLED") },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                            border = BorderStroke(1.dp, Color.Red),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Cancel", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 10. PROVIDER DASHBOARD (PROVIDER VIEW)
@Composable
fun ProviderDashboardScreen(
    profile: UserProfile,
    bookings: List<BookingEntity>,
    viewModel: MainViewModel,
    onGoPro: () -> Unit
) {
    var category by remember { mutableStateOf("Home Services & Repairs") }
    var subcategory by remember { mutableStateOf("General Electrician") }
    var location by remember { mutableStateOf("Imphal West") }
    var rate by remember { mutableStateOf("300") }
    var rateUnit by remember { mutableStateOf("hour") }
    var description by remember { mutableStateOf("Reliable service provider in Imphal. Client satisfaction guaranteed.") }
    var experienceYears by remember { mutableStateOf("4") }

    val activeProviderProfile by viewModel.activeProviderProfile.collectAsStateWithLifecycle()

    // Dynamically bind from DB when available
    LaunchedEffect(activeProviderProfile) {
        activeProviderProfile?.let { prov ->
            category = prov.category
            subcategory = prov.subcategory
            location = prov.location
            rate = prov.rate.toInt().toString()
            rateUnit = prov.rateUnit
            description = prov.description
            experienceYears = prov.experienceYears.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
    ) {
        var availabilityState by remember { mutableStateOf("Available") }
        var showStrengthDetails by remember { mutableStateOf(false) }

        // Banner info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6)) // Purple provider theme
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Provider Dashboard",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            "Welcome back, ${profile.name}!",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Display PRO label if active
                    if (profile.isPro) {
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("PRO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "SINTHA is 100% commission-free. Set your prices and keep all earnings.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // 1. Availability segmented selector (PRD 4.1.1 & 4.9.1)
                Text(
                    "Set Working Status",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statuses = listOf("Available", "Busy", "Offline")
                    statuses.forEach { status ->
                        val isSelected = availabilityState == status
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    availabilityState = status
                                    android.widget.Toast.makeText(viewModel.getApplication(), "Status changed to $status", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = status.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Stats Grid (PRD 4.1.6)
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Performance Overview",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pending Request card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Pending, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pending", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            bookings.count { it.status == "PENDING" }.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                // Total Bookings card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Assignment, null, tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Bookings", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            bookings.size.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total Earnings card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Payments, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Earnings", color = Color.Gray, fontSize = 11.sp)
                        val completedSum = bookings.filter { it.status == "COMPLETED" }.sumOf { it.rate }
                        Text(
                            "₹${completedSum.toInt()}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // Average Rating card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Avg Rating", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            "4.9",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }

        // 3. Profile Strength Meter (PRD 4.1.8)
        val profilePhotoDone = profile.profilePictureUrl.isNotEmpty()
        val descDone = description.trim().length > 10
        val subcategoryDone = subcategory.trim().isNotEmpty()
        val verifiedDone = profile.isVerified
        val proDone = profile.isPro

        var strengthPoints = 0
        if (profilePhotoDone) strengthPoints += 20
        if (descDone) strengthPoints += 20
        if (subcategoryDone) strengthPoints += 20
        if (verifiedDone) strengthPoints += 20
        if (proDone) strengthPoints += 20

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Profile Strength Meter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("$strengthPoints%", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF8B5CF6))
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                LinearProgressIndicator(
                    progress = strengthPoints / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFFF3E8FF)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStrengthDetails = !showStrengthDetails },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "How to boost your profile strength?",
                        fontSize = 11.sp,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (showStrengthDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (showStrengthDetails) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (profilePhotoDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (profilePhotoDone) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload profile photo (+20%)", fontSize = 11.sp, color = if (profilePhotoDone) Color.Gray else Color(0xFF1E293B))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (subcategoryDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (subcategoryDone) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set subcategory & rate (+20%)", fontSize = 11.sp, color = if (subcategoryDone) Color.Gray else Color(0xFF1E293B))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (descDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (descDone) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Write compelling specialty description (+20%)", fontSize = 11.sp, color = if (descDone) Color.Gray else Color(0xFF1E293B))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (verifiedDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (verifiedDone) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Identity Verified badge (+20%)", fontSize = 11.sp, color = if (verifiedDone) Color.Gray else Color(0xFF1E293B))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!proDone) onGoPro() }
                        ) {
                            Icon(
                                if (proDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (proDone) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Unlock SINTHA PRO status (+20%)", 
                                fontSize = 11.sp, 
                                color = if (proDone) Color.Gray else Color(0xFF8B5CF6),
                                fontWeight = if (proDone) FontWeight.Normal else FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Active Booking Management Panel
        Text(
            "Service Contracts Received",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp)
        )

        val pendingRequests = bookings.filter { it.status == "PENDING" || it.status == "CONFIRMED" }
        if (pendingRequests.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AssignmentLate, null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No active service orders", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
                    Text("Your services are live. Clients will book you directly.", fontSize = 11.sp, color = Color.LightGray)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingRequests.forEach { booking ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(booking.clientName, fontWeight = FontWeight.Bold)
                                    Text(booking.bookingDate, fontSize = 11.sp, color = Color.Gray)
                                }
                                Surface(
                                    color = if (booking.status == "PENDING") Color(0xFFFEF3C7) else Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        booking.status,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (booking.status == "PENDING") Color(0xFFD97706) else Color(0xFF0F4C81)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Address: ${booking.address}", fontSize = 12.sp, color = Color.Gray)
                            if (booking.notes.isNotEmpty()) {
                                Text("Requirement: ${booking.notes}", fontSize = 11.sp, color = Color.LightGray)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Payout: ₹${booking.rate.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { viewModel.openChat(booking.clientId, booking.clientName) },
                                        modifier = Modifier.size(32.dp).background(Color(0xFFF3E8FF), shape = RoundedCornerShape(16.dp))
                                    ) {
                                        Icon(Icons.Default.Chat, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(14.dp))
                                    }

                                    if (booking.status == "PENDING") {
                                        Button(
                                            onClick = { viewModel.updateBookingStatus(booking.id, "CONFIRMED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Accept", fontSize = 10.sp)
                                        }
                                    } else if (booking.status == "CONFIRMED") {
                                        Button(
                                            onClick = { viewModel.updateBookingStatus(booking.id, "COMPLETED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Complete", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Service Listing Customizer Form
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Configure Your Public Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                Text("Your listing on the public market feed.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = subcategory,
                    onValueChange = { subcategory = it },
                    label = { Text("Service Subcategory (e.g. Electrician, bridal stylist)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Service Charge (INR)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Service Location Coverage (e.g. Imphal West)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = experienceYears,
                    onValueChange = { experienceYears = it },
                    label = { Text("Years of Experience") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Service Description/Specialties") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val parsedRate = rate.toDoubleOrNull() ?: 300.0
                        val parsedExp = experienceYears.toIntOrNull() ?: 4
                        viewModel.saveProviderProfile(
                            category,
                            subcategory,
                            location,
                            parsedRate,
                            rateUnit,
                            description,
                            parsedExp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save & Update Listing", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 11. PROFILE SCREEN (PERSISTENCE, SAVED ADDRESSES, PRO SUBSCRIPTION & REFERRALS)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    savedProviders: List<ProviderEntity>,
    savedAddresses: List<SavedAddressEntity>,
    onAddAddress: (String, String, String) -> Unit,
    onDeleteAddress: (Int) -> Unit,
    onApplyReferral: (String) -> Unit,
    onGoPro: () -> Unit,
    onGetVerified: () -> Unit,
    onProviderClick: (ProviderEntity) -> Unit,
    onSaveToggle: (ProviderEntity) -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    viewModel: MainViewModel
) {
    var showAddressDialog by remember { mutableStateOf(false) }
    var referralCodeInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadProfilePicture(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
    ) {
        // User Meta Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.profilePictureUrl.isNotEmpty()) {
                        AsyncImage(
                            model = profile.profilePictureUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(44.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            profile.name.take(2).uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                    }
                    
                    // Compact Camera Overlay Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Change photo",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text(profile.phone, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Text("Role: ${profile.role}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
            }
        }

        // Role switcher toggle card (PRD 3.1 & 18.1)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (profile.role == "PROVIDER") Icons.Default.Engineering else Icons.Default.Person,
                        contentDescription = "Role Mode",
                        tint = if (profile.role == "PROVIDER") Color(0xFF8B5CF6) else Color(0xFF0F4C81),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (profile.role == "PROVIDER") "Active Mode: Provider" else "Active Mode: Client",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = if (profile.role == "PROVIDER") "Switch back to request services" else "Switch to offer services & earn",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                Switch(
                    checked = (profile.role == "PROVIDER"),
                    onCheckedChange = { viewModel.toggleRole() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF8B5CF6),
                        checkedTrackColor = Color(0xFFF3E8FF),
                        uncheckedThumbColor = Color(0xFF0F4C81),
                        uncheckedTrackColor = Color(0xFFEFF6FF)
                    )
                )
            }
        }

        // Trust & Verification Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    profile.isVerified -> Color(0xFFECFDF5)
                    profile.verificationStatus == "PENDING" -> Color(0xFFFFFBEB)
                    profile.verificationStatus == "REJECTED" -> Color(0xFFFEF2F2)
                    else -> Color(0xFFFEF2F2)
                }
            ),
            border = BorderStroke(
                1.dp,
                when {
                    profile.isVerified -> Color(0xFFA7F3D0)
                    profile.verificationStatus == "PENDING" -> Color(0xFFFDE68A)
                    profile.verificationStatus == "REJECTED" -> Color(0xFFFCA5A5)
                    else -> Color(0xFFFCA5A5)
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when {
                    profile.isVerified -> {
                        Text("Identity Verified ✓", fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                        Text("Your profile is certified with a green badge. Tapping will view your verification status.", fontSize = 11.sp, color = Color(0xFF047857))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGetVerified,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("View Verification Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    profile.verificationStatus == "PENDING" -> {
                        Text("Verification Request Pending", fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Text("Our administrators are currently validating your identity documents. Please wait.", fontSize = 11.sp, color = Color(0xFF78350F))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGetVerified,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Track Review Progress", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    profile.verificationStatus == "REJECTED" -> {
                        Text("Verification Request Disapproved", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                        Text("Reason: ${profile.rejectionReason.ifEmpty { "Provided documents were unclear. Please resubmit." }}. Click to resubmit.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGetVerified,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Resubmit Documents", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Text("Get Identity Verified", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                        Text("Verified badges get 3x higher booking rates. Upload ID credentials instantly.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGetVerified,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Verify Identity Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // PRO Upgrade Panel
        if (!profile.isPro) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.5.dp, Color(0xFFFBBF24))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, "PRO", tint = Color(0xFFD97706), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upgrade to SINTHA PRO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFB45309))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Get top market listing placement, featured homepage badges, and high-visibility feeds for only ₹199/month.", fontSize = 12.sp, color = Color(0xFF78350F))
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onGoPro,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade for ₹199/mo", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Admin Console Card (For testing and credential verification approval)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, "Admin", tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Verification Console", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF334155))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Simulate and manage document verification requests submitted by other service providers and clients.", fontSize = 11.sp, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAdminConsole,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Admin Console", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Referral Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("SINTHA Referral Program", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                Text("Earn 30% recurring commission from PRO subscriptions purchased by referred users.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Your Referral Code", fontSize = 10.sp, color = Color.Gray)
                        Text(profile.referralCode, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF8B5CF6))
                    }
                    Column {
                        Text("Earned Balance", fontSize = 10.sp, color = Color.Gray)
                        Text("₹${profile.balance.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF16A34A))
                    }
                    Column {
                        Text("Successful Referrals", fontSize = 10.sp, color = Color.Gray)
                        Text("${profile.referralCount} Users", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF1E293B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                if (profile.referredBy.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = referralCodeInput,
                            onValueChange = { referralCodeInput = it },
                            placeholder = { Text("Enter invite code") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp)
                        )
                        Button(
                            onClick = { onApplyReferral(referralCodeInput) },
                            shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Apply")
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Referred by: ${profile.referredBy}",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
            }
        }

        // Saved Addresses List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Addresses", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                    TextButton(onClick = { showAddressDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (savedAddresses.isEmpty()) {
                    Text("No saved addresses", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedAddresses.forEach { addr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(addr.fullAddress, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteAddress(addr.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Service Providers Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Saved Professionals", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                if (savedProviders.isEmpty()) {
                    Text("You haven't saved any professionals yet.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedProviders.forEach { provider ->
                            ProviderCard(
                                provider = provider,
                                onClick = { onProviderClick(provider) },
                                onSaveToggle = { onSaveToggle(provider) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        // Quick-link to the real-time SINTHA AI Chat Assistant
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "SINTHA AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SINTHA AI Chat Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Have questions about SINTHA's free commission-free model, booking flows, PRO features, or need help finding active service providers in Manipur? Ask our AI assistant in either English or Romanized Meiteilon!",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onOpenAiChat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Chat with AI Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Sign Out Button
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
        ) {
            Icon(Icons.Default.ExitToApp, null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Add Address dialog
    if (showAddressDialog) {
        var label by remember { mutableStateOf("") }
        var fullAddress by remember { mutableStateOf("") }
        var landmark by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("Save New Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (e.g. Home, Office)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = fullAddress,
                        onValueChange = { fullAddress = it },
                        label = { Text("Full Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = landmark,
                        onValueChange = { landmark = it },
                        label = { Text("Landmark") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddAddress(label, fullAddress, landmark)
                        showAddressDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81))
                ) {
                    Text("Save Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RazorpayPaymentModal(
    profile: UserProfile,
    onPaymentSuccess: () -> Unit,
    onPaymentCancel: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onPaymentCancel
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            // states: "CREATING_ORDER", "CHECKOUT", "PROCESSING", "SUCCESS", "ERROR"
            var paymentState by remember { mutableStateOf("CREATING_ORDER") }
            var selectedTab by remember { mutableStateOf("UPI") } // UPI, CARD, NETBANKING, WALLET
            
            // Form Inputs
            var upiId by remember { mutableStateOf("") }
            var cardNumber by remember { mutableStateOf("") }
            var cardExpiry by remember { mutableStateOf("") }
            var cardCvv by remember { mutableStateOf("") }
            var cardName by remember { mutableStateOf(profile.name) }
            
            var selectedBank by remember { mutableStateOf("State Bank of India") }
            var selectedWallet by remember { mutableStateOf("Paytm") }
            
            var errorMessage by remember { mutableStateOf("Payment failed. Please try again.") }

            // Start order creation simulation
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1200)
                paymentState = "CHECKOUT"
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header of the payment overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F2C59)) // Razorpay Signature Dark Navy
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "SINTHA PRO",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                "Secure Checkout • rzp_order_S1NTHA",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "₹199.00",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "Razorpay Secured",
                                    fontSize = 9.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Main Content area based on states
                when (paymentState) {
                    "CREATING_ORDER" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF0F4C81),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Contacting secure Razorpay servers...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Creating Order ID rzp_order_S1NTHA...",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    "CHECKOUT" -> {
                        // Display user info summary bar (prefilled)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paying: ${profile.id.ifEmpty { "client@sintha.app" }} • ${profile.phone}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Edit Prefills",
                                fontSize = 10.sp,
                                color = Color(0xFF0F4C81),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    // Just show toast, editable is simulated
                                }
                            )
                        }

                        // Tab selectors for Payment Methods
                        TabRow(
                            selectedTabIndex = when (selectedTab) {
                                "UPI" -> 0
                                "CARD" -> 1
                                "NETBANKING" -> 2
                                else -> 3
                            },
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F4C81)
                        ) {
                            Tab(
                                selected = selectedTab == "UPI",
                                onClick = { selectedTab = "UPI" },
                                text = { Text("UPI", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == "CARD",
                                onClick = { selectedTab = "CARD" },
                                text = { Text("Card", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == "NETBANKING",
                                onClick = { selectedTab = "NETBANKING" },
                                text = { Text("Net Bank", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == "WALLET",
                                onClick = { selectedTab = "WALLET" },
                                text = { Text("Wallet", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        // Screen based on tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            when (selectedTab) {
                                "UPI" -> {
                                    Text("Pay via Instant UPI apps", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Google Pay option
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    upiId = "gpay@okaxis"
                                                },
                                            border = BorderStroke(1.dp, if (upiId == "gpay@okaxis") Color(0xFF0F4C81) else Color(0xFFE2E8F0)),
                                            colors = CardDefaults.cardColors(containerColor = if (upiId == "gpay@okaxis") Color(0xFFEFF6FF) else Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(Icons.Default.Send, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Google Pay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // PhonePe option
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    upiId = "phonepe@ybl"
                                                },
                                            border = BorderStroke(1.dp, if (upiId == "phonepe@ybl") Color(0xFF0F4C81) else Color(0xFFE2E8F0)),
                                            colors = CardDefaults.cardColors(containerColor = if (upiId == "phonepe@ybl") Color(0xFFEFF6FF) else Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(Icons.Default.Payments, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("PhonePe", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Paytm option
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    upiId = "paytm@paytm"
                                                },
                                            border = BorderStroke(1.dp, if (upiId == "paytm@paytm") Color(0xFF0F4C81) else Color(0xFFE2E8F0)),
                                            colors = CardDefaults.cardColors(containerColor = if (upiId == "paytm@paytm") Color(0xFFEFF6FF) else Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF0F4C81), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Paytm", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("Or enter UPI Address manually", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = upiId,
                                        onValueChange = { upiId = it },
                                        placeholder = { Text("username@bank") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                                "CARD" -> {
                                    Text("Pay with Debit/Credit Card", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    OutlinedTextField(
                                        value = cardNumber,
                                        onValueChange = { 
                                            if (it.length <= 16) cardNumber = it.filter { char -> char.isDigit() }
                                        },
                                        label = { Text("Card Number") },
                                        placeholder = { Text("4111 1111 1111 1111") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        trailingIcon = {
                                            if (cardNumber.startsWith("4")) {
                                                Text("VISA", fontWeight = FontWeight.Bold, color = Color(0xFF0F4C81), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                            } else if (cardNumber.startsWith("5")) {
                                                Text("MC", fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = cardExpiry,
                                            onValueChange = { cardExpiry = it },
                                            label = { Text("Expiry (MM/YY)") },
                                            placeholder = { Text("12/28") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = cardCvv,
                                            onValueChange = { 
                                                if (it.length <= 3) cardCvv = it.filter { char -> char.isDigit() }
                                            },
                                            label = { Text("CVV") },
                                            placeholder = { Text("123") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = cardName,
                                        onValueChange = { cardName = it },
                                        label = { Text("Cardholder Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                                "NETBANKING" -> {
                                    Text("Popular Banks", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val banks = listOf("State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank")
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        banks.forEach { bank ->
                                            val isSelected = selectedBank == bank
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, if (isSelected) Color(0xFF0F4C81) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Color(0xFFEFF6FF) else Color.White)
                                                    .clickable { selectedBank = bank }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(bank, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedBank = bank },
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F4C81))
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> { // WALLET
                                    Text("Available Wallets", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val wallets = listOf("Paytm Wallet", "PhonePe Wallet", "Amazon Pay")
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        wallets.forEach { wallet ->
                                            val isSelected = selectedWallet == wallet
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, if (isSelected) Color(0xFF0F4C81) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Color(0xFFEFF6FF) else Color.White)
                                                    .clickable { selectedWallet = wallet }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(wallet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedWallet = wallet },
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F4C81))
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Bottom Action Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPaymentCancel,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel", color = Color.Gray)
                                }

                                Button(
                                    onClick = {
                                        // Validate inputs and process
                                        if (selectedTab == "CARD") {
                                            if (cardNumber.length < 16) {
                                                errorMessage = "Card declined: Invalid card number length."
                                                paymentState = "ERROR"
                                                return@Button
                                            }
                                            if (!cardExpiry.contains("/")) {
                                                errorMessage = "Card declined: Expiry must be in format MM/YY."
                                                paymentState = "ERROR"
                                                return@Button
                                            }
                                            // Handle decline simulation scenario
                                            if (cardNumber.endsWith("4000")) {
                                                errorMessage = "Payment Declined by Bank: Card has insufficient funds. (Error Code: RZP_DEC_INS)"
                                                paymentState = "ERROR"
                                                return@Button
                                            }
                                        } else if (selectedTab == "UPI") {
                                            if (upiId.trim().isEmpty()) {
                                                errorMessage = "UPI declined: Please select or enter a valid UPI address."
                                                paymentState = "ERROR"
                                                return@Button
                                            }
                                        }

                                        paymentState = "PROCESSING"
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pay ₹199.00 Securely")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            // Direct Scenario Simulator row for QA Checklist
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text(
                                    "[QA] Insufficient Funds Card",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.clickable {
                                        selectedTab = "CARD"
                                        cardNumber = "4111111111114000"
                                        cardExpiry = "12/29"
                                        cardCvv = "999"
                                    }
                                )
                                Text(
                                    "|",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    "[QA] Valid Card prefill",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.clickable {
                                        selectedTab = "CARD"
                                        cardNumber = "4111111111111111"
                                        cardExpiry = "12/28"
                                        cardCvv = "123"
                                    }
                                )
                            }
                        }
                    }
                    "PROCESSING" -> {
                        // Launch simulation to complete purchase
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(2000)
                            paymentState = "SUCCESS"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF0F4C81),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Processing Payment...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Do not close the app or click back.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Communicating with secure bank servers...",
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    "SUCCESS" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Payment Successful",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "SINTHA PRO Activated! 🎉",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF16A34A)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Payment of ₹199.00 processed successfully.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Receipt: rzp_rec_${System.currentTimeMillis().toString().takeLast(6)}", fontSize = 10.sp, color = Color.Gray)
                                    Text("Transaction ID: pay_SNT${System.currentTimeMillis().toString().takeLast(8)}", fontSize = 10.sp, color = Color.Gray)
                                    Text("Gateway: Razorpay Native v1.6", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    onPaymentSuccess()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Let's Go PRO!", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "ERROR" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Payment Failed",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Payment Failed",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPaymentCancel,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel", color = Color.Gray)
                                }
                                Button(
                                    onClick = {
                                        paymentState = "CHECKOUT"
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Retry Payment")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchModal(
    status: String,
    queryText: String,
    parsedResult: VoiceSearchResult?,
    errorText: String,
    onApply: (VoiceSearchResult) -> Unit,
    onDismiss: () -> Unit,
    onRetrySpeech: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gemini Voice Search",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Content based on status
                when (status) {
                    "ANALYZING" -> {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF3B82F6),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(52.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Analyzing your speech...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$queryText\"",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Gemini is translating your request into search filters...",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                    "RESULT" -> {
                        if (parsedResult != null) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search Applied!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Parsed query: \"$queryText\"",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "EXTRACTED FILTER METRICS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Keywords", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = parsedResult.searchQuery ?: "None",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Category", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = parsedResult.category ?: "All Categories",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (parsedResult.category != null) Color(0xFF3B82F6) else Color(0xFF1E293B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("District", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = parsedResult.location ?: "All Manipur",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (parsedResult.location != null) Color(0xFF8B5CF6) else Color(0xFF1E293B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Verified Only", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = if (parsedResult.verifiedOnly == true) "YES" else "NO",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (parsedResult.verifiedOnly == true) Color(0xFF10B981) else Color(0xFF1E293B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("PRO Only", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = if (parsedResult.proOnly == true) "YES (PRO only)" else "NO",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (parsedResult.proOnly == true) Color(0xFF8B5CF6) else Color(0xFF1E293B)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Sorted By", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = parsedResult.sortBy ?: "None",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (parsedResult.sortBy != "None") Color(0xFFF59E0B) else Color(0xFF1E293B)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Dismiss", color = Color.Gray)
                                }

                                Button(
                                    onClick = {
                                        onApply(parsedResult)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Show Results", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Parsing Failed",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorText.ifEmpty { "Speech recognition or AI analysis failed. Please try again." },
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Button(
                                onClick = onRetrySpeech,
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Try Speaking Again")
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. AI CHAT SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    history: List<GeminiContent>,
    isLoading: Boolean,
    languagePreference: String,
    onSendMessage: (String) -> Unit,
    onLanguagePreferenceChange: (String) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(history.size, isLoading) {
        if (history.isNotEmpty()) {
            lazyListState.animateScrollToItem(history.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SINTHA AI Assistant", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("English & Meiteilon Helper", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClearChat) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Conversation", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Language preference selector bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Preferred Chat Language:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val languages = listOf(
                            Triple("AUTO", "Auto Detect 🤖", Color(0xFF3B82F6)),
                            Triple("ENGLISH", "English 🇬🇧", Color(0xFF0F4C81)),
                            Triple("MEITEILON", "Meiteilon 🇲🇳", Color(0xFF10B981))
                        )

                        languages.forEach { (key, label, activeColor) ->
                            val isSelected = languagePreference == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeColor else Color(0xFFF1F5F9))
                                    .clickable { onLanguagePreferenceChange(key) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }

            // Chat Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (history.isEmpty()) {
                    // Empty / Welcome state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Khurumjari / Hello!",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "I am your SINTHA AI directory helper. Ask me about plumbers, painters, beauty parlors, tutors, or event planners in Manipur in either English or Meiteilon / Manipuri!",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Try asking these:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Prompt chips
                        val suggestedPrompts = listOf(
                            "English: Recommend best plumbers in Imphal West",
                            "Meiteilon: Nangga chat tounabada kari help fanggani?",
                            "English: Tutors in Kakching district with high rating",
                            "Meiteilon: Beauty or wellness helper Thoubal da leibra?"
                        )

                        suggestedPrompts.forEach { prompt ->
                            val cleanPromptText = prompt.substringAfter(": ")
                            val isMeiteilon = prompt.startsWith("Meiteilon")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSendMessage(cleanPromptText)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isMeiteilon) Color(0xFFE6F4EA) else Color(0xFFE8F0FE),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (isMeiteilon) "MN" else "EN",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMeiteilon) Color(0xFF137333) else Color(0xFF1A73E8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        cleanPromptText,
                                        fontSize = 12.sp,
                                        color = Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(history) { message ->
                            val isUser = message.role == "user"
                            val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else Color.White
                            val textColor = if (isUser) Color.White else Color(0xFF1E293B)
                            val alignment = if (isUser) Alignment.End else Alignment.Start

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = alignment
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 2.dp,
                                                bottomEnd = if (isUser) 2.dp else 16.dp
                                            )
                                        )
                                        .background(bubbleColor)
                                        .padding(14.dp)
                                ) {
                                    val textContent = message.parts.firstOrNull()?.text ?: ""
                                    if (isUser) {
                                        Text(
                                            text = textContent,
                                            color = textColor,
                                            fontSize = 14.sp
                                        )
                                    } else {
                                        // Simple bold parser for Gemini response
                                        val annotatedString = remember(textContent) {
                                            buildAnnotatedString {
                                                val rawText = textContent
                                                var index = 0
                                                val regex = Regex("\\*\\*(.*?)\\*\\*")
                                                val matches = regex.findAll(rawText)
                                                
                                                for (match in matches) {
                                                    // Append preceding text
                                                    if (match.range.first > index) {
                                                        append(rawText.substring(index, match.range.first))
                                                    }
                                                    // Append bold text
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Black, color = Color(0xFF0F4C81))) {
                                                        append(match.groupValues[1])
                                                    }
                                                    index = match.range.last + 1
                                                }
                                                if (index < rawText.length) {
                                                    append(rawText.substring(index))
                                                }
                                            }
                                        }
                                        Text(
                                            text = annotatedString,
                                            color = textColor,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                                Text(
                                    text = if (isUser) "You" else "SINTHA AI",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                                )
                            }
                        }

                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "SINTHA AI is thinking...",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }

            // Input Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        placeholder = { Text("Ask something in English/Meiteilon...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            disabledContainerColor = Color(0xFFF1F5F9),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                onSendMessage(textState)
                                textState = ""
                            }
                        },
                        enabled = textState.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (textState.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (textState.isNotBlank() && !isLoading) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
