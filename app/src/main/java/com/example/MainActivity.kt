package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.DeliveryOrder
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.repository.DeliveryRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.DxpTopHeader
import com.example.ui.screens.AccountingScreen
import com.example.ui.screens.AddOrEditStaffDialog
import com.example.ui.screens.AdminSecretCodeDialog
import com.example.ui.screens.CreateOrEditOrderDialog
import com.example.ui.screens.DriversManagementScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.DxpSoftViolet
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DeliveryViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    AppNavigation()
                }
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

enum class DxpTab(val title: String, val icon: ImageVector, val testTag: String) {
    ORDERS("وەسڵەکان", Icons.Default.Inventory2, "nav_tab_orders"),
    ACCOUNTING("ژمێریاری", Icons.Default.AccountBalanceWallet, "nav_tab_accounting"),
    DRIVERS("شۆفێرەکان", Icons.AutoMirrored.Filled.DirectionsBike, "nav_tab_drivers"),
    PROFILE("هەژمارەکەم", Icons.Default.Person, "nav_tab_profile")
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        DxpMainWorkspace(
            currentUserId = user.uid,
            currentUserDisplayName = user.displayName ?: "بەڕێوەبەری DXP POST",
            currentUserEmail = user.email ?: ""
        )
    }
}

@Composable
fun DxpMainWorkspace(
    currentUserId: String,
    currentUserDisplayName: String,
    currentUserEmail: String
) {
    val viewModel: DeliveryViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DeliveryViewModel(
                    repository = DeliveryRepository(db),
                    currentUserId = currentUserId,
                    currentUserDisplayName = currentUserDisplayName,
                    currentUserEmail = currentUserEmail
                )
            }
        }
    )

    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val staffState by viewModel.staffState.collectAsStateWithLifecycle()
    val settlementsState by viewModel.settlementsState.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeViewRole.collectAsStateWithLifecycle()
    val actualRole by viewModel.actualUserRole.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val selectedDriverFilterId by viewModel.selectedDriverFilterId.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(DxpTab.ORDERS) }
    var showCreateOrderDialog by remember { mutableStateOf(false) }
    var orderToEdit by remember { mutableStateOf<DeliveryOrder?>(null) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var staffMemberToEdit by remember { mutableStateOf<StaffMember?>(null) }
    var showAdminSecretCodeDialog by remember { mutableStateOf(false) }

    // BackHandler on secondary tabs returns to ORDERS home tab
    BackHandler(enabled = currentTab != DxpTab.ORDERS) {
        currentTab = DxpTab.ORDERS
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val staffList = (staffState as? UiState.Success)?.data ?: emptyList()
    val settlements = (settlementsState as? UiState.Success)?.data ?: emptyList()
    val drivers = remember(staffList) {
        staffList.filter { it.role == StaffRole.DRIVER.code || staffList.size == 1 }
    }
    val kpis = remember(orders) { viewModel.calculateCompanyKpis(orders) }
    val driverSummaries = remember(staffList, orders) {
        viewModel.calculateDriverSummaries(staffList, orders)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DxpTopHeader(
                    activeRole = activeRole,
                    canSwitchRole = actualRole == StaffRole.OWNER,
                    onSwitchRole = { viewModel.switchActiveRole(it) },
                    onRequestAdminCode = { showAdminSecretCodeDialog = true },
                    userName = currentUserDisplayName
                )
            }
        },
        floatingActionButton = {
            if (currentTab == DxpTab.ORDERS) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateOrderDialog = true },
                    containerColor = DxpRoyalPlum,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("زیادکردنی کاڵا / وەسڵ") },
                    modifier = Modifier.testTag("fab_add_order")
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                DxpTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DxpRoyalPlum,
                            selectedTextColor = DxpRoyalPlum,
                            indicatorColor = DxpSoftViolet
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (ordersState is UiState.Loading && orders.isEmpty()) {
                CircularProgressIndicator(
                    color = DxpRoyalPlum,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (currentTab) {
                    DxpTab.ORDERS -> {
                        OrdersScreen(
                            orders = orders,
                            staffList = staffList,
                            currentUserId = currentUserId,
                            activeRole = activeRole,
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            selectedStatus = selectedStatus,
                            onSelectStatus = viewModel::setStatusFilter,
                            selectedDriverFilterId = selectedDriverFilterId,
                            onSelectDriverFilter = viewModel::setDriverFilter,
                            kpis = kpis,
                            onAddNewOrderClick = { showCreateOrderDialog = true },
                            onSeedSampleDataClick = { viewModel.seedSampleDxpData() },
                            onStatusChange = { order, newStatus ->
                                viewModel.updateOrderStatus(order.id, newStatus)
                            },
                            onReturnOrderWithReason = { order, reason ->
                                viewModel.updateOrderStatus(
                                    order.id,
                                    com.example.data.model.OrderStatus.RETURNED,
                                    reason
                                )
                            },
                            onAssignDriver = { order, drvId, drvName ->
                                viewModel.assignDriver(order, drvId, drvName)
                            },
                            onEditOrder = { order ->
                                orderToEdit = order
                            },
                            onDeleteOrder = { order ->
                                viewModel.deleteOrder(order.id)
                            }
                        )
                    }

                    DxpTab.ACCOUNTING -> {
                        val visibleDriverSummaries = remember(driverSummaries, activeRole, currentUserId) {
                            if (activeRole == StaffRole.OWNER) {
                                driverSummaries
                            } else {
                                driverSummaries.filter { it.driverId == currentUserId }
                            }
                        }
                        AccountingScreen(
                            kpis = kpis,
                            driverSummaries = visibleDriverSummaries,
                            settlements = settlements,
                            isOwnerView = activeRole == StaffRole.OWNER,
                            onSettleDriver = { summary ->
                                viewModel.settleDriverOrders(summary)
                            }
                        )
                    }

                    DxpTab.DRIVERS -> {
                        DriversManagementScreen(
                            staffList = staffList,
                            isOwnerView = activeRole == StaffRole.OWNER,
                            onAddDriverClick = { showAddStaffDialog = true },
                            onSeedTeamClick = { viewModel.seedFullTeamRoster() },
                            onEditStaffMember = { member -> staffMemberToEdit = member }
                        )
                    }

                    DxpTab.PROFILE -> {
                        ProfileAndSettingsScreen(
                            currentUserId = currentUserId,
                            currentUserEmail = currentUserEmail,
                            staffList = staffList,
                            activeRole = activeRole,
                            canSwitchRole = actualRole == StaffRole.OWNER,
                            onSwitchRole = { viewModel.switchActiveRole(it) },
                            onRequestAdminCode = { showAdminSecretCodeDialog = true },
                            onEditOwnProfile = { member -> staffMemberToEdit = member },
                            onSignOutClick = {
                                val credManager = CredentialManager.create(context)
                                signOut(
                                    context = context,
                                    credentialManager = credManager,
                                    onSignOutComplete = {},
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdminSecretCodeDialog) {
        AdminSecretCodeDialog(
            onDismiss = { showAdminSecretCodeDialog = false },
            onUnlockWithCode = { code, onError ->
                viewModel.unlockAdminWithCode(
                    enteredCode = code,
                    onUnlocked = { showAdminSecretCodeDialog = false },
                    onInvalidCode = { err -> onError(err) }
                )
            }
        )
    }

    if (showCreateOrderDialog) {
        CreateOrEditOrderDialog(
            existingOrder = null,
            drivers = drivers,
            currentUserId = currentUserId,
            currentUserDisplayName = currentUserDisplayName,
            isOwnerView = activeRole == StaffRole.OWNER,
            onDismiss = { showCreateOrderDialog = false },
            onSave = { code, sender, senderPhone, customer, phone, city, address, notes, packageImg, itemPrice, fee, drvId, drvName, _, _, _ ->
                showCreateOrderDialog = false
                viewModel.createNewOrder(
                    trackingCode = code,
                    senderName = sender,
                    senderPhone = senderPhone,
                    customerName = customer,
                    customerPhone = phone,
                    cityArea = city,
                    addressDetails = address,
                    notes = notes,
                    packageImageBase64 = packageImg,
                    itemPriceIqd = itemPrice,
                    deliveryFeeIqd = fee,
                    assignedDriverId = drvId,
                    assignedDriverName = drvName
                )
            }
        )
    }

    if (orderToEdit != null) {
        val editing = orderToEdit!!
        CreateOrEditOrderDialog(
            existingOrder = editing,
            drivers = drivers,
            currentUserId = currentUserId,
            currentUserDisplayName = currentUserDisplayName,
            isOwnerView = activeRole == StaffRole.OWNER,
            onDismiss = { orderToEdit = null },
            onSave = { _, sender, senderPhone, customer, phone, city, address, notes, packageImg, itemPrice, fee, drvId, drvName, status, returnReason, isSettled ->
                orderToEdit = null
                viewModel.updateFullOrder(
                    order = editing,
                    senderName = sender,
                    senderPhone = senderPhone,
                    customerName = customer,
                    customerPhone = phone,
                    cityArea = city,
                    addressDetails = address,
                    notes = notes,
                    packageImageBase64 = packageImg,
                    itemPriceIqd = itemPrice,
                    deliveryFeeIqd = fee,
                    assignedDriverId = drvId,
                    assignedDriverName = drvName,
                    status = status,
                    returnReason = returnReason,
                    isSettled = isSettled
                )
            }
        )
    }

    if (showAddStaffDialog || staffMemberToEdit != null) {
        val existing = staffMemberToEdit
        AddOrEditStaffDialog(
            existingMember = existing,
            isAdminEditor = actualRole == StaffRole.OWNER,
            onDismiss = {
                showAddStaffDialog = false
                staffMemberToEdit = null
            },
            onSave = { name, phone, email, role, vehicleInfo, active ->
                showAddStaffDialog = false
                staffMemberToEdit = null
                viewModel.saveStaffMember(
                    staffId = existing?.id ?: "",
                    name = name,
                    phone = phone,
                    email = email,
                    role = role,
                    vehicleInfo = vehicleInfo,
                    active = active,
                    existingMember = existing
                )
            }
        )
    }
}
