package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale

const val COMPANY_CODE_DXP = "DXP_POST"
const val ADMIN_USERNAME = "DXP"
const val ADMIN_SECRET_CODE = "DXPPOST"

enum class StaffRole(val code: String, val kurdishTitle: String) {
    OWNER("OWNER", "ئەدمین (ئۆنەر)"),
    DRIVER("DRIVER", "شۆفێر (مەندووب)");

    companion object {
        fun fromCode(code: String): StaffRole =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DRIVER
    }
}

enum class OrderStatus(
    val code: String,
    val kurdishLabel: String,
    val badgeColorHex: Long
) {
    PENDING("PENDING", "چاوەڕوانی ناردن", 0xFFF59E0B),
    ASSIGNED("ASSIGNED", "دراوە بە شۆفێر", 0xFF7C3AED),
    ON_THE_WAY("ON_THE_WAY", "لە ڕێگایە", 0xFF2563EB),
    DELIVERED("DELIVERED", "گەیشتووە", 0xFF10B981),
    RETURNED("RETURNED", "گەڕاوەتەوە", 0xFFEF4444);

    companion object {
        fun fromCode(code: String): OrderStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PENDING
    }
}

@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val companyCode: String = COMPANY_CODE_DXP,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val role: String = StaffRole.DRIVER.code,
    val adminUsername: String = "",
    val adminCode: String = "",
    val vehicleInfo: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeliveryOrder(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val companyCode: String = COMPANY_CODE_DXP,
    val trackingCode: String = "",
    val senderName: String = "",
    val senderPhone: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val cityArea: String = "",
    val addressDetails: String = "",
    val notes: String = "",
    val packageImageBase64: String = "",
    val itemPriceIqd: Long = 0L,
    val deliveryFeeIqd: Long = 0L,
    val totalAmountIqd: Long = 0L,
    val status: String = OrderStatus.PENDING.code,
    val returnReason: String = "",
    val assignedDriverId: String = "",
    val assignedDriverName: String = "",
    val isSettled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CashSettlement(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val companyCode: String = COMPANY_CODE_DXP,
    val driverId: String = "",
    val driverName: String = "",
    val ordersCount: Long = 0L,
    val itemPriceTotalIqd: Long = 0L,
    val deliveryFeeTotalIqd: Long = 0L,
    val totalSettledCashIqd: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DriverFinancialSummary(
    val driverId: String,
    val driverName: String,
    val phone: String,
    val vehicleInfo: String,
    val active: Boolean,
    val totalOrdersCount: Int,
    val inProgressCount: Int,
    val deliveredCount: Int,
    val returnedCount: Int,
    val unsettledOrders: List<DeliveryOrder>,
    val unsettledItemPriceIqd: Long,
    val unsettledDeliveryFeeIqd: Long,
    val unsettledTotalCashIqd: Long,
    val settledTotalCashIqd: Long
)

data class CompanyFinancialKpis(
    val totalOrders: Int = 0,
    val pendingCount: Int = 0,
    val onTheWayCount: Int = 0,
    val deliveredCount: Int = 0,
    val returnedCount: Int = 0,
    val totalCollectedCashIqd: Long = 0L,
    val totalItemPriceDeliveredIqd: Long = 0L,
    val totalDeliveryFeeEarnedIqd: Long = 0L,
    val unsettledCashWithDriversIqd: Long = 0L,
    val settledCashWithOwnerIqd: Long = 0L
)

fun formatIqd(amount: Long): String {
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
    return "$formatted د.ع"
}
