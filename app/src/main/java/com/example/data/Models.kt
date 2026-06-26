package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String, // e.g., email or phone
    val name: String,
    val phone: String,
    val role: String, // "CLIENT" or "PROVIDER"
    val isPro: Boolean = false,
    val proExpiry: Long = 0L,
    val referralCode: String = "",
    val referredBy: String = "",
    val referralCount: Int = 0,
    val balance: Double = 0.0,
    val isVerified: Boolean = false,
    val profilePictureUrl: String = "",
    val verificationStatus: String = "NOT_STARTED", // "NOT_STARTED", "PENDING", "APPROVED", "REJECTED"
    val verificationIdDocument: String = "",
    val verificationDocFileUri: String = "",
    val rejectionReason: String = ""
) : Serializable

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val location: String, // "Imphal West", "Imphal East", "Thoubal", "Churachandpur", etc.
    val rate: Double,
    val rateUnit: String = "hour", // "hour", "day", "service"
    val rating: Float = 4.5f,
    val reviewCount: Int = 12,
    val description: String,
    val isVerified: Boolean = false,
    val isPro: Boolean = false,
    val phone: String,
    val experienceYears: Int = 3,
    val profilePictureUrl: String = ""
) : Serializable

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String,
    val clientName: String,
    val providerId: String,
    val providerName: String,
    val serviceCategory: String,
    val bookingDate: String,
    val rate: Double,
    val status: String, // "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"
    val address: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "saved_providers")
data class SavedProviderEntity(
    @PrimaryKey val providerId: String,
    val clientId: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_addresses")
data class SavedAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String, // "Home", "Office", "Other"
    val fullAddress: String,
    val landmark: String
) : Serializable

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderId: String,
    val receiverId: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "job_posts")
data class JobPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String,
    val clientName: String,
    val title: String,
    val description: String,
    val category: String,
    val budget: Double,
    val location: String,
    val phone: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
