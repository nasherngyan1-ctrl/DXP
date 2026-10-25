package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.COMPANY_CODE_DXP
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

    fun observeCompanyStaff(): Flow<List<StaffMember>> {
        val path = "staff"
        return db.collection(path)
            .whereEqualTo("companyCode", COMPANY_CODE_DXP)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StaffMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name }
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
        defaultPhone: String = "",
        preferredRole: String = StaffRole.OWNER.code
    ): Result<StaffMember> = runCatching {
        val uid = requireUserId()
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
            val safeName = defaultName.trim().ifBlank { "بەکارهێنەری DXP" }.take(80)
            val payload = mapOf(
                "ownerUid" to uid,
                "companyCode" to COMPANY_CODE_DXP,
                "name" to safeName,
                "phone" to defaultPhone.trim().take(30),
                "role" to preferredRole,
                "vehicleInfo" to "گەیاندنی گشتی",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            StaffMember(
                id = uid,
                ownerUid = uid,
                companyCode = COMPANY_CODE_DXP,
                name = safeName,
                phone = defaultPhone.trim().take(30),
                role = preferredRole,
                vehicleInfo = "گەیاندنی گشتی",
                active = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun createOrUpdateStaffMember(
        staffId: String = "",
        name: String,
        phone: String,
        role: String,
        vehicleInfo: String,
        active: Boolean = true,
        existingMember: StaffMember? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val resolvedId = staffId.ifBlank {
            existingMember?.id?.ifBlank { null } ?: "drv_${UUID.randomUUID().toString().take(8)}"
        }
        val docRef = db.collection("staff").document(resolvedId)
        try {
            if (existingMember == null) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "companyCode" to COMPANY_CODE_DXP,
                    "name" to name.trim().ifBlank { "شۆفێری DXP" }.take(80),
                    "phone" to phone.trim().take(30),
                    "role" to role,
                    "vehicleInfo" to vehicleInfo.trim().take(80),
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to name.trim().ifBlank { existingMember.name }.take(80),
                    "phone" to phone.trim().take(30),
                    "role" to role,
                    "vehicleInfo" to vehicleInfo.trim().take(80),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
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
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
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
            "customerName" to customerName.trim().take(80),
            "customerPhone" to customerPhone.trim().take(30),
            "cityArea" to cityArea.trim().take(80),
            "addressDetails" to addressDetails.trim().take(250),
            "notes" to notes.trim().take(300),
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
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        val cleanItemPrice = itemPriceIqd.coerceAtLeast(0L)
        val cleanDeliveryFee = deliveryFeeIqd.coerceAtLeast(0L)
        val updates = mapOf(
            "senderName" to senderName.trim().take(80),
            "customerName" to customerName.trim().take(80),
            "customerPhone" to customerPhone.trim().take(30),
            "cityArea" to cityArea.trim().take(80),
            "addressDetails" to addressDetails.trim().take(250),
            "notes" to notes.trim().take(300),
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

    suspend fun settleDriverOrders(unsettledOrders: List<DeliveryOrder>): Result<Int> = runCatching {
        requireUserId()
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
        try {
            batch.commit().await()
            unsettledOrders.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "orders/batch_settle")
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
}
