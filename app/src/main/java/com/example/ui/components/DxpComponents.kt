package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DeliveryOrder
import com.example.data.model.OrderStatus
import com.example.data.model.StaffRole
import com.example.data.model.formatIqd
import com.example.ui.theme.DxpAccentViolet
import com.example.ui.theme.DxpDeepPurple
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.StatusDeliveredGreen
import com.example.ui.theme.StatusOnTheWayBlue
import com.example.ui.theme.StatusReturnedRed

fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap? {
    if (base64Str.isBlank()) return null
    return try {
        val clean = base64Str.substringAfter("base64,", base64Str)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bmp?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun DxpTopHeader(
    activeRole: StaffRole,
    canSwitchRole: Boolean = true,
    onSwitchRole: (StaffRole) -> Unit,
    onRequestAdminCode: () -> Unit = {},
    userName: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(DxpRoyalPlum, DxpDeepPurple)
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DxpRoyalPlum,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(width = 64.dp, height = 46.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dxp_post_logo),
                        contentDescription = "DXP POST Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DXP POST",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusDeliveredGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ڕاستەوخۆ",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Role Switcher Pill (Admins switch freely; Drivers can tap ئەدمین to unlock with code DXPPOST)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.testTag("role_switcher_pill")
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoleChipItem(
                        label = "ئەدمین",
                        selected = activeRole == StaffRole.OWNER,
                        showLockIcon = !canSwitchRole && activeRole != StaffRole.OWNER,
                        onClick = {
                            if (canSwitchRole) {
                                onSwitchRole(StaffRole.OWNER)
                            } else {
                                onRequestAdminCode()
                            }
                        },
                        testTag = "switch_role_owner"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    RoleChipItem(
                        label = "مەندووب",
                        selected = activeRole == StaffRole.DRIVER,
                        showLockIcon = false,
                        onClick = { onSwitchRole(StaffRole.DRIVER) },
                        testTag = "switch_role_driver"
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleChipItem(
    label: String,
    selected: Boolean,
    showLockIcon: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color.White else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (showLockIcon) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (selected) DxpRoyalPlum else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) DxpRoyalPlum else Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(
    order: DeliveryOrder,
    isOwnerView: Boolean,
    onStatusChange: (OrderStatus) -> Unit,
    onRequestReturnDialog: () -> Unit,
    onAssignDriverClick: () -> Unit,
    onEditOrderClick: () -> Unit,
    onDeleteOrderClick: () -> Unit
) {
    val context = LocalContext.current
    val statusEnum = OrderStatus.fromCode(order.status)
    val statusColor = Color(statusEnum.badgeColorHex)
    val packageBitmap = remember(order.packageImageBase64) {
        decodeBase64ToImageBitmap(order.packageImageBase64)
    }
    var showFullImageDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_card_${order.trackingCode}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Tracking Code + Status Badge + Settlement Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DxpRoyalPlum
                    ) {
                        Text(
                            text = order.trackingCode,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (order.status == OrderStatus.DELIVERED.code) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (order.isSettled) {
                                StatusDeliveredGreen.copy(alpha = 0.15f)
                            } else {
                                Color(0xFFFEF3C7)
                            }
                        ) {
                            Text(
                                text = if (order.isSettled) "پاکتاوکراو ✓" else "پارە لای شۆفێر",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (order.isSettled) StatusDeliveredGreen else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusEnum.kurdishLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Package Image Banner (if uploaded by Driver or Admin)
            if (packageBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showFullImageDialog = true }
                        .testTag("order_package_image_${order.trackingCode}")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = packageBitmap,
                            contentDescription = "وێنەی کاڵا",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(bottomStart = 10.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = "وێنەی کاڵا (گرتە بکە بۆ گەورەکردن)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Receiver (Customer) & Phone Row with Direct Call Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = DxpRoyalPlum,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "وەرگر: ${order.customerName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = DxpAccentViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append("شوێن: ")
                                append(order.cityArea)
                                if (order.addressDetails.isNotBlank()) {
                                    append(" — ")
                                    append(order.addressDetails)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (order.customerPhone.isNotBlank()) {
                    FilledTonalCallButton(
                        label = "وەرگر: ${order.customerPhone}",
                        phone = order.customerPhone,
                        onCall = {
                            val dialIntent = Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${order.customerPhone}")
                            )
                            context.startActivity(dialIntent)
                        }
                    )
                }
            }

            // Sender Name & Sender Phone Row (ئەو کەسەی ناردوویەتی)
            if (order.senderName.isNotBlank() || order.senderPhone.isNotBlank() || order.notes.isNotBlank() || order.returnReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (order.senderName.isNotBlank() || order.senderPhone.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = DxpRoyalPlum,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "نێرەر: ${order.senderName.ifBlank { "دیاری نەکراوە" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (order.senderPhone.isNotBlank()) {
                                FilledTonalCallButton(
                                    label = "نێرەر: ${order.senderPhone}",
                                    phone = order.senderPhone,
                                    tintColor = DxpRoyalPlum,
                                    onCall = {
                                        val dialIntent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${order.senderPhone}")
                                        )
                                        context.startActivity(dialIntent)
                                    }
                                )
                            }
                        }
                    }
                    if (order.notes.isNotBlank()) {
                        Text(
                            text = "تێبینی: ${order.notes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (order.returnReason.isNotBlank()) {
                        Text(
                            text = "هۆکاری گەڕانەوە: ${order.returnReason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusReturnedRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Financial Breakdown Box (Item Price + Delivery Fee = Total COD)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DxpRoyalPlum.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FinancialColumn(
                        label = "نرخی کاڵا",
                        value = formatIqd(order.itemPriceIqd),
                        highlight = false
                    )
                    FinancialColumn(
                        label = "کرێی گەیاندن",
                        value = formatIqd(order.deliveryFeeIqd),
                        highlight = false
                    )
                    FinancialColumn(
                        label = "کۆی وەرگرتن",
                        value = formatIqd(order.totalAmountIqd),
                        highlight = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Assigned Driver Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        tint = if (order.assignedDriverId.isBlank()) Color.Gray else DxpAccentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (order.assignedDriverName.isNotBlank()) {
                            "مەندووب: ${order.assignedDriverName}"
                        } else {
                            "مەندووب دیاری نەکراوە"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (order.assignedDriverId.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                if (isOwnerView) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onAssignDriverClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("assign_driver_btn_${order.trackingCode}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "دیاریکردنی شۆفێر",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (order.assignedDriverId.isBlank()) "دانانی شۆفێر" else "گۆڕینی شۆفێر",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        IconButton(onClick = onEditOrderClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "دەستکاری وەسڵ",
                                tint = DxpRoyalPlum
                            )
                        }
                        IconButton(onClick = onDeleteOrderClick) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "سڕینەوەی وەسڵ",
                                tint = StatusReturnedRed
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Quick Real-time Status Transition Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (order.status != OrderStatus.ON_THE_WAY.code && order.status != OrderStatus.DELIVERED.code) {
                    Button(
                        onClick = { onStatusChange(OrderStatus.ON_THE_WAY) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusOnTheWayBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("status_on_the_way_${order.trackingCode}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("لە ڕێگایە", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (order.status != OrderStatus.DELIVERED.code) {
                    Button(
                        onClick = { onStatusChange(OrderStatus.DELIVERED) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusDeliveredGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("status_delivered_${order.trackingCode}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("گەیشتووە ✓", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (order.status != OrderStatus.RETURNED.code) {
                    OutlinedButton(
                        onClick = onRequestReturnDialog,
                        border = BorderStroke(1.dp, StatusReturnedRed),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusReturnedRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("status_returned_${order.trackingCode}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("گەڕاوە", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showFullImageDialog && packageBitmap != null) {
        AlertDialog(
            onDismissRequest = { showFullImageDialog = false },
            title = {
                Text(
                    text = "وێنەی کاڵای وەسڵ (${order.trackingCode})",
                    style = MaterialTheme.typography.titleMedium,
                    color = DxpRoyalPlum
                )
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    Image(
                        bitmap = packageBitmap,
                        contentDescription = "وێنەی کاڵا",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullImageDialog = false }) {
                    Text("داخستن")
                }
            }
        )
    }
}

@Composable
private fun FinancialColumn(
    label: String,
    value: String,
    highlight: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) DxpRoyalPlum else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FilledTonalCallButton(
    label: String,
    phone: String,
    tintColor: Color = StatusDeliveredGreen,
    onCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tintColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCall)
            .testTag("call_customer_btn_$phone")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "پەیوەندی",
                tint = tintColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tintColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
