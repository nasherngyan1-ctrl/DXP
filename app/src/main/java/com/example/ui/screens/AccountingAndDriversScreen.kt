package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CompanyFinancialKpis
import com.example.data.model.DriverFinancialSummary
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.formatIqd
import com.example.ui.theme.DxpAccentViolet
import com.example.ui.theme.DxpDeepPurple
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.StatusDeliveredGreen
import com.example.ui.theme.StatusReturnedRed

@Composable
fun AccountingScreen(
    kpis: CompanyFinancialKpis,
    driverSummaries: List<DriverFinancialSummary>,
    isOwnerView: Boolean,
    onSettleDriver: (DriverFinancialSummary) -> Unit
) {
    var summaryToConfirmSettle by remember { mutableStateOf<DriverFinancialSummary?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("accounting_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DxpRoyalPlum, DxpDeepPurple)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ژمێریاری و حیساباتی DXP POST",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                        AccountingMetricRow(
                            title = "کۆی گشتی پارەی وەسڵە گەیشتووەکان:",
                            value = formatIqd(kpis.totalCollectedCashIqd),
                            valueColor = Color.White,
                            isBold = true
                        )
                        AccountingMetricRow(
                            title = "کۆی نرخی کاڵا (پشکی فرۆشیاران):",
                            value = formatIqd(kpis.totalItemPriceDeliveredIqd),
                            valueColor = Color.White.copy(alpha = 0.9f)
                        )
                        AccountingMetricRow(
                            title = "کۆی داهاتی کرێی گەیاندن:",
                            value = formatIqd(kpis.totalDeliveryFeeEarnedIqd),
                            valueColor = Color(0xFFA7F3D0),
                            isBold = true
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "پاکتاونەکراو (لای شۆفێر)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFFDE68A)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatIqd(kpis.unsettledCashWithDriversIqd),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "پاکتاوکراو (لای ئۆنەر)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFA7F3D0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatIqd(kpis.settledCashWithOwnerIqd),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "حیساباتی شۆفێرەکان (مەندووبەکان)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (driverSummaries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "هێشتا هیچ شۆفێرێک یان وەسڵێکی سپێردراو لە سیستەمدا نییە.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(driverSummaries, key = { it.driverId }) { summary ->
                DriverSettlementCard(
                    summary = summary,
                    isOwnerView = isOwnerView,
                    onRequestSettle = { summaryToConfirmSettle = summary }
                )
            }
        }
    }

    if (summaryToConfirmSettle != null) {
        val target = summaryToConfirmSettle!!
        AlertDialog(
            onDismissRequest = { summaryToConfirmSettle = null },
            title = {
                Text(
                    text = "پاکتاوکردنی حیسابی شۆفێر",
                    style = MaterialTheme.typography.titleLarge,
                    color = DxpRoyalPlum
                )
            },
            text = {
                Text(
                    text = "ئایا دڵنیایت لە وەرگرتنی بڕی (${formatIqd(target.unsettledTotalCashIqd)}) لە شۆفێر (${target.driverName}) بۆ ${target.unsettledOrders.size} وەسڵی گەیشتوو؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        summaryToConfirmSettle = null
                        onSettleDriver(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeliveredGreen)
                ) {
                    Text("بەڵێ، پاکتاوی بکە")
                }
            },
            dismissButton = {
                TextButton(onClick = { summaryToConfirmSettle = null }) {
                    Text("پاشگەزبوونەوە")
                }
            }
        )
    }
}

@Composable
private fun AccountingMetricRow(
    title: String,
    value: String,
    valueColor: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun DriverSettlementCard(
    summary: DriverFinancialSummary,
    isOwnerView: Boolean,
    onRequestSettle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("driver_settlement_card_${summary.driverId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DxpRoyalPlum.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = DxpRoyalPlum
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.driverName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (summary.phone.isNotBlank() || summary.vehicleInfo.isNotBlank()) {
                            Text(
                                text = "${summary.vehicleInfo} • ${summary.phone}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DxpAccentViolet.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${summary.deliveredCount} گەیشتوو / ${summary.totalOrdersCount} وەسڵ",
                        style = MaterialTheme.typography.labelMedium,
                        color = DxpRoyalPlum,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("نرخی کاڵای پاکتاونەکراو:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatIqd(summary.unsettledItemPriceIqd),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("کرێی گەیاندنی پاکتاونەکراو:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatIqd(summary.unsettledDeliveryFeeIqd),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "کۆی پارەی لای شۆفێر (${summary.unsettledOrders.size} وەسڵ):",
                            style = MaterialTheme.typography.titleMedium,
                            color = DxpRoyalPlum
                        )
                        Text(
                            text = formatIqd(summary.unsettledTotalCashIqd),
                            style = MaterialTheme.typography.titleLarge,
                            color = DxpRoyalPlum,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (summary.unsettledOrders.isNotEmpty() && isOwnerView) {
                Button(
                    onClick = onRequestSettle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusDeliveredGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settle_driver_btn_${summary.driverId}")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پاکتاوکردنی حیساب (تەسفیەی ${formatIqd(summary.unsettledTotalCashIqd)})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else if (summary.unsettledOrders.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = StatusDeliveredGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "هەموو وەسڵە گەیشتووەکانی ئەم شۆفێرە پاکتاو کراون (${formatIqd(summary.settledTotalCashIqd)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusDeliveredGreen
                    )
                }
            }
        }
    }
}

@Composable
fun DriversManagementScreen(
    staffList: List<StaffMember>,
    isOwnerView: Boolean,
    onAddDriverClick: () -> Unit,
    onEditStaffMember: (StaffMember) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("drivers_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "شۆفێرەکان و ستافی DXP POST",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "بەڕێوەبردنی مەندووبەکان و ئۆنەرەکانی کۆمپانیا",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isOwnerView) {
                    Button(
                        onClick = onAddDriverClick,
                        colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_new_driver_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("زیادکردنی شۆفێر")
                    }
                }
            }
        }

        items(staffList, key = { it.id }) { member ->
            val roleEnum = StaffRole.fromCode(member.role)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (roleEnum == StaffRole.OWNER) {
                                    DxpRoyalPlum
                                } else {
                                    DxpAccentViolet.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = roleEnum.kurdishTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (roleEnum == StaffRole.OWNER) Color.White else DxpRoyalPlum,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = member.vehicleInfo.ifBlank { "گەیاندنی گشتی" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (member.phone.isNotBlank()) {
                            Text(
                                text = "مۆبایل: ${member.phone}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DxpRoyalPlum,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (member.phone.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${member.phone}")
                                    )
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "پەیوەندی",
                                    tint = StatusDeliveredGreen
                                )
                            }
                        }
                        if (isOwnerView) {
                            IconButton(onClick = { onEditStaffMember(member) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "دەستکاری",
                                    tint = DxpRoyalPlum
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAndSettingsScreen(
    currentUserId: String,
    currentUserEmail: String,
    staffList: List<StaffMember>,
    activeRole: StaffRole,
    onSwitchRole: (StaffRole) -> Unit,
    onEditOwnProfile: (StaffMember) -> Unit,
    onSignOutClick: () -> Unit
) {
    val myProfile = remember(staffList, currentUserId) {
        staffList.firstOrNull { it.id == currentUserId }
            ?: StaffMember(
                id = currentUserId,
                name = "بەکارهێنەری DXP POST",
                role = activeRole.code,
                vehicleInfo = "گەیاندنی گشتی"
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DxpRoyalPlum),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = DxpRoyalPlum,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(width = 160.dp, height = 110.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dxp_post_logo),
                            contentDescription = "DXP POST Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = myProfile.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    if (currentUserEmail.isNotBlank()) {
                        Text(
                            text = currentUserEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "ڕۆڵی ئێستا: ${activeRole.kurdishTitle}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "زانیارییەکانی هەژمارەکەم",
                        style = MaterialTheme.typography.titleMedium,
                        color = DxpRoyalPlum
                    )
                    Text("ناو: ${myProfile.name}")
                    Text("ژمارە مۆبایل: ${myProfile.phone.ifBlank { "تۆمار نەکراوە" }}")
                    Text("ناوچە / هۆکاری گواستنەوە: ${myProfile.vehicleInfo}")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onEditOwnProfile(myProfile) },
                            colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("دەستکاریکردنی پرۆفایل")
                        }

                        OutlinedButton(
                            onClick = {
                                val next = if (activeRole == StaffRole.OWNER) {
                                    StaffRole.DRIVER
                                } else {
                                    StaffRole.OWNER
                                }
                                onSwitchRole(next)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (activeRole == StaffRole.OWNER) {
                                    "گۆڕین بۆ دیمەنی مەندووب"
                                } else {
                                    "گۆڕین بۆ دیمەنی ئۆنەر"
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOutClick,
                border = BorderStroke(1.dp, StatusReturnedRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusReturnedRed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("چوونەدەرەوە لە هەژمار", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
