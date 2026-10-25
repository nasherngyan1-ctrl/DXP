package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ADMIN_SECRET_CODE
import com.example.data.model.COMPANY_CODE_DXP
import com.example.data.model.CashSettlement
import com.example.data.model.DeliveryOrder
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DeliveryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("پێویستە سەرەتا بە هەژماری گووگڵ بچیتە ژوورەوە")
    }

    fun isKnownAdminEmail(email: String?): Boolean {
        val clean = email?.trim()?.lowercase() ?: return false
        return clean in BOOTSTRAPPED_ADMIN_EMAILS
    }

    // Admin real-time stream: all company orders
    fun observeCompanyOrders(): Flow<List<DeliveryOrder>> {
        val path = "orders"
        return db.collection(path)
            .whereEqualTo("companyCode", COMPANY_CODE_DXP)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DeliveryOrder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    // Driver real-time stream: only orders assigned to this driver
    fun observeDriverOrders(driverId: String): Flow<List<DeliveryOrder>> {
        val path = "orders"
        return db.collection(path)
            .whereEqualTo("assignedDriverId", driverId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DeliveryOrder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeCompanyStaff(): Flow<List<StaffMember>> {
        val path = "staff"
        return db.collection(path)
            .whereEqualTo("companyCode", COMPANY_CODE_DXP)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StaffMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareBy<StaffMember> { it.role != StaffRole.OWNER.code }
                        .thenBy { it.name }
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    // Admin real-time stream: all cash settlement records
    fun observeCompanySettlements(): Flow<List<CashSettlement>> {
        val path = "settlements"
        return db.collection(path)
            .whereEqualTo("companyCode", COMPANY_CODE_DXP)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CashSettlement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    // Driver real-time stream: only cash settlements for this driver
    fun observeDriverSettlements(driverId: String): Flow<List<CashSettlement>> {
        val path = "settlements"
        return db.collection(path)
            .whereEqualTo("driverId", driverId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CashSettlement::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getOrderById(orderId: String): Result<DeliveryOrder> = runCatching {
        val docRef = db.collection("orders").document(orderId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(
                DeliveryOrder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw NoSuchElementException("وەسڵەکە نەدۆزرایەوە")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun ensureCurrentUserProfile(
        defaultName: String,
        defaultEmail: String = "",
        defaultPhone: String = "",
        preferredRole: String? = null
    ): Result<StaffMember> = runCatching {
        val uid = requireUserId()
        val userEmail = defaultEmail.ifBlank { auth.currentUser?.email ?: "" }.trim()
        val docRef = db.collection("staff").document(uid)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val member = existingSnap.toObject(
                    StaffMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (member != null) return@runCatching member
            }

            // Check if an Admin pre-registered this user by email in /staff
            var matchedPreRegistered: StaffMember? = null
            if (userEmail.isNotBlank()) {
                val staffQuery = db.collection("staff")
                    .whereEqualTo("companyCode", COMPANY_CODE_DXP)
                    .get()
                    .await()
                matchedPreRegistered = staffQuery.toObjects(
                    StaffMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).firstOrNull {
                    it.id != uid && it.email.equals(userEmail, ignoreCase = true)
                }
            }

            val resolvedRole = when {
                preferredRole != null -> preferredRole
                isKnownAdminEmail(userEmail) -> StaffRole.OWNER.code
                else -> StaffRole.DRIVER.code
            }

            val safeName = (matchedPreRegistered?.name?.ifBlank { null } ?: defaultName)
                .trim()
                .ifBlank {
                    if (resolvedRole == StaffRole.OWNER.code) "ئەدمینی DXP POST" else "مەندووبی DXP POST"
                }
                .take(80)
            val safePhone = (matchedPreRegistered?.phone?.ifBlank { null } ?: defaultPhone)
                .trim()
                .take(30)
            val safeVehicle = (matchedPreRegistered?.vehicleInfo?.ifBlank { null } ?: "گەیاندنی گشتی")
                .trim()
                .take(80)

            val payload = mutableMapOf<String, Any>(
                "ownerUid" to uid,
                "companyCode" to COMPANY_CODE_DXP,
                "name" to safeName,
                "phone" to safePhone,
                "email" to userEmail.take(120),
                "role" to resolvedRole,
                "vehicleInfo" to safeVehicle,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (resolvedRole == StaffRole.OWNER.code) {
                payload["adminCode"] = ADMIN_SECRET_CODE
            }
            docRef.set(payload).await()
            StaffMember(
                id = uid,
                ownerUid = uid,
                companyCode = COMPANY_CODE_DXP,
                name = safeName,
                phone = safePhone,
                email = userEmail.take(120),
                role = resolvedRole,
                adminCode = if (resolvedRole == StaffRole.OWNER.code) ADMIN_SECRET_CODE else "",
                vehicleInfo = safeVehicle,
                active = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Unlock unlimited Admin (OWNER) access using the secret code DXPPOST
    suspend fun unlockAdminWithSecretCode(
        secretCode: String,
        defaultName: String,
        defaultEmail: String = ""
    ): Result<Unit> = runCatching {
        val cleanCode = secretCode.trim().uppercase()
        if (cleanCode != ADMIN_SECRET_CODE) {
            throw IllegalArgumentException("کۆدی نهێنی ئەدمین هەڵەیە! تکایە کۆدی دروست بنووسە.")
        }
        val uid = requireUserId()
        val docRef = db.collection("staff").document(uid)
        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "role" to StaffRole.OWNER.code,
                        "adminCode" to ADMIN_SECRET_CODE,
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val safeName = defaultName.trim().ifBlank { "ئەدمینی DXP POST" }.take(80)
                docRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "companyCode" to COMPANY_CODE_DXP,
                        "name" to safeName,
                        "phone" to "",
                        "email" to defaultEmail.trim().take(120),
                        "role" to StaffRole.OWNER.code,
                        "adminCode" to ADMIN_SECRET_CODE,
                        "vehicleInfo" to "بەڕێوەبەرایەتی DXP POST",
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun createOrUpdateStaffMember(
        staffId: String = "",
        name: String,
        phone: String,
        email: String = "",
        role: String,
        vehicleInfo: String,
        active: Boolean = true,
        existingMember: StaffMember? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val resolvedId = staffId.ifBlank {
            existingMember?.id?.ifBlank { null } ?: "drv_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        }
        val docRef = db.collection("staff").document(resolvedId)
        try {
            if (existingMember == null) {
                val payload = mutableMapOf<String, Any>(
                    "ownerUid" to uid,
                    "companyCode" to COMPANY_CODE_DXP,
                    "name" to name.trim().ifBlank { "شۆفێری DXP" }.take(80),
                    "phone" to phone.trim().take(30),
                    "email" to email.trim().take(120),
                    "role" to role,
                    "vehicleInfo" to vehicleInfo.trim().take(80),
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (role == StaffRole.OWNER.code) {
                    payload["adminCode"] = ADMIN_SECRET_CODE
                }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "name" to name.trim().ifBlank { existingMember.name }.take(80),
                    "phone" to phone.trim().take(30),
                    "email" to email.trim().take(120),
                    "role" to role,
                    "vehicleInfo" to vehicleInfo.trim().take(80),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (role == StaffRole.OWNER.code) {
                    updates["adminCode"] = ADMIN_SECRET_CODE
                }
                docRef.update(updates).await()
            }
            resolvedId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingMember == null) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
    }

    suspend fun createOrder(
        trackingCode: String,
        senderName: String,
        senderPhone: String = "",
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        packageImageBase64: String = "",
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String = "",
        assignedDriverName: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val orderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("orders").document(orderId)
        val cleanItemPrice = itemPriceIqd.coerceAtLeast(0L)
        val cleanDeliveryFee = deliveryFeeIqd.coerceAtLeast(0L)
        val totalAmount = cleanItemPrice + cleanDeliveryFee
        val initialStatus = if (assignedDriverId.isNotBlank()) {
            OrderStatus.ASSIGNED.code
        } else {
            OrderStatus.PENDING.code
        }
        val code = trackingCode.trim().ifBlank {
            "DXP-${(1000..9999).random()}"
        }.take(40)

        val payload = mapOf(
            "ownerUid" to uid,
            "companyCode" to COMPANY_CODE_DXP,
            "trackingCode" to code,
            "senderName" to senderName.trim().take(80),
            "senderPhone" to senderPhone.trim().take(30),
            "customerName" to customerName.trim().take(80),
            "customerPhone" to customerPhone.trim().take(30),
            "cityArea" to cityArea.trim().take(80),
            "addressDetails" to addressDetails.trim().take(250),
            "notes" to notes.trim().take(300),
            "packageImageBase64" to packageImageBase64.trim().take(340000),
            "itemPriceIqd" to cleanItemPrice,
            "deliveryFeeIqd" to cleanDeliveryFee,
            "totalAmountIqd" to totalAmount,
            "status" to initialStatus,
            "returnReason" to "",
            "assignedDriverId" to assignedDriverId.trim().take(128),
            "assignedDriverName" to assignedDriverName.trim().take(80),
            "isSettled" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            orderId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        returnReason: String = ""
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        val updates = mapOf(
            "status" to newStatus.code,
            "returnReason" to returnReason.trim().take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun assignDriverToOrder(
        orderId: String,
        driverId: String,
        driverName: String,
        currentStatus: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        val nextStatus = if (driverId.isBlank()) {
            OrderStatus.PENDING.code
        } else if (currentStatus == OrderStatus.PENDING.code) {
            OrderStatus.ASSIGNED.code
        } else {
            currentStatus
        }
        val updates = mapOf(
            "assignedDriverId" to driverId.trim().take(128),
            "assignedDriverName" to driverName.trim().take(80),
            "status" to nextStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateFullOrder(
        orderId: String,
        senderName: String,
        senderPhone: String = "",
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        packageImageBase64: String = "",
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String,
        assignedDriverName: String,
        status: String,
        returnReason: String,
        isSettled: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        val cleanItemPrice = itemPriceIqd.coerceAtLeast(0L)
        val cleanDeliveryFee = deliveryFeeIqd.coerceAtLeast(0L)
        val updates = mapOf(
            "senderName" to senderName.trim().take(80),
            "senderPhone" to senderPhone.trim().take(30),
            "customerName" to customerName.trim().take(80),
            "customerPhone" to customerPhone.trim().take(30),
            "cityArea" to cityArea.trim().take(80),
            "addressDetails" to addressDetails.trim().take(250),
            "notes" to notes.trim().take(300),
            "packageImageBase64" to packageImageBase64.trim().take(340000),
            "itemPriceIqd" to cleanItemPrice,
            "deliveryFeeIqd" to cleanDeliveryFee,
            "totalAmountIqd" to (cleanItemPrice + cleanDeliveryFee),
            "assignedDriverId" to assignedDriverId.trim().take(128),
            "assignedDriverName" to assignedDriverName.trim().take(80),
            "status" to status,
            "returnReason" to returnReason.trim().take(200),
            "isSettled" to isSettled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun settleDriverOrders(
        driverId: String,
        driverName: String,
        unsettledOrders: List<DeliveryOrder>,
        notes: String = "پاکتاوی حیسابی شۆفێر"
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        if (unsettledOrders.isEmpty()) return@runCatching 0
        val batch = db.batch()
        for (order in unsettledOrders) {
            if (order.id.isNotBlank()) {
                val docRef = db.collection("orders").document(order.id)
                batch.update(
                    docRef,
                    mapOf(
                        "isSettled" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        val itemPriceTotal = unsettledOrders.sumOf { it.itemPriceIqd.coerceAtLeast(0L) }
        val deliveryFeeTotal = unsettledOrders.sumOf { it.deliveryFeeIqd.coerceAtLeast(0L) }
        val totalSettled = itemPriceTotal + deliveryFeeTotal
        val settlementId = "stl_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val settlementRef = db.collection("settlements").document(settlementId)
        val settlementPayload = mapOf(
            "ownerUid" to uid,
            "companyCode" to COMPANY_CODE_DXP,
            "driverId" to driverId.trim().ifBlank { "drv_unknown" }.take(128),
            "driverName" to driverName.trim().ifBlank { "شۆفێر" }.take(80),
            "ordersCount" to unsettledOrders.size.toLong(),
            "itemPriceTotalIqd" to itemPriceTotal,
            "deliveryFeeTotalIqd" to deliveryFeeTotal,
            "totalSettledCashIqd" to totalSettled,
            "notes" to notes.trim().take(250),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(settlementRef, settlementPayload)

        try {
            batch.commit().await()
            unsettledOrders.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "settlements/$settlementId")
            throw e
        }
    }

    suspend fun seedTwoAdminsAndTenDrivers(): Result<Int> = runCatching {
        val uid = requireUserId()
        val batch = db.batch()
        var createdCount = 0

        val admin2Ref = db.collection("staff").document("admin_dxp_2")
        batch.set(
            admin2Ref,
            mapOf(
                "ownerUid" to uid,
                "companyCode" to COMPANY_CODE_DXP,
                "name" to "ئەدمینی دووەم (بەڕێوەبەر)",
                "phone" to "07501000002",
                "email" to "admin2@dxppost.com",
                "role" to StaffRole.OWNER.code,
                "adminCode" to ADMIN_SECRET_CODE,
                "vehicleInfo" to "بەڕێوەبەرایەتی سەرەکی DXP POST",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        createdCount++

        val defaultTenDrivers = listOf(
            Triple("drv_dxp_01", "مەندووب ١ - سۆران محەمەد", "هەولێر - ناوشار"),
            Triple("drv_dxp_02", "مەندووب ٢ - ئارام کەریم", "سلێمانی - ناوشار"),
            Triple("drv_dxp_03", "مەندووب ٣ - کاروان عەلی", "دهۆک - ناوەند"),
            Triple("drv_dxp_04", "مەندووب ٤ - هێمن حەسەن", "هەولێر - عەنکاوە و بەختیاری"),
            Triple("drv_dxp_05", "مەندووب ٥ - ڕێبین جەلال", "سلێمانی - سەرچنار و تووی مەلیک"),
            Triple("drv_dxp_06", "مەندووب ٦ - دیار فەرهاد", "کەرکووک - ڕەحیماوا"),
            Triple("drv_dxp_07", "مەندووب ٧ - شوان عومەر", "هەڵەبجە و شارەزوور"),
            Triple("drv_dxp_08", "مەندووب ٨ - بڕوا سەلام", "سۆران و چۆمان"),
            Triple("drv_dxp_09", "مەندووب ٩ - زانا تاهیر", "زاخۆ و سێمێل"),
            Triple("drv_dxp_10", "مەندووب ١٠ - توانا ئەحمەد", "گەرمیان و کەلار")
        )

        defaultTenDrivers.forEachIndexed { idx, (docId, drvName, zone) ->
            val drvRef = db.collection("staff").document(docId)
            val num = (idx + 1).toString().padStart(2, '0')
            batch.set(
                drvRef,
                mapOf(
                    "ownerUid" to uid,
                    "companyCode" to COMPANY_CODE_DXP,
                    "name" to drvName,
                    "phone" to "075044556$num",
                    "email" to "driver$num@dxppost.com",
                    "role" to StaffRole.DRIVER.code,
                    "vehicleInfo" to zone,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            createdCount++
        }

        try {
            batch.commit().await()
            createdCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "staff/batch_seed")
            throw e
        }
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private companion object {
        val BOOTSTRAPPED_ADMIN_EMAILS = setOf(
            "abdulbasitshwankareem@gmail.com",
            "admin1@dxppost.com",
            "admin2@dxppost.com",
            "owner@dxppost.com",
            "owner2@dxppost.com",
            "owner3@dxppost.com"
        )
    }
}
