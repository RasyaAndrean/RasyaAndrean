/**
 * Android Mobile Application - Cryptocurrency Portfolio Tracker
 * Author: Rasya Andrean
 * Description: Modern Android app with Jetpack Compose, MVVM, and Clean Architecture
 */

package com.rasyaandrean.cryptoportfolio

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Application Class
@HiltAndroidApp
class CryptoPortfolioApp : Application()

// Main Activity
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CryptoPortfolioTheme {
                CryptoPortfolioNavigation()
            }
        }
    }
}

// Theme and UI
@Composable
fun CryptoPortfolioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00D4FF),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

// Navigation
@Composable
fun CryptoPortfolioNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "portfolio"
    ) {
        composable("portfolio") {
            PortfolioScreen(navController)
        }
        composable("market") {
            MarketScreen(navController)
        }
        composable("transactions") {
            TransactionsScreen(navController)
        }
        composable("settings") {
            SettingsScreen(navController)
        }
    }
}

// Data Models
@Entity(tableName = "cryptocurrencies")
data class Cryptocurrency(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double,
    val marketCap: Long,
    val volume24h: Long,
    val lastUpdated: String
)

@Entity(tableName = "portfolio_holdings")
data class PortfolioHolding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cryptoId: String,
    val symbol: String,
    val amount: Double,
    val averageBuyPrice: Double,
    val totalInvested: Double,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cryptoId: String,
    val symbol: String,
    val type: TransactionType,
    val amount: Double,
    val price: Double,
    val totalValue: Double,
    val fee: Double,
    val notes: String?,
    val timestamp: String
)

enum class TransactionType {
    BUY, SELL, TRANSFER_IN, TRANSFER_OUT
}

// API Models
@Serializable
data class CoinGeckoResponse(
    val id: String,
    val symbol: String,
    val name: String,
    val current_price: Double,
    val price_change_24h: Double,
    val price_change_percentage_24h: Double,
    val market_cap: Long,
    val total_volume: Long,
    val last_updated: String
)

// Database
@Dao
interface CryptocurrencyDao {
    @Query("SELECT * FROM cryptocurrencies ORDER BY market_cap DESC")
    fun getAllCryptocurrencies(): Flow<List<Cryptocurrency>>

    @Query("SELECT * FROM cryptocurrencies WHERE id = :id")
    suspend fun getCryptocurrencyById(id: String): Cryptocurrency?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCryptocurrencies(cryptos: List<Cryptocurrency>)

    @Query("DELETE FROM cryptocurrencies")
    suspend fun deleteAllCryptocurrencies()
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio_holdings")
    fun getAllHoldings(): Flow<List<PortfolioHolding>>

    @Query("SELECT * FROM portfolio_holdings WHERE cryptoId = :cryptoId")
    suspend fun getHoldingByCryptoId(cryptoId: String): PortfolioHolding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: PortfolioHolding)

    @Update
    suspend fun updateHolding(holding: PortfolioHolding)

    @Delete
    suspend fun deleteHolding(holding: PortfolioHolding)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE cryptoId = :cryptoId ORDER BY timestamp DESC")
    fun getTransactionsByCrypto(cryptoId: String): Flow<List<Transaction>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
}

@Database(
    entities = [Cryptocurrency::class, PortfolioHolding::class, Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CryptoDatabase : RoomDatabase() {
    abstract fun cryptocurrencyDao(): CryptocurrencyDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun transactionDao(): TransactionDao
}

class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(type: String): TransactionType = TransactionType.valueOf(type)
}

// API Service
interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarketData(
        @Query("vs_currency") currency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false
    ): List<CoinGeckoResponse>
}

