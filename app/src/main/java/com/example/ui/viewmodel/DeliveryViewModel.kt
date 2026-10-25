package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ADMIN_SECRET_CODE
import com.example.data.model.ADMIN_USERNAME
import com.example.data.model.CashSettlement
import com.example.data.model.CompanyFinancialKpis
import com.example.data.model.DeliveryOrder
import com.example.data.model.DriverFinancialSummary
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.repository.DeliveryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryViewModel(
    private val repository: DeliveryRepository,
    val currentUserId: String,
    val currentUserDisplayName: String,
    val currentUserEmail: String = ""
) : ViewModel() {

    // Admin section is ALWAYS LOCKED when opening the app; user starts in DRIVER (مەندووب) mode
    private val _isAdminUnlocked = MutableStateFlow(false)
    val isAdminUnlocked: StateFlow<Boolean> = _isAdminUnlocked.asStateFlow()

    private val _actualUserRole = MutableStateFlow(StaffRole.DRIVER)
    val actualUserRole: StateFlow<StaffRole> = _actualUserRole.asStateFlow()

    private val _activeViewRole = MutableStateFlow(StaffRole.DRIVER)
    val activeViewRole: StateFlow<StaffRole> = _activeViewRole.asStateFlow()

    private val _isProfileReady = MutableStateFlow(false)

    val ordersState: StateFlow<UiState<List<DeliveryOrder>>> = _isProfileReady
        .filter { it }
        .flatMapLatest {
            combine(_activeViewRole, _isAdminUnlocked) { role, unlocked ->
                role == StaffRole.OWNER && unlocked
            }
        }
        .flatMapLatest { isAdminModeActive ->
            if (isAdminModeActive) {
                repository.observeCompanyOrders()
            } else {
                repository.observeDriverOrders(currentUserId)
            }
        }
        .map<List<DeliveryOrder>, UiState<List<DeliveryOrder>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing orders", error)
            emit(UiState.Error(error.localizedMessage ?: "هەڵە لە هێنانی وەسڵەکان"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val settlementsState: StateFlow<UiState<List<CashSettlement>>> = _isProfileReady
        .filter { it }
        .flatMapLatest {
            combine(_activeViewRole, _isAdminUnlocked) { role, unlocked ->
                role == StaffRole.OWNER && unlocked
            }
        }
        .flatMapLatest { isAdminModeActive ->
            if (isAdminModeActive) {
                repository.observeCompanySettlements()
            } else {
                repository.observeDriverSettlements(currentUserId)
            }
        }
        .map<List<CashSettlement>, UiState<List<CashSettlement>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing settlements", error)
            emit(UiState.Error(error.localizedMessage ?: "هەڵە لە هێنانی تۆماری حیسابات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val staffState: StateFlow<UiState<List<StaffMember>>> = _isProfileReady
        .filter { it }
        .flatMapLatest {
            repository.observeCompanyStaff()
        }
        .map<List<StaffMember>, UiState<List<StaffMember>>> { staffList ->
            // Note: Do NOT auto-switch to OWNER from staffList; Admin remains locked until DXP + DXPPOST is entered
            UiState.Success(staffList)
        }
        .catch { error ->
            Log.w(TAG, "Error observing staff", error)
            emit(UiState.Error(error.localizedMessage ?: "هەڵە لە هێنانی شۆفێرەکان"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatusFilter = MutableStateFlow<OrderStatus?>(null)
    val selectedStatusFilter: StateFlow<OrderStatus?> = _selectedStatusFilter.asStateFlow()

    private val _selectedDriverFilterId = MutableStateFlow<String?>(null)
    val selectedDriverFilterId: StateFlow<String?> = _selectedDriverFilterId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureCurrentUserProfile(
                defaultName = currentUserDisplayName.ifBlank { "مەندووبی DXP POST" },
                defaultEmail = currentUserEmail
            )
            // Always start in DRIVER mode with Admin locked when opening the app
            _isAdminUnlocked.value = false
            _actualUserRole.value = StaffRole.DRIVER
            _activeViewRole.value = StaffRole.DRIVER
            _isProfileReady.value = true
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: OrderStatus?) {
        _selectedStatusFilter.value = status
    }

    fun setDriverFilter(driverId: String?) {
        _selectedDriverFilterId.value = driverId
    }

    fun switchActiveRole(role: StaffRole) {
        if (role == StaffRole.DRIVER) {
            // Switching back to Driver locks the Admin section again so it requires DXP + DXPPOST next time
            _isAdminUnlocked.value = false
            _actualUserRole.value = StaffRole.DRIVER
            _activeViewRole.value = StaffRole.DRIVER
            _actionMessage.value = "بەشی ئەدمین قفڵ درا و گەڕایتەوە بۆ بەشی مەندووب 🔒"
        } else if (_isAdminUnlocked.value) {
            _activeViewRole.value = StaffRole.OWNER
        }
    }

    fun unlockAdminWithCredentials(
        enteredUsername: String,
        enteredPassword: String,
        onUnlocked: () -> Unit = {},
        onInvalidCredentials: (String) -> Unit = {}
    ) {
        val cleanUser = enteredUsername.trim().uppercase()
        val cleanPass = enteredPassword.trim().uppercase()
        if (cleanUser != ADMIN_USERNAME || cleanPass != ADMIN_SECRET_CODE) {
            onInvalidCredentials("یوزەر یان پاسۆرد هەڵەیە! تکایە یوزەری (DXP) و پاسۆردی (DXPPOST) بنووسە")
            return
        }
        viewModelScope.launch {
            val res = repository.unlockAdminWithCredentials(
                username = cleanUser,
                password = cleanPass,
                defaultName = currentUserDisplayName.ifBlank { "ئەدمینی DXP POST" },
                defaultEmail = currentUserEmail
            )
            res.onSuccess {
                _isAdminUnlocked.value = true
                _actualUserRole.value = StaffRole.OWNER
                _activeViewRole.value = StaffRole.OWNER
                _actionMessage.value = "قفڵی بەشی ئەدمین کرایەوە بە سەرکەوتوویی ✓"
                onUnlocked()
            }.onFailure { err ->
                onInvalidCredentials(err.localizedMessage ?: "هەڵە لە کردنەوەی قفڵی ئەدمین")
            }
        }
    }

    fun createNewOrder(
        trackingCode: String,
        senderName: String,
        senderPhone: String,
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        packageImageBase64: String,
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String,
        assignedDriverName: String
    ) {
        viewModelScope.launch {
            // If a Driver is creating a package, ensure it is assigned to their own UID
            val effectiveDriverId = if (_actualUserRole.value != StaffRole.OWNER) {
                currentUserId
            } else {
                assignedDriverId
            }
            val effectiveDriverName = if (_actualUserRole.value != StaffRole.OWNER) {
                assignedDriverName.ifBlank { currentUserDisplayName.ifBlank { "شۆفێر" } }
            } else {
                assignedDriverName
            }

            val result = repository.createOrder(
                trackingCode = trackingCode,
                senderName = senderName,
                senderPhone = senderPhone,
                customerName = customerName,
                customerPhone = customerPhone,
                cityArea = cityArea,
                addressDetails = addressDetails,
                notes = notes,
                packageImageBase64 = packageImageBase64,
                itemPriceIqd = itemPriceIqd,
                deliveryFeeIqd = deliveryFeeIqd,
                assignedDriverId = effectiveDriverId,
                assignedDriverName = effectiveDriverName
            )
            result.onSuccess {
                _actionMessage.value = "کاڵا / وەسڵی نوێ بە سەرکەوتوویی تۆمارکرا"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە: ${err.localizedMessage ?: "تۆمار نەکرا"}"
            }
        }
    }

    fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        returnReason: String = ""
    ) {
        viewModelScope.launch {
            val result = repository.updateOrderStatus(orderId, newStatus, returnReason)
            result.onSuccess {
                _actionMessage.value = "دۆخی وەسڵ گۆڕدرا بۆ: ${newStatus.kurdishLabel}"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە گۆڕینی دۆخ: ${err.localizedMessage}"
            }
        }
    }

    fun assignDriver(
        order: DeliveryOrder,
        driverId: String,
        driverName: String
    ) {
        viewModelScope.launch {
            val result = repository.assignDriverToOrder(
                orderId = order.id,
                driverId = driverId,
                driverName = driverName,
                currentStatus = order.status
            )
            result.onSuccess {
                val msg = if (driverName.isBlank()) {
                    "شۆفێر لەسەر وەسڵ لابرا"
                } else {
                    "وەسڵەکە سپێردرا بە شۆفێر: $driverName"
                }
                _actionMessage.value = msg
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە دیاریکردنی شۆفێر: ${err.localizedMessage}"
            }
        }
    }

    fun updateFullOrder(
        order: DeliveryOrder,
        senderName: String,
        senderPhone: String,
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        packageImageBase64: String,
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String,
        assignedDriverName: String,
        status: String,
        returnReason: String,
        isSettled: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.updateFullOrder(
                orderId = order.id,
                senderName = senderName,
                senderPhone = senderPhone,
                customerName = customerName,
                customerPhone = customerPhone,
                cityArea = cityArea,
                addressDetails = addressDetails,
                notes = notes,
                packageImageBase64 = packageImageBase64,
                itemPriceIqd = itemPriceIqd,
                deliveryFeeIqd = deliveryFeeIqd,
                assignedDriverId = assignedDriverId,
                assignedDriverName = assignedDriverName,
                status = status,
                returnReason = returnReason,
                isSettled = isSettled
            )
            result.onSuccess {
                _actionMessage.value = "زانیارییەکانی وەسڵ نوێکرانەوە"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە نوێکردنەوە: ${err.localizedMessage}"
            }
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            val result = repository.deleteOrder(orderId)
            result.onSuccess {
                _actionMessage.value = "وەسڵەکە سڕایەوە"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە سڕینەوە: ${err.localizedMessage}"
            }
        }
    }

    fun saveStaffMember(
        staffId: String = "",
        name: String,
        phone: String,
        email: String = "",
        role: StaffRole,
        vehicleInfo: String,
        active: Boolean = true,
        existingMember: StaffMember? = null
    ) {
        viewModelScope.launch {
            val result = repository.createOrUpdateStaffMember(
                staffId = staffId,
                name = name,
                phone = phone,
                email = email,
                role = role.code,
                vehicleInfo = vehicleInfo,
                active = active,
                existingMember = existingMember
            )
            result.onSuccess {
                if (resolvedIsSelf(staffId, existingMember)) {
                    _actualUserRole.value = role
                    _activeViewRole.value = role
                }
                _actionMessage.value = "زانیاری بەکارهێنەر/شۆفێر پاشەکەوت کرا"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە: ${err.localizedMessage}"
            }
        }
    }

    private fun resolvedIsSelf(staffId: String, existingMember: StaffMember?): Boolean {
        return staffId == currentUserId || existingMember?.id == currentUserId
    }

    fun settleDriverOrders(summary: DriverFinancialSummary) {
        viewModelScope.launch {
            val result = repository.settleDriverOrders(
                driverId = summary.driverId,
                driverName = summary.driverName,
                unsettledOrders = summary.unsettledOrders
            )
            result.onSuccess { count ->
                _actionMessage.value =
                    "حیسابی شۆفێر (${summary.driverName}) بۆ $count وەسڵ پاکتاو کرا و لە حیسابات تۆمارکرا"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە پاکتاوکردن: ${err.localizedMessage}"
            }
        }
    }

    fun seedFullTeamRoster() {
        viewModelScope.launch {
            val res = repository.seedTwoAdminsAndTenDrivers()
            res.onSuccess {
                _actionMessage.value = "لیستی ئەدمین و شۆفێرانی DXP POST ئامادە کرا"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە ئامادەکردنی ستاف: ${err.localizedMessage}"
            }
        }
    }

    fun seedSampleDxpData() {
        viewModelScope.launch {
            repository.seedTwoAdminsAndTenDrivers()

            repository.createOrder(
                trackingCode = "DXP-1081",
                senderName = "براندی جلوبەرگی شاهانە",
                senderPhone = "07509988776",
                customerName = "ژیار عەبدولڕەحمان",
                customerPhone = "07501122334",
                cityArea = "هەولێر - بەختیاری",
                addressDetails = "نزیک پارکی شانەدەر، کۆڵانی ١٤",
                notes = "پێش گەیشتن پەیوەندی پێوە بکە",
                itemPriceIqd = 45000L,
                deliveryFeeIqd = 4000L,
                assignedDriverId = "drv_dxp_01",
                assignedDriverName = "مەندووب ١ - سۆران محەمەد"
            )

            repository.createOrder(
                trackingCode = "DXP-1082",
                senderName = "فرۆشگای مۆبایلی داهاتوو",
                senderPhone = "07708877665",
                customerName = "هاوژین لەتیف",
                customerPhone = "07703344556",
                cityArea = "سلێمانی - تووی مەلیک",
                addressDetails = "تەنیشت نەخۆشخانەی تایبەت",
                notes = "پاکێجەکە ناسکە و شکانی تێدایە",
                itemPriceIqd = 120000L,
                deliveryFeeIqd = 5000L,
                assignedDriverId = "drv_dxp_02",
                assignedDriverName = "مەندووب ٢ - ئارام کەریم"
            )

            _actionMessage.value = "ئەدمین، شۆفێرەکان و نموونەی وەسڵەکانی DXP POST زیادکران"
        }
    }

    fun calculateCompanyKpis(orders: List<DeliveryOrder>): CompanyFinancialKpis {
        val pending = orders.count {
            it.status == OrderStatus.PENDING.code || it.status == OrderStatus.ASSIGNED.code
        }
        val onTheWay = orders.count { it.status == OrderStatus.ON_THE_WAY.code }
        val deliveredOrders = orders.filter { it.status == OrderStatus.DELIVERED.code }
        val returned = orders.count { it.status == OrderStatus.RETURNED.code }

        val totalCollected = deliveredOrders.sumOf { it.totalAmountIqd }
        val totalItemPrice = deliveredOrders.sumOf { it.itemPriceIqd }
        val totalDeliveryFee = deliveredOrders.sumOf { it.deliveryFeeIqd }
        val unsettledCash = deliveredOrders.filter { !it.isSettled }.sumOf { it.totalAmountIqd }
        val settledCash = deliveredOrders.filter { it.isSettled }.sumOf { it.totalAmountIqd }

        return CompanyFinancialKpis(
            totalOrders = orders.size,
            pendingCount = pending,
            onTheWayCount = onTheWay,
            deliveredCount = deliveredOrders.size,
            returnedCount = returned,
            totalCollectedCashIqd = totalCollected,
            totalItemPriceDeliveredIqd = totalItemPrice,
            totalDeliveryFeeEarnedIqd = totalDeliveryFee,
            unsettledCashWithDriversIqd = unsettledCash,
            settledCashWithOwnerIqd = settledCash
        )
    }

    fun calculateDriverSummaries(
        staffList: List<StaffMember>,
        orders: List<DeliveryOrder>
    ): List<DriverFinancialSummary> {
        val driversById = staffList.associateBy { it.id }.toMutableMap()
        orders.forEach { order ->
            if (order.assignedDriverId.isNotBlank() && !driversById.containsKey(order.assignedDriverId)) {
                driversById[order.assignedDriverId] = StaffMember(
                    id = order.assignedDriverId,
                    name = order.assignedDriverName.ifBlank { "شۆفێر" },
                    role = StaffRole.DRIVER.code,
                    active = true
                )
            }
        }

        return driversById.values
            .filter { it.role == StaffRole.DRIVER.code || orders.any { o -> o.assignedDriverId == it.id } }
            .map { driver ->
                val driverOrders = orders.filter { it.assignedDriverId == driver.id }
                val inProgress = driverOrders.count {
                    it.status == OrderStatus.ASSIGNED.code || it.status == OrderStatus.ON_THE_WAY.code
                }
                val delivered = driverOrders.filter { it.status == OrderStatus.DELIVERED.code }
                val returned = driverOrders.count { it.status == OrderStatus.RETURNED.code }
                val unsettled = delivered.filter { !it.isSettled }
                val settled = delivered.filter { it.isSettled }

                DriverFinancialSummary(
                    driverId = driver.id,
                    driverName = driver.name,
                    phone = driver.phone,
                    vehicleInfo = driver.vehicleInfo,
                    active = driver.active,
                    totalOrdersCount = driverOrders.size,
                    inProgressCount = inProgress,
                    deliveredCount = delivered.size,
                    returnedCount = returned,
                    unsettledOrders = unsettled,
                    unsettledItemPriceIqd = unsettled.sumOf { it.itemPriceIqd },
                    unsettledDeliveryFeeIqd = unsettled.sumOf { it.deliveryFeeIqd },
                    unsettledTotalCashIqd = unsettled.sumOf { it.totalAmountIqd },
                    settledTotalCashIqd = settled.sumOf { it.totalAmountIqd }
                )
            }
            .sortedByDescending { it.unsettledTotalCashIqd }
    }

    private companion object {
        const val TAG = "DeliveryViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
