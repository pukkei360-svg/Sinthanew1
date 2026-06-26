package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        ProviderEntity::class,
        BookingEntity::class,
        SavedProviderEntity::class,
        SavedAddressEntity::class,
        ChatMessageEntity::class,
        JobPostEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun providerDao(): ProviderDao
    abstract fun bookingDao(): BookingDao
    abstract fun savedProviderDao(): SavedProviderDao
    abstract fun savedAddressDao(): SavedAddressDao
    abstract fun chatDao(): ChatDao
    abstract fun jobPostDao(): JobPostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sintha_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val providerDao = database.providerDao()
            val jobPostDao = database.jobPostDao()
            val userProfileDao = database.userProfileDao()

            // Pre-populate some realistic service providers in Manipur
            val initialProviders = listOf(
                ProviderEntity(
                    id = "provider_tomba",
                    name = "Tomba Singh",
                    category = "Home Services & Repairs",
                    subcategory = "Electrician",
                    location = "Imphal West",
                    rate = 350.0,
                    rateUnit = "hour",
                    rating = 4.8f,
                    reviewCount = 24,
                    description = "Certified electrician with 8+ years experience. Expert in home wiring, appliance repairs, and smart home setup. Prompt service.",
                    isVerified = true,
                    isPro = true,
                    phone = "+91 98765 43210",
                    experienceYears = 8
                ),
                ProviderEntity(
                    id = "provider_bembem",
                    name = "Bembem Devi",
                    category = "Beauty & Wellness",
                    subcategory = "Bridal Makeup Artist",
                    location = "Imphal East",
                    rate = 4500.0,
                    rateUnit = "service",
                    rating = 4.9f,
                    reviewCount = 42,
                    description = "Professional makeup artist specializing in traditional Meitei bridal makeup and modern hair styling. Over 6 years of experience.",
                    isVerified = true,
                    isPro = true,
                    phone = "+91 87654 32109",
                    experienceYears = 6
                ),
                ProviderEntity(
                    id = "provider_thoi",
                    name = "Thoiba Sana",
                    category = "Home Services & Repairs",
                    subcategory = "Plumber",
                    location = "Thoubal",
                    rate = 250.0,
                    rateUnit = "hour",
                    rating = 4.6f,
                    reviewCount = 15,
                    description = "Reliable plumbing expert for pipe leaks, bathroom installations, water tank cleaning, and sanitary fitting repair.",
                    isVerified = true,
                    isPro = false,
                    phone = "+91 76543 21098",
                    experienceYears = 4
                ),
                ProviderEntity(
                    id = "provider_robert",
                    name = "Robert Kamei",
                    category = "Education & Tutors",
                    subcategory = "Mathematics Tutor (Class IX-XII)",
                    location = "Churachandpur",
                    rate = 400.0,
                    rateUnit = "hour",
                    rating = 4.7f,
                    reviewCount = 18,
                    description = "M.Sc. in Mathematics. Passionate tutor offering clear explanations, study materials, and customized exam prep support.",
                    isVerified = false,
                    isPro = true,
                    phone = "+91 65432 10987",
                    experienceYears = 5
                ),
                ProviderEntity(
                    id = "provider_chao",
                    name = "Chaoba Singh",
                    category = "Cleaning & Sanitization",
                    subcategory = "Deep Home Cleaning",
                    location = "Imphal West",
                    rate = 1800.0,
                    rateUnit = "service",
                    rating = 4.5f,
                    reviewCount = 10,
                    description = "Complete deep home cleaning, sofa sanitization, kitchen chimney cleaning, and eco-friendly chemicals used.",
                    isVerified = true,
                    isPro = false,
                    phone = "+91 54321 09876",
                    experienceYears = 3
                ),
                ProviderEntity(
                    id = "provider_somorendro",
                    name = "Somorendro L.",
                    category = "Event Support",
                    subcategory = "Wedding Photographer",
                    location = "Kakching",
                    rate = 8000.0,
                    rateUnit = "day",
                    rating = 4.9f,
                    reviewCount = 31,
                    description = "Candid wedding photography, pre-wedding shoots, and cinematic event coverage. Delivering premium memories across Manipur.",
                    isVerified = true,
                    isPro = true,
                    phone = "+91 99887 76655",
                    experienceYears = 7
                ),
                ProviderEntity(
                    id = "provider_momo",
                    name = "Momocha Luwang",
                    category = "Home Services & Repairs",
                    subcategory = "Appliance Repair Technician",
                    location = "Imphal West",
                    rate = 300.0,
                    rateUnit = "hour",
                    rating = 4.4f,
                    reviewCount = 8,
                    description = "Expert in fixing refrigerators, washing machines, microwaves, and air conditioners. Quick turnaround and genuine parts.",
                    isVerified = false,
                    isPro = false,
                    phone = "+91 91122 33445",
                    experienceYears = 3
                )
            )
            providerDao.insertProviders(initialProviders)

            // Pre-populate some realistic client job posts
            val initialJobs = listOf(
                JobPostEntity(
                    clientId = "client_leima",
                    clientName = "Leimarenbi Chanu",
                    title = "Need tutor for Class X Chemistry",
                    description = "Looking for an experienced Chemistry tutor for home tuition in Imphal West near Singjamei. 3 days a week, preparing for COHSEM board exams.",
                    category = "Education & Tutors",
                    budget = 3500.0,
                    location = "Imphal West",
                    phone = "+91 88990 01122"
                ),
                JobPostEntity(
                    clientId = "client_iboyaima",
                    clientName = "Iboyaima Singh",
                    title = "Urgent plumbing work for overhead tank overflow",
                    description = "Our water tank automatic sensor failed and there is a major leak in the inlet pipe. Need a plumber to replace the valve and sensor immediately.",
                    category = "Home Services & Repairs",
                    budget = 800.0,
                    location = "Imphal East",
                    phone = "+91 77665 54433"
                ),
                JobPostEntity(
                    clientId = "client_yaiphabi",
                    clientName = "Yaiphabi Devi",
                    title = "Sofa and curtain deep cleaning",
                    description = "Need complete sanitization and shampoo cleaning for a 5-seater L-shaped sofa and 6 heavy window curtains before a family gathering next week.",
                    category = "Cleaning & Sanitization",
                    budget = 2000.0,
                    location = "Bishnupur",
                    phone = "+91 66554 43322"
                )
            )
            for (job in initialJobs) {
                jobPostDao.insertJob(job)
            }

            // Create a default login account
            val defaultClient = UserProfile(
                id = "aicrafts56@gmail.com",
                name = "Irabot Laishram",
                phone = "+91 93621 12345",
                role = "CLIENT",
                isPro = false,
                referralCode = "IRABOT30",
                referredBy = "",
                referralCount = 2,
                balance = 119.4,
                isVerified = true
            )
            userProfileDao.insertProfile(defaultClient)
        }
    }
}