// Repository
@Singleton
class CryptoRepository @Inject constructor(
    private val api: CoinGeckoApi,
    private val database: CryptoDatabase
) {
    private val cryptocurrencyDao = database.cryptocurrencyDao()
    private val portfolioDao = database.portfolioDao()
    private val transactionDao = database.transactionDao()

    fun getAllCryptocurrencies(): Flow<List<Cryptocurrency>> =
        cryptocurrencyDao.getAllCryptocurrencies()

    fun getAllHoldings(): Flow<List<PortfolioHolding>> =
        portfolioDao.getAllHoldings()

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    suspend fun refreshMarketData(): Result<Unit> = try {
        val response = api.getMarketData()
        val cryptocurrencies = response.map { coin ->
            Cryptocurrency(
                id = coin.id,
                symbol = coin.symbol,
                name = coin.name,
                currentPrice = coin.current_price,
                priceChange24h = coin.price_change_24h,
                priceChangePercentage24h = coin.price_change_percentage_24h,
                marketCap = coin.market_cap,
                volume24h = coin.total_volume,
                lastUpdated = coin.last_updated
            )
        }

        cryptocurrencyDao.deleteAllCryptocurrencies()
        cryptocurrencyDao.insertCryptocurrencies(cryptocurrencies)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
        updatePortfolioHolding(transaction)
    }

    private suspend fun updatePortfolioHolding(transaction: Transaction) {
        val existingHolding = portfolioDao.getHoldingByCryptoId(transaction.cryptoId)

        when (transaction.type) {
            TransactionType.BUY -> {
                if (existingHolding != null) {
                    val newAmount = existingHolding.amount + transaction.amount
                    val newTotalInvested = existingHolding.totalInvested + transaction.totalValue
                    val newAveragePrice = newTotalInvested / newAmount

                    portfolioDao.updateHolding(
                        existingHolding.copy(
                            amount = newAmount,
                            averageBuyPrice = newAveragePrice,
                            totalInvested = newTotalInvested,
                            updatedAt = LocalDateTime.now().toString()
                        )
                    )
                } else {
                    portfolioDao.insertHolding(
                        PortfolioHolding(
                            cryptoId = transaction.cryptoId,
                            symbol = transaction.symbol,
                            amount = transaction.amount,
                            averageBuyPrice = transaction.price,
                            totalInvested = transaction.totalValue,
                            createdAt = LocalDateTime.now().toString(),
                            updatedAt = LocalDateTime.now().toString()
                        )
                    )
                }
            }
            TransactionType.SELL -> {
                existingHolding?.let { holding ->
                    val newAmount = holding.amount - transaction.amount
                    val proportionSold = transaction.amount / holding.amount
                    val newTotalInvested = holding.totalInvested * (1 - proportionSold)

                    if (newAmount > 0) {
                        portfolioDao.updateHolding(
                            holding.copy(
                                amount = newAmount,
                                totalInvested = newTotalInvested,
                                updatedAt = LocalDateTime.now().toString()
                            )
                        )
                    } else {
                        portfolioDao.deleteHolding(holding)
                    }
                }
            }
            TransactionType.TRANSFER_IN -> {
                // Handle transfer in logic
            }
            TransactionType.TRANSFER_OUT -> {
                // Handle transfer out logic
            }
        }
    }
}

// ViewModels
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: CryptoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        loadPortfolioData()
        refreshMarketData()
    }

    private fun loadPortfolioData() {
        viewModelScope.launch {
            combine(
                repository.getAllHoldings(),
                repository.getAllCryptocurrencies()
            ) { holdings, cryptos ->
                val cryptoMap = cryptos.associateBy { it.id }
                val portfolioItems = holdings.mapNotNull { holding ->
                    cryptoMap[holding.cryptoId]?.let { crypto ->
                        PortfolioItem(
                            holding = holding,
                            cryptocurrency = crypto,
                            currentValue = holding.amount * crypto.currentPrice,
                            profitLoss = (holding.amount * crypto.currentPrice) - holding.totalInvested,
                            profitLossPercentage = ((holding.amount * crypto.currentPrice) - holding.totalInvested) / holding.totalInvested * 100
                        )
                    }
                }

                val totalValue = portfolioItems.sumOf { it.currentValue }
                val totalInvested = portfolioItems.sumOf { it.holding.totalInvested }
                val totalProfitLoss = totalValue - totalInvested
                val totalProfitLossPercentage = if (totalInvested > 0) (totalProfitLoss / totalInvested * 100) else 0.0

                PortfolioUiState(
                    portfolioItems = portfolioItems,
                    totalValue = totalValue,
                    totalInvested = totalInvested,
                    totalProfitLoss = totalProfitLoss,
                    totalProfitLossPercentage = totalProfitLossPercentage,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            repository.refreshMarketData()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message
                    )
                }
        }
    }
}

