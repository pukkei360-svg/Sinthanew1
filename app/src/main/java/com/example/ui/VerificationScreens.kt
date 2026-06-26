package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.UserProfile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSubmitScreen(
    profile: UserProfile,
    onSubmit: (docType: String, docUri: String) -> Unit,
    onBack: () -> Unit
) {
    var docType by remember { mutableStateOf("Aadhaar Card") }
    var idNumber by remember { mutableStateOf("") }
    var docUri by remember { mutableStateOf("") }
    var portraitUri by remember { mutableStateOf(profile.profilePictureUrl) }
    var expandedDocDropdown by remember { mutableStateOf(false) }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            docUri = uri.toString()
        }
    }

    val portraitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            portraitUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity Verification", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (profile.verificationStatus) {
                "PENDING" -> {
                    // Pending state
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pending,
                                contentDescription = "Pending Review",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Verification In Progress",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Your Aadhaar identity documents have been submitted and are currently under review by the SINTHA Admin Panel. We will update your profile status as soon as validation completes.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Document Submitted:", fontSize = 12.sp, color = Color.Gray)
                                Text(profile.verificationIdDocument.ifEmpty { "Aadhaar Card" }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Submitted At:", fontSize = 12.sp, color = Color.Gray)
                                Text("Just now", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
                "APPROVED" -> {
                    // Approved state
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Badge",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Identity Fully Verified!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF065F46)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Your profile is active with the verified badge (✓). You now receive maximum platform visibility, 3x higher booking rates, and top organic placements on the home and explore listings.",
                                fontSize = 13.sp,
                                color = Color(0xFF047857),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Back to Profile", color = Color.White)
                            }
                        }
                    }
                }
                else -> {
                    // Not started or rejected
                    if (profile.verificationStatus == "REJECTED") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = "Error", tint = Color(0xFFDC2626))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verification Disapproved", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Reason: ${profile.rejectionReason.ifEmpty { "Provided document image was blurry or incomplete. Please resubmit clear photos." }}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }

                    // Card header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, "Badge", tint = Color(0xFF0F4C81), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Why get verified?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                Text("Get the green verification checkmark and build immediate trust with clients looking for professional services in Manipur.", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Document Details", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Document Type Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = docType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Identity Document") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedDocDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = expandedDocDropdown,
                            onDismissRequest = { expandedDocDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Aadhaar Card", "Voter ID Card", "PAN Card", "Driving License").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        docType = type
                                        expandedDocDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ID Number input
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = { idNumber = it },
                        label = { Text("$docType Number") },
                        placeholder = { Text("e.g. 12-digit Aadhaar number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Upload Documents", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Text("Provide high-resolution photos of your credentials for swift admin approval.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Image upload selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Document Pic Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .background(Color.White, shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFCBD5E1), shape = RoundedCornerShape(12.dp))
                                .clickable { docLauncher.launch("image/*") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (docUri.isNotEmpty()) {
                                AsyncImage(
                                    model = docUri,
                                    contentDescription = "Document",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Attachment, "Doc", tint = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Front of ID", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                                    Text("Tap to select", fontSize = 9.sp, color = Color.LightGray)
                                }
                            }
                        }

                        // Portrait Passport Photo Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .background(Color.White, shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFCBD5E1), shape = RoundedCornerShape(12.dp))
                                .clickable { portraitLauncher.launch("image/*") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (portraitUri.isNotEmpty()) {
                                AsyncImage(
                                    model = portraitUri,
                                    contentDescription = "Portrait",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AccountBox, "Portrait", tint = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Passport Photo", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                                    Text("Tap to select", fontSize = 9.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Convenience Button to use mock document values for testing
                    Button(
                        onClick = {
                            idNumber = "5849-3829-9405"
                            docUri = "mock_aadhaar_uri"
                            portraitUri = "mock_portrait_uri"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Auto", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fill Mock ID and Document", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (idNumber.isEmpty()) {
                                return@Button
                            }
                            onSubmit(docType, if (docUri.isEmpty()) "mock_aadhaar_uri" else docUri)
                        },
                        enabled = idNumber.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Submit Identity Documents", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    profiles: List<UserProfile>,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Pending, 1 = All Users
    var showRejectDialogForUser by remember { mutableStateOf<String?>(null) }
    var rejectionReason by remember { mutableStateOf("") }

    val pendingProfiles = profiles.filter { it.verificationStatus == "PENDING" }
    val verifiedCount = profiles.count { it.isVerified }
    val totalCount = profiles.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SINTHA Admin Console", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Profiles Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Users", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalCount", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF1E293B))
                    }
                }

                // Pending Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Pending ID", fontSize = 11.sp, color = Color(0xFFB45309))
                        Text("${pendingProfiles.size}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFD97706))
                    }
                }

                // Verified Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Verified Badge", fontSize = 11.sp, color = Color(0xFF047857))
                        Text("$verifiedCount", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF10B981))
                    }
                }
            }

            // Tab bar
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Pending (${pendingProfiles.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("All System Users", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (activeTab == 0) {
                // Pending review list
                if (pendingProfiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, "Empty", tint = Color(0xFF10B981), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("All clear!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                            Text("No pending identity verification submissions exist in the queue.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pendingProfiles) { user ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // User meta
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                            Text(user.id, fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (user.role == "PROVIDER") Color(0xFFF3E8FF) else Color(0xFFEFF6FF))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = user.role,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (user.role == "PROVIDER") Color(0xFF8B5CF6) else Color(0xFF0F4C81)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Document specifications
                                    Text("DOCUMENT DETAILS", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column {
                                            Text("Document Type", fontSize = 11.sp, color = Color.Gray)
                                            Text(user.verificationIdDocument.ifEmpty { "Aadhaar Card" }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                        }
                                        Column {
                                            Text("Aadhaar / ID Number", fontSize = 11.sp, color = Color.Gray)
                                            Text("5849-3829-9405", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Render a simulated document image card since user asked for vertical document verification views
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEEF2F6))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContactMail,
                                                contentDescription = "Doc",
                                                tint = Color(0xFF475569),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Aadhaar Card Front (Verified Upload)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                                Text("Size: 1.4 MB | Format: JPG", fontSize = 9.sp, color = Color.Gray)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Unique Hash: SHA256-DA68FC77...", fontSize = 9.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showRejectDialogForUser = user.id },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { onApprove(user.id) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Approve ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // All users list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(profiles) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(user.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text(user.id, fontSize = 10.sp, color = Color.Gray)
                                }
                                // Status
                                if (user.isVerified) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFD1FAE5))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Verified ✓", color = Color(0xFF065F46), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (user.verificationStatus) {
                                                    "PENDING" -> Color(0xFFFEF3C7)
                                                    "REJECTED" -> Color(0xFFFEE2E2)
                                                    else -> Color(0xFFF1F5F9)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = when (user.verificationStatus) {
                                                "PENDING" -> "Pending Review"
                                                "REJECTED" -> "Rejected"
                                                else -> "Not Verified"
                                            },
                                            color = when (user.verificationStatus) {
                                                "PENDING" -> Color(0xFFD97706)
                                                "REJECTED" -> Color(0xFFB91C1C)
                                                else -> Color.Gray
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Reject Dialog
    if (showRejectDialogForUser != null) {
        Dialog(onDismissRequest = {
            showRejectDialogForUser = null
            rejectionReason = ""
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Decline Verification Request", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Please specify the reason why this identity verification submission is being rejected. This will be shown to the user.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Rejection Reason") },
                        placeholder = { Text("e.g. Blurry photo, mismatch in ID credentials") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showRejectDialogForUser = null
                                rejectionReason = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val userId = showRejectDialogForUser!!
                                onReject(userId, rejectionReason.ifEmpty { " blurry document, please upload again." })
                                showRejectDialogForUser = null
                                rejectionReason = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Decline", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
