package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanyFinancialKpis
import com.example.data.model.DeliveryOrder
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.formatIqd
import com.example.ui.components.OrderCard
import com.example.ui.theme.DxpAccentViolet
import com.example.ui.theme.DxpDeepPurple
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.StatusDeliveredGreen
import com.example.ui.theme.StatusOnTheWayBlue
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusReturnedRed

@Composable
fun OrdersScreen(
    orders: List<DeliveryOrder>,
    staffList: List<StaffMember>,
    currentUserId: String,
    activeRole: StaffRole,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: OrderStatus?,
    onSelectStatus: (OrderStatus?) -> Unit,
    selectedDriverFilterId: String?,
    onSelectDriverFilter: (String?) -> Unit,
    kpis: CompanyFinancialKpis,
    onAddNewOrderClick: () -> Unit,
    onSeedSampleDataClick: () -> Unit,
    onStatusChange: (DeliveryOrder, OrderStatus) -> Unit,
    onReturnOrderWithReason: (DeliveryOrder, String) -> Unit,
    onAssignDriver: (DeliveryOrder, String, String) -> Unit,
    onEditOrder: (DeliveryOrder) -> Unit,
    onDeleteOrder: (DeliveryOrder) -> Unit
) {
    val drivers = remember(staffList) {
        staffList.filter { it.role == StaffRole.DRIVER.code || staffList.size == 1 }
    }

    var orderForDriverAssign by remember { mutableStateOf<DeliveryOrder?>(null) }
    var orderForReturnReason by remember { mutableStateOf<DeliveryOrder?>(null) }

    val effectiveDriverIdForDriverMode = remember(activeRole, selectedDriverFilterId, currentUserId, drivers, orders) {
        if (activeRole == StaffRole.DRIVER) {
            selectedDriverFilterId
                ?: if (orders.any { it.assignedDriverId == currentUserId }) {
                    currentUserId
                } else {
                    drivers.firstOrNull()?.id ?: currentUserId
                }
        } else {
            selectedDriverFilterId
        }
    }

    val filteredOrders = remember(
        orders,
        activeRole,
        searchQuery,
        selectedStatus,
        selectedDriverFilterId,
        effectiveDriverIdForDriverMode
    ) {
        orders.filter { order ->
            val matchesRoleAndDriver = if (activeRole == StaffRole.DRIVER) {
                if (selectedDriverFilterId == "ALL_DRIVERS" || orders.all { it.assignedDriverId == currentUserId }) {
                    true
                } else {
                    order.assignedDriverId == effectiveDriverIdForDriverMode
                }
            } else {
                selectedDriverFilterId == null || order.assignedDriverId == selectedDriverFilterId
            }

            val matchesStatus = selectedStatus == null || order.status == selectedStatus.code

            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                order.trackingCode.contains(q, ignoreCase = true) ||
                order.customerName.contains(q, ignoreCase = true) ||
                order.customerPhone.contains(q, ignoreCase = true) ||
                order.senderPhone.contains(q, ignoreCase = true) ||
                order.cityArea.contains(q, ignoreCase = true) ||
                order.senderName.contains(q, ignoreCase = true) ||
                order.assignedDriverName.contains(q, ignoreCase = true)

            matchesRoleAndDriver && matchesStatus && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("orders_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top KPI Card (Owner vs Driver)
        item {
            if (activeRole == StaffRole.OWNER) {
                OwnerLiveKpiCard(kpis = kpis)
            } else {
                DriverWorkspaceBanner(
                    drivers = drivers,
                    orders = orders,
                    selectedDriverId = if (selectedDriverFilterId == "ALL_DRIVERS") "ALL_DRIVERS" else effectiveDriverIdForDriverMode,
                    onSelectDriverId = { id -> onSelectDriverFilter(id) },
                    onAddPackageClick = onAddNewOrderClick
                )
            }
        }

        // 2. Search Bar & Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text("گەڕان بە کۆدی وەسڵ، ناوی نێرەر/وەرگر، ژمارە مۆبایل، شوێن...")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = DxpRoyalPlum
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "سڕینەوەی گەڕان"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DxpRoyalPlum,
                        unfocusedBorderColor = DxpRoyalPlum.copy(alpha = 0.25f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_orders_input")
                )

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { onSelectStatus(null) },
                        label = { Text("هەموو دۆخەکان (${orders.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DxpRoyalPlum,
                            selectedLabelColor = Color.White
                        )
                    )
                    OrderStatus.entries.forEach { status ->
                        val count = orders.count { it.status == status.code }
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = {
                                onSelectStatus(if (selectedStatus == status) null else status)
                            },
                            label = { Text("${status.kurdishLabel} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(status.badgeColorHex),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Owner Driver Filter Row
                if (activeRole == StaffRole.OWNER && drivers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "فلتەری شۆفێر:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = selectedDriverFilterId == null,
                            onClick = { onSelectDriverFilter(null) },
                            label = { Text("هەموو شۆفێرەکان") }
                        )
                        drivers.forEach { drv ->
                            FilterChip(
                                selected = selectedDriverFilterId == drv.id,
                                onClick = {
                                    onSelectDriverFilter(
                                        if (selectedDriverFilterId == drv.id) null else drv.id
                                    )
                                },
                                label = { Text(drv.name) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Empty State or Order Items
        if (filteredOrders.isEmpty()) {
            item {
                EmptyOrdersCard(
                    hasAnyOrdersInSystem = orders.isNotEmpty(),
                    isOwner = activeRole == StaffRole.OWNER,
                    onAddNewOrderClick = onAddNewOrderClick,
                    onSeedSampleDataClick = onSeedSampleDataClick
                )
            }
        } else {
            items(filteredOrders, key = { it.id.ifBlank { it.trackingCode } }) { order ->
                OrderCard(
                    order = order,
                    isOwnerView = activeRole == StaffRole.OWNER,
                    onStatusChange = { nextStatus ->
                        onStatusChange(order, nextStatus)
                    },
                    onRequestReturnDialog = {
                        orderForReturnReason = order
                    },
                    onAssignDriverClick = {
                        orderForDriverAssign = order
                    },
                    onEditOrderClick = {
                        onEditOrder(order)
                    },
                    onDeleteOrderClick = {
                        onDeleteOrder(order)
                    }
                )
            }
        }
    }

    if (orderForDriverAssign != null) {
        AssignDriverDialog(
            order = orderForDriverAssign!!,
            drivers = drivers,
            onDismiss = { orderForDriverAssign = null },
            onSelectDriver = { drvId, drvName ->
                val target = orderForDriverAssign!!
                orderForDriverAssign = null
                onAssignDriver(target, drvId, drvName)
            }
        )
    }

    if (orderForReturnReason != null) {
        ReturnOrderReasonDialog(
            order = orderForReturnReason!!,
            onDismiss = { orderForReturnReason = null },
            onConfirmReturn = { reason ->
                val target = orderForReturnReason!!
                orderForReturnReason = null
                onReturnOrderWithReason(target, reason)
            }
        )
    }
}

@Composable
private fun OwnerLiveKpiCard(kpis: CompanyFinancialKpis) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_kpi_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(DxpRoyalPlum, Color(0xFF4C1D95), DxpDeepPurple)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "پوختەی گشتی کۆمپانیای DXP POST",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = formatIqd(kpis.totalCollectedCashIqd),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "کۆی پارەی وەسڵە گەیشتووەکان",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "پارەی لای شۆفێرەکان",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFDE68A)
                            )
                            Text(
                                text = formatIqd(kpis.unsettledCashWithDriversIqd),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 4 Status Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMiniPill(
                        label = "چاوەڕوان",
                        count = kpis.pendingCount,
                        dotColor = StatusPendingAmber,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniPill(
                        label = "لە ڕێگا",
                        count = kpis.onTheWayCount,
                        dotColor = StatusOnTheWayBlue,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniPill(
                        label = "گەیشتوو",
                        count = kpis.deliveredCount,
                        dotColor = StatusDeliveredGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniPill(
                        label = "گەڕاوە",
                        count = kpis.returnedCount,
                        dotColor = StatusReturnedRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMiniPill(
    label: String,
    count: Int,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun DriverWorkspaceBanner(
    drivers: List<StaffMember>,
    orders: List<DeliveryOrder>,
    selectedDriverId: String?,
    onSelectDriverId: (String?) -> Unit,
    onAddPackageClick: () -> Unit
) {
    val activeDriverOrders = remember(orders, selectedDriverId) {
        if (selectedDriverId == "ALL_DRIVERS" || selectedDriverId == null) {
            orders
        } else {
            val filtered = orders.filter { it.assignedDriverId == selectedDriverId }
            if (filtered.isEmpty()) orders else filtered
        }
    }
    val remainingToDeliver = activeDriverOrders.count {
        it.status == OrderStatus.ASSIGNED.code || it.status == OrderStatus.ON_THE_WAY.code
    }
    val deliveredToday = activeDriverOrders.count { it.status == OrderStatus.DELIVERED.code }
    val cashInHand = activeDriverOrders
        .filter { it.status == OrderStatus.DELIVERED.code && !it.isSettled }
        .sumOf { it.totalAmountIqd }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DxpRoyalPlum),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("driver_workspace_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پەڕەی کاری شۆفێر (مەندووب)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "پارەی لای من: ${formatIqd(cashInHand)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFA7F3D0),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Button(
                onClick = onAddPackageClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = DxpRoyalPlum
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_banner_add_package_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "زیادکردنی کاڵا / وەسڵی نوێ (لەگەڵ وێنە و ژمارەی نێرەر و وەرگر)",
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMiniPill(
                    label = "ئامادە بۆ گەیاندن",
                    count = remainingToDeliver,
                    dotColor = StatusPendingAmber,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniPill(
                    label = "گەیشتووی من",
                    count = deliveredToday,
                    dotColor = StatusDeliveredGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyOrdersCard(
    hasAnyOrdersInSystem: Boolean,
    isOwner: Boolean,
    onAddNewOrderClick: () -> Unit,
    onSeedSampleDataClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DxpRoyalPlum.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = DxpRoyalPlum,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = if (hasAnyOrdersInSystem) {
                    "هیچ وەسڵێک بەم فلتەرە نەدۆزرایەوە"
                } else {
                    "هێشتا هیچ کاڵا یان وەسڵێک لە سیستەمی DXP POST تۆمار نەکراوە"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "شۆفێر یان ئەدمین دەتوانن کاڵا و وەسڵی نوێ زیاد بکەن لەگەڵ وێنەی کاڵاکە و ژمارەی نێرەر و وەرگر و شوێن.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Button(
                    onClick = onAddNewOrderClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_state_add_order_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("زیادکردنی کاڵا / وەسڵی نوێ")
                }

                if (isOwner && !hasAnyOrdersInSystem) {
                    OutlinedButton(
                        onClick = onSeedSampleDataClick,
                        border = BorderStroke(1.dp, DxpAccentViolet),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("seed_sample_data_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = DxpAccentViolet
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نموونەی تاقیکردنەوە")
                    }
                }
            }
        }
    }
}