data class PortfolioUiState(
    val portfolioItems: List<PortfolioItem> = emptyList(),
    val totalValue: Double = 0.0,
    val totalInvested: Double = 0.0,
    val totalProfitLoss: Double = 0.0,
    val totalProfitLossPercentage: Double = 0.0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

data class PortfolioItem(
    val holding: PortfolioHolding,
    val cryptocurrency: Cryptocurrency,
    val currentValue: Double,
    val profitLoss: Double,
    val profitLossPercentage: Double
)

// UI Screens
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    navController: NavController,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                actions = {
                    IconButton(onClick = { viewModel.refreshMarketData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PortfolioSummaryCard(
                    totalValue = uiState.totalValue,
                    totalProfitLoss = uiState.totalProfitLoss,
                    totalProfitLossPercentage = uiState.totalProfitLossPercentage
                )
            }

            items(uiState.portfolioItems) { item ->
                PortfolioItemCard(item = item)
            }

            if (uiState.portfolioItems.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyPortfolioCard()
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PortfolioSummaryCard(
    totalValue: Double,
    totalProfitLoss: Double,
    totalProfitLossPercentage: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Total Portfolio Value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Text(
                    text = formatCurrency(totalValue),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (totalProfitLoss >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (totalProfitLoss >= 0) Color.Green else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "${formatCurrency(totalProfitLoss)} (${formatPercentage(totalProfitLossPercentage)})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (totalProfitLoss >= 0) Color.Green else Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PortfolioItemCard(item: PortfolioItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.cryptocurrency.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.cryptocurrency.symbol.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatCurrency(item.currentValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatCurrency(item.profitLoss)} (${formatPercentage(item.profitLossPercentage)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.profitLoss >= 0) Color.Green else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Holdings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${item.holding.amount} ${item.cryptocurrency.symbol.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Avg. Buy Price",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatCurrency(item.holding.averageBuyPrice),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPortfolioCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your portfolio is empty",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Start by adding your first cryptocurrency transaction",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    NavigationBar {
        val items = listOf(
            BottomNavItem("Portfolio", Icons.Default.AccountBalanceWallet, "portfolio"),
            BottomNavItem("Market", Icons.Default.TrendingUp, "market"),
            BottomNavItem("Transactions", Icons.Default.Receipt, "transactions"),
            BottomNavItem("Settings", Icons.Default.Settings, "settings")
        )

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = false, // Simplified for example
                onClick = { navController.navigate(item.route) }
            )
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

// Placeholder screens
@Composable
fun MarketScreen(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Market Screen - Coming Soon!")
    }
}

@Composable
fun TransactionsScreen(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Transactions Screen - Coming Soon!")
    }
}

@Composable
fun SettingsScreen(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings Screen - Coming Soon!")
    }
}

// Utility Functions
fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(amount)
}

fun formatPercentage(percentage: Double): String {
    return "${BigDecimal(percentage).setScale(2, RoundingMode.HALF_UP)}%"
}

// Dependency Injection
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): CryptoDatabase {
        return Room.databaseBuilder(
            context,
            CryptoDatabase::class.java,
            "crypto_database"
        ).build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(retrofit: Retrofit): CoinGeckoApi {
        return retrofit.create(CoinGeckoApi::class.java)
    }
}

// Security Event Data Class
data class SecurityEvent(
    val eventType: String,
    val timestamp: Long,
    val details: String,
    val severity: String,
    val source: String
)

// Enhanced Analytics Data Classes
data class PortfolioAnalytics(
    val totalValue: Double,
    val totalProfitLoss: Double,
    val profitLossPercentage: Double,
    val holdingsCount: Int,
    val securityMetrics: Map<String, Any>,
    val recentSecurityEvents: List<SecurityEvent>,
    val anomalyScore: Double // 0.0 to 1.0
)

// Security Monitoring Service
@Singleton
class SecurityMonitoringService @Inject constructor() {
    private val securityEvents = mutableListOf<SecurityEvent>()

    fun logSecurityEvent(
        eventType: String,
        details: String,
        severity: String,
        source: String
    ) {
        val event = SecurityEvent(
            eventType = eventType,
            timestamp = System.currentTimeMillis(),
            details = details,
            severity = severity,
            source = source
        )

        synchronized(securityEvents) {
            securityEvents.add(event)
            // Keep only last 1000 events
            if (securityEvents.size > 1000) {
                securityEvents.subList(0, securityEvents.size - 1000).clear()
            }
        }

        // Log the security event
        println("Security Event: $eventType - $severity - $source - $details")
    }

    fun getRecentSecurityEvents(limit: Int = 10): List<SecurityEvent> {
        synchronized(securityEvents) {
            return securityEvents.sortedByDescending { it.timestamp }.take(limit)
        }
    }

    fun getSecurityMetrics(): Map<String, Any> {
        synchronized(securityEvents) {
            val severityCounts = securityEvents.groupingBy { it.severity }.eachCount()
            val typeCounts = securityEvents.groupingBy { it.eventType }.eachCount()
            val recentEvents = securityEvents.count {
                System.currentTimeMillis() - it.timestamp < 3600000 // Last hour
            }

            return mapOf(
                "severityCounts" to severityCounts,
                "typeCounts" to typeCounts,
                "recentEvents" to recentEvents
            )
        }
    }

    fun reset() {
        synchronized(securityEvents) {
            securityEvents.clear()
        }
    }
}

// Enhanced Repository with Security Monitoring
@Singleton
class EnhancedCryptoRepository @Inject constructor(
    private val api: CoinGeckoApi,
    private val database: CryptoDatabase,
    private val securityMonitoringService: SecurityMonitoringService
) {
    private val cryptocurrencyDao = database.cryptocurrencyDao()
    private val portfolioDao = database.portfolioDao()
    private val transactionDao = database.transactionDao()

    fun getAllCryptocurrencies(): Flow<List<Cryptocurrency>> =
        cryptocurrencyDao.getAllCryptocurrencies()

    fun getAllHoldings(): Flow<List<PortfolioHolding>> =
        portfolioDao.getAllHoldings()

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    suspend fun refreshMarketData(): Result<Unit> = try {
        val response = api.getMarketData()
        val cryptocurrencies = response.map { coin ->
            Cryptocurrency(
                id = coin.id,
                symbol = coin.symbol,
                name = coin.name,
                currentPrice = coin.current_price,
                priceChange24h = coin.price_change_24h,
                priceChangePercentage24h = coin.price_change_percentage_24h,
                marketCap = coin.market_cap,
                volume24h = coin.total_volume,
                lastUpdated = coin.last_updated
            )
        }

        // Security check: Validate data integrity
        validateMarketData(cryptocurrencies)

        cryptocurrencyDao.deleteAllCryptocurrencies()
        cryptocurrencyDao.insertCryptocurrencies(cryptocurrencies)
        Result.success(Unit)
    } catch (e: Exception) {
        securityMonitoringService.logSecurityEvent(
            "market_data_error",
            "Failed to refresh market data: ${e.message}",
            "medium",
            "CoinGeckoApi"
        )
        Result.failure(e)
    }

    private fun validateMarketData(cryptocurrencies: List<Cryptocurrency>) {
        // Check for suspicious price changes
        cryptocurrencies.forEach { crypto ->
            if (Math.abs(crypto.priceChangePercentage24h) > 50.0) {
                securityMonitoringService.logSecurityEvent(
                    "extreme_price_change",
                    "Extreme price change for ${crypto.name}: ${crypto.priceChangePercentage24h}%",
                    "medium",
                    "MarketData"
                )
            }
        }

        // Check for data consistency
        val uniqueIds = cryptocurrencies.map { it.id }.toSet()
        if (uniqueIds.size != cryptocurrencies.size) {
            securityMonitoringService.logSecurityEvent(
                "duplicate_data",
                "Duplicate cryptocurrency entries detected",
                "high",
                "MarketData"
            )
        }
    }

    suspend fun addTransaction(transaction: Transaction) {
        // Security check: Validate transaction
        validateTransaction(transaction)

        transactionDao.insertTransaction(transaction)
        updatePortfolioHolding(transaction)
    }

    private fun validateTransaction(transaction: Transaction) {
        // Check for suspicious transaction amounts
        if (transaction.amount > 1000000) {
            securityMonitoringService.logSecurityEvent(
                "large_transaction",
                "Large transaction amount: ${transaction.amount} ${transaction.symbol}",
                "medium",
                "Transaction"
            )
        }

        // Check for negative amounts
        if (transaction.amount < 0) {
            securityMonitoringService.logSecurityEvent(
                "negative_amount",
                "Negative transaction amount: ${transaction.amount}",
                "high",
                "Transaction"
            )
        }

        // Check for future timestamps
        val currentTime = System.currentTimeMillis()
        if (transaction.timestamp.toLong() > currentTime + 3600000) { // 1 hour in future
            securityMonitoringService.logSecurityEvent(
                "future_timestamp",
                "Transaction with future timestamp: ${transaction.timestamp}",
                "medium",
                "Transaction"
            )
        }
    }

    private suspend fun updatePortfolioHolding(transaction: Transaction) {
        val existingHolding = portfolioDao.getHoldingByCryptoId(transaction.cryptoId)

        when (transaction.type) {
            TransactionType.BUY -> {
                if (existingHolding != null) {
                    val newAmount = existingHolding.amount + transaction.amount
                    val newTotalInvested = existingHolding.totalInvested + transaction.totalValue
                    val newAveragePrice = newTotalInvested / newAmount

                    portfolioDao.updateHolding(
                        existingHolding.copy(
                            amount = newAmount,
                            averageBuyPrice = newAveragePrice,
                            totalInvested = newTotalInvested,
                            updatedAt = LocalDateTime.now().toString()
                        )
                    )
                } else {
                    portfolioDao.insertHolding(
                        PortfolioHolding(
                            cryptoId = transaction.cryptoId,
                            symbol = transaction.symbol,
                            amount = transaction.amount,
                            averageBuyPrice = transaction.price,
                            totalInvested = transaction.totalValue,
                            createdAt = LocalDateTime.now().toString(),
                            updatedAt = LocalDateTime.now().toString()
                        )
                    )
                }
            }
            TransactionType.SELL -> {
                existingHolding?.let { holding ->
                    val newAmount = holding.amount - transaction.amount
                    val proportionSold = transaction.amount / holding.amount
                    val newTotalInvested = holding.totalInvested * (1 - proportionSold)

                    if (newAmount > 0) {
                        portfolioDao.updateHolding(
                            holding.copy(
                                amount = newAmount,
                                totalInvested = newTotalInvested,
                                updatedAt = LocalDateTime.now().toString()
                            )
                        )
                    } else {
                        portfolioDao.deleteHolding(holding)
                    }
                }
            }
            TransactionType.TRANSFER_IN -> {
                // Handle transfer in logic
            }
            TransactionType.TRANSFER_OUT -> {
                // Handle transfer out logic
            }
        }
    }

    suspend fun getPortfolioAnalytics(): PortfolioAnalytics {
        val holdings = portfolioDao.getAllHoldings().first()
        val cryptocurrencies = cryptocurrencyDao.getAllCryptocurrencies().first()

        val cryptoMap = cryptocurrencies.associateBy { it.id }
        var totalValue = 0.0
        var totalInvested = 0.0

        holdings.forEach { holding ->
            cryptoMap[holding.cryptoId]?.let { crypto ->
                totalValue += holding.amount * crypto.currentPrice
                totalInvested += holding.totalInvested
            }
        }

        val totalProfitLoss = totalValue - totalInvested
        val profitLossPercentage = if (totalInvested > 0) (totalProfitLoss / totalInvested) * 100 else 0.0

        // Get security metrics
        val securityMetrics = securityMonitoringService.getSecurityMetrics()
        val recentSecurityEvents = securityMonitoringService.getRecentSecurityEvents(10)

        // Calculate anomaly score
        val anomalyScore = calculateAnomalyScore(holdings, cryptocurrencies)

        return PortfolioAnalytics(
            totalValue = totalValue,
            totalProfitLoss = totalProfitLoss,
            profitLossPercentage = profitLossPercentage,
            holdingsCount = holdings.size,
            securityMetrics = securityMetrics,
            recentSecurityEvents = recentSecurityEvents,
            anomalyScore = anomalyScore
        )
    }

    private fun calculateAnomalyScore(
        holdings: List<PortfolioHolding>,
        cryptocurrencies: List<Cryptocurrency>
    ): Double {
        var score = 0.0

        // Check for high concentration risk (single asset > 50% of portfolio)
        val cryptoMap = cryptocurrencies.associateBy { it.id }
        var totalValue = 0.0
        val assetValues = mutableMapOf<String, Double>()

        holdings.forEach { holding ->
            cryptoMap[holding.cryptoId]?.let { crypto ->
                val value = holding.amount * crypto.currentPrice
                totalValue += value
                assetValues[holding.cryptoId] = value
            }
        }

        assetValues.values.forEach { value ->
            if (totalValue > 0 && (value / totalValue) > 0.5) {
                score += 0.3
            }
        }

        // Check for high number of holdings (diversification)
        if (holdings.size > 20) {
            score += 0.1
        }

        return score.coerceAtMost(1.0)
    }
}

// Enhanced ViewModels
@HiltViewModel
class EnhancedPortfolioViewModel @Inject constructor(
    private val repository: EnhancedCryptoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private val _analytics = MutableStateFlow<PortfolioAnalytics?>(null)
    val analytics: StateFlow<PortfolioAnalytics?> = _analytics.asStateFlow()

    init {
        loadPortfolioData()
        refreshMarketData()
        loadAnalytics()
    }

    private fun loadPortfolioData() {
        viewModelScope.launch {
            combine(
                repository.getAllHoldings(),
                repository.getAllCryptocurrencies()
            ) { holdings, cryptos ->
                val cryptoMap = cryptos.associateBy { it.id }
                val portfolioItems = holdings.mapNotNull { holding ->
                    cryptoMap[holding.cryptoId]?.let { crypto ->
                        PortfolioItem(
                            holding = holding,
                            cryptocurrency = crypto,
                            currentValue = holding.amount * crypto.currentPrice,
                            profitLoss = holding.amount * crypto.currentPrice - holding.totalInvested,
                            profitLossPercentage = if (holding.totalInvested > 0) {
                                ((holding.amount * crypto.currentPrice - holding.totalInvested) / holding.totalInvested) * 100
                            } else 0.0
                        )
                    }
                }

                PortfolioUiState(
                    items = portfolioItems,
                    isLoading = false,
                    error = null
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            try {
                val analyticsData = repository.getPortfolioAnalytics()
                _analytics.value = analyticsData
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            repository.refreshMarketData()
                .onFailure { exception ->
                    _uiState.value = PortfolioUiState(
                        items = emptyList(),
                        isLoading = false,
                        error = exception.message
                    )
                }
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
            loadAnalytics() // Refresh analytics after transaction
        }
    }
}

// Enhanced UI Components
@Composable
fun EnhancedPortfolioScreen(navController: NavController) {
    val viewModel: EnhancedPortfolioViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val analytics by viewModel.analytics.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with analytics summary
        analytics?.let { data ->
            AnalyticsSummaryCard(analytics = data)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Portfolio list
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${uiState.error}")
                }
            }
            uiState.items.isEmpty() -> {
                EmptyPortfolioCard()
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.items) { item ->
                        PortfolioItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsSummaryCard(analytics: PortfolioAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Portfolio Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Anomaly indicator
                if (analytics.anomalyScore > 0.5) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "High Anomaly Score",
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatCurrency(analytics.totalValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "P&L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${formatCurrency(analytics.totalProfitLoss)} (${formatPercentage(analytics.profitLossPercentage)})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (analytics.totalProfitLoss >= 0) Color.Green else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Security metrics
            Text(
                text = "Security Status: ${analytics.recentSecurityEvents.size} events (Score: ${String.format("%.2f", analytics.anomalyScore)})",
                style = MaterialTheme.typography.bodySmall,
                color = if (analytics.anomalyScore > 0.7) Color.Red else
                       if (analytics.anomalyScore > 0.4) Color.Yellow else Color.Green
            )
        }
    }
}

// Enhanced Security Screen
@Composable
fun SecurityScreen(navController: NavController) {
    val viewModel: EnhancedPortfolioViewModel = hiltViewModel()
    val analytics by viewModel.analytics.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Security Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        analytics?.let { data ->
            // Security metrics
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Security Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    (data.securityMetrics["severityCounts"] as? Map<*, *>)?.forEach { (severity, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(severity.toString())
                            Text(count.toString())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent security events
            Text(
                text = "Recent Security Events",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(data.recentSecurityEvents) { event ->
                    SecurityEventItem(event = event)
                }
            }
        }
    }
}

@Composable
fun SecurityEventItem(event: SecurityEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.eventType,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.severity,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (event.severity) {
                        "critical" -> Color.Red
                        "high" -> Color(0xFFFF9800) // Orange
                        "medium" -> Color.Yellow
                        else -> Color.Green
                    }
                )
            }

            Text(
                text = event.details,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = java.time.Instant.ofEpochMilli(event.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// Enhanced Navigation
@Composable
fun EnhancedCryptoPortfolioNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "portfolio"
    ) {
        composable("portfolio") {
            EnhancedPortfolioScreen(navController)
        }
        composable("market") {
            MarketScreen(navController)
        }
        composable("transactions") {
            TransactionsScreen(navController)
        }
        composable("security") {
            SecurityScreen(navController)
        }
        composable("settings") {
            SettingsScreen(navController)
        }
    }
}
