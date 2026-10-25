package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.OrderStatus
import com.example.data.model.StaffRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun driver_canCreatePackageWithPhotoAndSenderReceiverPhones_and_observeRealtime() = runBlocking {
        val driverUid = signInTestUser("driver_creator@dxppost.com")
        val driverRepo = DeliveryRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            driverRepo.createOrder(
                trackingCode = "DXP-DRV-901",
                senderName = "فرۆشگای هەولێر",
                senderPhone = "07508889900",
                customerName = "کاروان محەمەد",
                customerPhone = "07501234567",
                cityArea = "هەولێر - شۆڕش",
                addressDetails = "شەقامی چل مەتری",
                notes = "پێش چوون تەلەفۆن بکە",
                packageImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                itemPriceIqd = 35000L,
                deliveryFeeIqd = 4000L,
                assignedDriverId = driverUid,
                assignedDriverName = "شۆفێر کاروان"
            )
        }
        assertTrue("Driver createOrder failed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val orderId = createResult.getOrThrow()

        val orders = withTimeout(FLOW_TIMEOUT_MS) {
            driverRepo.observeDriverOrders(driverUid).first { list -> list.any { it.id == orderId } }
        }
        val created = orders.first { it.id == orderId }
        assertEquals("DXP-DRV-901", created.trackingCode)
        assertEquals("07508889900", created.senderPhone)
        assertEquals("07501234567", created.customerPhone)
        assertTrue(created.packageImageBase64.isNotBlank())
        assertEquals(39000L, created.totalAmountIqd)
    }

    @Test
    fun user_canUnlockUnlimitedAdminRole_withSecretCode_DXPPOST() = runBlocking {
        val newAdminUid = signInTestUser("new_unlimited_admin@dxppost.com")
        val repo = DeliveryRepository(firestore, auth)

        // First create default DRIVER profile
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureCurrentUserProfile(
                defaultName = "ئەدمینی نوێ",
                defaultEmail = "new_unlimited_admin@dxppost.com"
            ).getOrThrow()
        }

        // Wrong code fails
        val wrongResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.unlockAdminWithSecretCode("WRONGCODE", "ئەدمینی نوێ")
        }
        assertTrue(wrongResult.isFailure)

        // Correct DXPPOST code unlocks OWNER role in Firestore
        val unlockResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.unlockAdminWithSecretCode("DXPPOST", "ئەدمینی نوێ", "new_unlimited_admin@dxppost.com")
        }
        assertTrue("Unlock with DXPPOST failed: ${unlockResult.exceptionOrNull()}", unlockResult.isSuccess)

        val staffList = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCompanyStaff().first { list -> list.any { it.id == newAdminUid && it.role == StaffRole.OWNER.code } }
        }
        assertTrue(staffList.any { it.id == newAdminUid && it.role == StaffRole.OWNER.code })
    }

    @Test
    fun assignedDriver_canObserveAssignedOrders_and_updateStatusToDelivered() = runBlocking {
        val driverUid = signInTestUser("driver1@dxppost.com")
        signInTestUser("owner2@dxppost.com")
        val ownerRepo = DeliveryRepository(firestore, auth)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOrder(
                trackingCode = "DXP-5002",
                senderName = "بازاڕی سلێمانی",
                senderPhone = "07709991122",
                customerName = "ئارام عەلی",
                customerPhone = "07701234567",
                cityArea = "سلێمانی - سەرچنار",
                addressDetails = "نزیک پردەکە",
                notes = "",
                itemPriceIqd = 20000L,
                deliveryFeeIqd = 3000L,
                assignedDriverId = driverUid,
                assignedDriverName = "هێمن مەندووب"
            ).getOrThrow()
        }

        signInTestUser("driver1@dxppost.com")
        val driverRepo = DeliveryRepository(firestore, auth)

        val driverOrders = withTimeout(FLOW_TIMEOUT_MS) {
            driverRepo.observeDriverOrders(driverUid).first { list -> list.any { it.id == orderId } }
        }
        assertTrue(driverOrders.any { it.id == orderId })

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            driverRepo.updateOrderStatus(orderId, OrderStatus.DELIVERED)
        }
        assertTrue(updateResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            driverRepo.getOrderById(orderId).getOrThrow()
        }
        assertEquals(OrderStatus.DELIVERED.code, fetched.status)
    }

    @Test
    fun admin_settleDriverOrders_marksOrdersSettled_and_createsSettlementRecord() = runBlocking {
        val driverUid = signInTestUser("driver4@dxppost.com")
        signInTestUser("admin1@dxppost.com")
        val adminRepo = DeliveryRepository(firestore, auth)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createOrder(
                trackingCode = "DXP-5004",
                senderName = "فرۆشگای دهۆک",
                senderPhone = "07507778899",
                customerName = "ژیار",
                customerPhone = "07503334455",
                cityArea = "دهۆک",
                addressDetails = "سەنتەر",
                notes = "",
                itemPriceIqd = 45000L,
                deliveryFeeIqd = 5000L,
                assignedDriverId = driverUid,
                assignedDriverName = "شۆفێر ٤"
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateOrderStatus(orderId, OrderStatus.DELIVERED).getOrThrow()
        }
        val deliveredOrder = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.getOrderById(orderId).getOrThrow()
        }

        val settleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.settleDriverOrders(
                driverId = driverUid,
                driverName = "شۆفێر ٤",
                unsettledOrders = listOf(deliveredOrder)
            )
        }
        assertTrue("settleDriverOrders failed: ${settleResult.exceptionOrNull()}", settleResult.isSuccess)
        assertEquals(1, settleResult.getOrThrow())

        val settlements = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeCompanySettlements().first { list -> list.any { it.driverId == driverUid } }
        }
        val settlement = settlements.first { it.driverId == driverUid }
        assertEquals(50000L, settlement.totalSettledCashIqd)
    }

    @Test
    fun getOrderById_crossUserStranger_failsWithPermissionDenied() = runBlocking {
        val driverUid = signInTestUser("driver2@dxppost.com")
        signInTestUser("owner3@dxppost.com")
        val ownerRepo = DeliveryRepository(firestore, auth)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOrder(
                trackingCode = "DXP-5003",
                senderName = "فرۆشگا",
                senderPhone = "07501112233",
                customerName = "سارا",
                customerPhone = "07509998877",
                cityArea = "دهۆک",
                addressDetails = "ناوەند",
                notes = "",
                itemPriceIqd = 40000L,
                deliveryFeeIqd = 5000L,
                assignedDriverId = driverUid,
                assignedDriverName = "شۆفێر"
            ).getOrThrow()
        }

        signInTestUser("stranger@dxppost.com")
        val strangerRepo = DeliveryRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            strangerRepo.getOrderById(orderId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeCompanyOrders_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = DeliveryRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCompanyOrders().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
