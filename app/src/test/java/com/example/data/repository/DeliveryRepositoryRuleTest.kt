package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.OrderStatus
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
    fun createOrder_and_observeCompanyOrders_emitsRealtimeOrder() = runBlocking {
        signInTestUser("owner@dxppost.com")
        val repository = DeliveryRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOrder(
                trackingCode = "DXP-5001",
                senderName = "فرۆشگای هەولێر",
                customerName = "کاروان محەمەد",
                customerPhone = "07501234567",
                cityArea = "هەولێر - شۆڕش",
                addressDetails = "شەقامی چل مەتری",
                notes = "پێش چوون تەلەفۆن بکە",
                itemPriceIqd = 35000L,
                deliveryFeeIqd = 4000L
            )
        }
        assertTrue(createResult.isSuccess)
        val orderId = createResult.getOrThrow()

        val orders = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCompanyOrders().first { list -> list.any { it.id == orderId } }
        }
        val created = orders.first { it.id == orderId }
        assertEquals("DXP-5001", created.trackingCode)
        assertEquals(39000L, created.totalAmountIqd)
    }

    @Test
    fun assignedDriver_canUpdateOrderStatusToDelivered() = runBlocking {
        val driverUid = signInTestUser("driver1@dxppost.com")
        signInTestUser("owner2@dxppost.com")
        val ownerRepo = DeliveryRepository(firestore, auth)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOrder(
                trackingCode = "DXP-5002",
                senderName = "بازاڕی سلێمانی",
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

        // Switch to assigned driver
        signInTestUser("driver1@dxppost.com")
        val driverRepo = DeliveryRepository(firestore, auth)
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
    fun getOrderById_crossUserStranger_failsWithPermissionDenied() = runBlocking {
        val driverUid = signInTestUser("driver2@dxppost.com")
        signInTestUser("owner3@dxppost.com")
        val ownerRepo = DeliveryRepository(firestore, auth)

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createOrder(
                trackingCode = "DXP-5003",
                senderName = "فرۆشگا",
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

        // Sign in as a stranger who is neither the owner nor the assigned driver
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
