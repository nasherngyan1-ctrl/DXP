package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompanyFinancialKpis
import com.example.data.model.DeliveryOrder
import com.example.data.model.DriverFinancialSummary
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.repository.DeliveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class DeliveryViewModel(
    private val repository: DeliveryRepository,
    val currentUserId: String,
    val currentUserDisplayName: String
) : ViewModel() {

    val ordersState: StateFlow<UiState<List<DeliveryOrder>>> = repository.observeCompanyOrders()
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

    val staffState: StateFlow<UiState<List<StaffMember>>> = repository.observeCompanyStaff()
        .map<List<StaffMember>, UiState<List<StaffMember>>> { staffList ->
            val currentMember = staffList.firstOrNull { it.id == currentUserId }
            if (currentMember != null && !_hasInitializedRoleFromProfile) {
                _hasInitializedRoleFromProfile = true
                _activeViewRole.value = StaffRole.fromCode(currentMember.role)
            }
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

    private var _hasInitializedRoleFromProfile = false

    private val _activeViewRole = MutableStateFlow(StaffRole.OWNER)
    val activeViewRole: StateFlow<StaffRole> = _activeViewRole.asStateFlow()

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
                defaultName = currentUserDisplayName.ifBlank { "بەڕێوەبەری DXP POST" },
                preferredRole = StaffRole.OWNER.code
            )
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
        _activeViewRole.value = role
    }

    fun createNewOrder(
        trackingCode: String,
        senderName: String,
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String,
        assignedDriverName: String
    ) {
        viewModelScope.launch {
            val result = repository.createOrder(
                trackingCode = trackingCode,
                senderName = senderName,
                customerName = customerName,
                customerPhone = customerPhone,
                cityArea = cityArea,
                addressDetails = addressDetails,
                notes = notes,
                itemPriceIqd = itemPriceIqd,
                deliveryFeeIqd = deliveryFeeIqd,
                assignedDriverId = assignedDriverId,
                assignedDriverName = assignedDriverName
            )
            result.onSuccess {
                _actionMessage.value = "وەسڵی نوێ بە سەرکەوتوویی تۆمارکرا"
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
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
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
                customerName = customerName,
                customerPhone = customerPhone,
                cityArea = cityArea,
                addressDetails = addressDetails,
                notes = notes,
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
                role = role.code,
                vehicleInfo = vehicleInfo,
                active = active,
                existingMember = existingMember
            )
            result.onSuccess {
                if (resolvedIsSelf(staffId, existingMember)) {
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
            val result = repository.settleDriverOrders(summary.unsettledOrders)
            result.onSuccess { count ->
                _actionMessage.value =
                    "حیسابی شۆفێر (${summary.driverName}) بۆ $count وەسڵ پاکتاو کرا"
            }.onFailure { err ->
                _actionMessage.value = "هەڵە لە پاکتاوکردن: ${err.localizedMessage}"
            }
        }
    }

    fun seedSampleDxpData() {
        viewModelScope.launch {
            val drv1Result = repository.createOrUpdateStaffMember(
                staffId = "drv_soran_dxp",
                name = "سۆران محەمەد (مەندووب)",
                phone = "07504455667",
                role = StaffRole.DRIVER.code,
                vehicleInfo = "ماتۆڕ - هەولێر ناوشار",
                active = true
            )
            val drv2Result = repository.createOrUpdateStaffMember(
                staffId = "drv_aram_dxp",
                name = "ئارام کەریم (مەندووب)",
                phone = "07708899001",
                role = StaffRole.DRIVER.code,
                vehicleInfo = "ئۆتۆمبێل - سلێمانی و دەوروبەر",
                active = true
            )
            val d1Id = drv1Result.getOrDefault("drv_soran_dxp")
            val d2Id = drv2Result.getOrDefault("drv_aram_dxp")

            repository.createOrder(
                trackingCode = "DXP-1081",
                senderName = "براندی جلوبەرگی شاهانە",
                customerName = "ژیار عەبدولڕەحمان",
                customerPhone = "07501122334",
                cityArea = "هەولێر - بەختیاری",
                addressDetails = "نزیک پارکی شانەدەر، کۆڵانی ١٤",
                notes = "پێش گەیشتن پەیوەندی پێوە بکە",
                itemPriceIqd = 45000L,
                deliveryFeeIqd = 4000L,
                assignedDriverId = d1Id,
                assignedDriverName = "سۆران محەمەد (مەندووب)"
            )

            repository.createOrder(
                trackingCode = "DXP-1082",
                senderName = "فرۆشگای مۆبایلی داهاتوو",
                customerName = "هاوژین لەتیف",
                customerPhone = "07703344556",
                cityArea = "سلێمانی - تووی مەلیک",
                addressDetails = "تەنیشت نەخۆشخانەی تایبەت",
                notes = "پاکێجەکە ناسکە و شکانی تێدایە",
                itemPriceIqd = 120000L,
                deliveryFeeIqd = 5000L,
                assignedDriverId = d2Id,
                assignedDriverName = "ئارام کەریم (مەندووب)"
            )

            _actionMessage.value = "نموونەی شۆفێر و وەسڵەکانی DXP POST زیادکران"
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
        // Also include any driver IDs referenced on orders that may not be in staffList yet
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
