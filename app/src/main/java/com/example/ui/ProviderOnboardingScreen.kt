package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderOnboardingDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSaveAndSwitch: (
        category: String,
        subcategory: String,
        description: String,
        rate: Double,
        rateUnit: String,
        location: String,
        experienceYears: Int,
        profilePicUri: String?
    ) -> Unit
) {
    var category by remember { mutableStateOf("Home Services & Repairs") }
    var subcategory by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rateAmount by remember { mutableStateOf("") }
    var rateUnit by remember { mutableStateOf("hour") } // "hour", "day", "negotiable"
    var location by remember { mutableStateOf("Imphal West") }
    var experienceYears by remember { mutableStateOf("3") }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedLocation by remember { mutableStateOf(false) }
    var expandedRateUnit by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri.toString()
        }
    }

    val categories = listOf(
        "Home Services & Repairs",
        "Beauty & Wellness",
        "Education & Tutors",
        "Event Support",
        "Cleaning & Sanitization"
    )

    val locations = listOf(
        "Imphal West",
        "Imphal East",
        "Thoubal",
        "Churachandpur",
        "Kakching",
        "Bishnupur",
        "Senapati",
        "Ukhrul"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        content = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Set Up Provider Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF8FAFC))
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header text
                    Text(
                        text = "Become a SINTHA Expert",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = Color(0xFF0F4C81)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Complete your service credentials to start accepting bookings from clients in Manipur.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Profile Photo Upload Box
                    Text(
                        text = "UPLOAD PROFILE PHOTO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .border(2.dp, Color(0xFFCBD5E1), CircleShape)
                            .clickable { imageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedPhotoUri != null) {
                            AsyncImage(
                                model = selectedPhotoUri,
                                contentDescription = "Profile Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (userProfile.profilePictureUrl.isNotEmpty()) {
                            AsyncImage(
                                model = userProfile.profilePictureUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", tint = Color.Gray, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Upload", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }
                    }
                    if (selectedPhotoUri == null && userProfile.profilePictureUrl.isEmpty()) {
                        Text(
                            text = "A clean face portrait builds trust with clients (Required)",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Service Category Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Service Category") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedCategory = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        DropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Service Name / Subcategory
                    OutlinedTextField(
                        value = subcategory,
                        onValueChange = { subcategory = it },
                        label = { Text("Service Name (e.g., Plumber, Home Tutor, Beautician)") },
                        placeholder = { Text("Your primary service expertise") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.HomeRepairService, null, tint = Color.Gray) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rate selection & unit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Billing Unit Selector
                        Box(modifier = Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = when (rateUnit) {
                                    "hour" -> "Per Hour"
                                    "day" -> "Per Day"
                                    else -> "Negotiable"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Rate Structure") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { expandedRateUnit = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            DropdownMenu(
                                expanded = expandedRateUnit,
                                onDismissRequest = { expandedRateUnit = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Per Hour") },
                                    onClick = {
                                        rateUnit = "hour"
                                        expandedRateUnit = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Per Day") },
                                    onClick = {
                                        rateUnit = "day"
                                        expandedRateUnit = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Negotiable") },
                                    onClick = {
                                        rateUnit = "negotiable"
                                        expandedRateUnit = false
                                    }
                                )
                            }
                        }

                        // Rate Amount Field
                        AnimatedVisibility(
                            visible = rateUnit != "negotiable",
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = rateAmount,
                                onValueChange = { rateAmount = it },
                                label = { Text("Rate Amount (₹)") },
                                placeholder = { Text("e.g. 350") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 8.dp)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location Coverage Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Primary District Coverage") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedLocation = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        DropdownMenu(
                            expanded = expandedLocation,
                            onDismissRequest = { expandedLocation = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            locations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        location = loc
                                        expandedLocation = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Experience Years
                    OutlinedTextField(
                        value = experienceYears,
                        onValueChange = { experienceYears = it },
                        label = { Text("Years of Experience") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.Star, null, tint = Color.Gray) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Service Description") },
                        placeholder = { Text("Describe your services, skills, equipment, and how you can help clients. Min 20 characters recommended.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Action buttons
                    Button(
                        onClick = {
                            val rateValue = if (rateUnit == "negotiable") 0.0 else rateAmount.toDoubleOrNull() ?: 0.0
                            onSaveAndSwitch(
                                category,
                                subcategory.ifEmpty { "General Expert" },
                                description.ifEmpty { "Certified service expert dedicated to providing professional and high quality results." },
                                rateValue,
                                rateUnit,
                                location,
                                experienceYears.toIntOrNull() ?: 3,
                                selectedPhotoUri
                            )
                        },
                        enabled = subcategory.isNotEmpty() && (rateUnit == "negotiable" || rateAmount.isNotEmpty()) && description.length >= 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, "Save", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile & Launch Dashboard", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Cancel Setup", color = Color(0xFF64748B))
                    }
                }
            }
        }
    )
}
