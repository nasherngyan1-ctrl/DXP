package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DeliveryOrder
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.formatIqd
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.StatusReturnedRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditOrderDialog(
    existingOrder: DeliveryOrder? = null,
    drivers: List<StaffMember>,
    onDismiss: () -> Unit,
    onSave: (
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
        assignedDriverName: String,
        status: String,
        returnReason: String,
        isSettled: Boolean
    ) -> Unit
) {
    var trackingCode by remember {
        mutableStateOf(existingOrder?.trackingCode ?: "DXP-${(1000..9999).random()}")
    }
    var senderName by remember { mutableStateOf(existingOrder?.senderName ?: "") }
    var customerName by remember { mutableStateOf(existingOrder?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(existingOrder?.customerPhone ?: "") }
    var cityArea by remember { mutableStateOf(existingOrder?.cityArea ?: "هەولێر") }
    var addressDetails by remember { mutableStateOf(existingOrder?.addressDetails ?: "") }
    var notes by remember { mutableStateOf(existingOrder?.notes ?: "") }
    var itemPriceText by remember {
        mutableStateOf(existingOrder?.itemPriceIqd?.toString() ?: "25000")
    }
    var deliveryFeeText by remember {
        mutableStateOf(existingOrder?.deliveryFeeIqd?.toString() ?: "4000")
    }
    var selectedDriverId by remember { mutableStateOf(existingOrder?.assignedDriverId ?: "") }
    var selectedDriverName by remember { mutableStateOf(existingOrder?.assignedDriverName ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val parsedItemPrice = itemPriceText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val parsedDeliveryFee = deliveryFeeText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val totalAmount = parsedItemPrice + parsedDeliveryFee

    val presetCities = listOf("هەولێر", "سلێمانی", "دهۆک", "کەرکووک", "هەڵەبجە", "سۆران", "زاحۆ")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingOrder == null) "تۆمارکردنی وەسڵی نوێ (DXP POST)" else "دەستکاریکردنی وەسڵ",
                style = MaterialTheme.typography.titleLarge,
                color = DxpRoyalPlum
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = trackingCode,
                    onValueChange = { trackingCode = it },
                    label = { Text("کۆدی وەسڵ (Tracking Code)") },
                    singleLine = true,
                    enabled = existingOrder == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tracking_code")
                )

                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("ناوی فرۆشیار / پەیج (ئارەزوومەندانە)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_sender_name")
                )

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("ناوی کڕیار (وەرگر) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_name")
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("ژمارە مۆبایلی کڕیار *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_phone")
                )

                Text(
                    text = "شار / ناوچە هەڵبژێرە یان بنووسە:",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetCities.forEach { city ->
                        FilterChip(
                            selected = cityArea.startsWith(city),
                            onClick = { cityArea = city },
                            label = { Text(city) }
                        )
                    }
                }

                OutlinedTextField(
                    value = cityArea,
                    onValueChange = { cityArea = it },
                    label = { Text("شار و گەڕەک *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_city_area")
                )

                OutlinedTextField(
                    value = addressDetails,
                    onValueChange = { addressDetails = it },
                    label = { Text("وردەکاری ناونیشان و نزیکترین شوێن") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_address_details")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = itemPriceText,
                        onValueChange = { itemPriceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("نرخی کاڵا (دینار)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_item_price")
                    )

                    OutlinedTextField(
                        value = deliveryFeeText,
                        onValueChange = { deliveryFeeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("کرێی گەیاندن") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_delivery_fee")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DxpRoyalPlum.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "کۆی گشتی وەرگرتن لە کڕیار:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatIqd(totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            color = DxpRoyalPlum,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("تێبینی بۆ شۆفێر") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_order_notes")
                )

                Text(
                    text = "دیاریکردنی شۆفێری گەیاندن (مەندووب):",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedDriverId.isBlank(),
                        onClick = {
                            selectedDriverId = ""
                            selectedDriverName = ""
                        },
                        label = { Text("بێ شۆفێر (چاوەڕوان)") }
                    )
                    drivers.forEach { driver ->
                        FilterChip(
                            selected = selectedDriverId == driver.id,
                            onClick = {
                                selectedDriverId = driver.id
                                selectedDriverName = driver.name
                            },
                            label = { Text(driver.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customerName.isBlank() || customerPhone.isBlank() || cityArea.isBlank()) {
                        errorText = "تکایە ناوی کڕیار، ژمارە مۆبایل و شار پڕ بکەرەوە"
                        return@Button
                    }
                    val resolvedStatus = existingOrder?.status ?: if (selectedDriverId.isNotBlank()) {
                        OrderStatus.ASSIGNED.code
                    } else {
                        OrderStatus.PENDING.code
                    }
                    onSave(
                        trackingCode,
                        senderName,
                        customerName,
                        customerPhone,
                        cityArea,
                        addressDetails,
                        notes,
                        parsedItemPrice,
                        parsedDeliveryFee,
                        selectedDriverId,
                        selectedDriverName,
                        resolvedStatus,
                        existingOrder?.returnReason ?: "",
                        existingOrder?.isSettled ?: false
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                modifier = Modifier.testTag("confirm_save_order_btn")
            ) {
                Text("پاشەکەوتکردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("پاشگەزبوونەوە")
            }
        }
    )
}

@Composable
fun AssignDriverDialog(
    order: DeliveryOrder,
    drivers: List<StaffMember>,
    onDismiss: () -> Unit,
    onSelectDriver: (driverId: String, driverName: String) -> Unit
) {
    var chosenId by remember { mutableStateOf(order.assignedDriverId) }
    var chosenName by remember { mutableStateOf(order.assignedDriverName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "دیاریکردنی مەندووب بۆ وەسڵی ${order.trackingCode}",
                style = MaterialTheme.typography.titleLarge,
                color = DxpRoyalPlum
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            chosenId = ""
                            chosenName = ""
                        }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = chosenId.isBlank(),
                        onClick = {
                            chosenId = ""
                            chosenName = ""
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بێ شۆفێر (لە کۆگا / چاوەڕوان)")
                }

                drivers.forEach { driver ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosenId = driver.id
                                chosenName = driver.name
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = chosenId == driver.id,
                            onClick = {
                                chosenId = driver.id
                                chosenName = driver.name
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = driver.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (driver.vehicleInfo.isNotBlank() || driver.phone.isNotBlank()) {
                                Text(
                                    text = "${driver.vehicleInfo} • ${driver.phone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectDriver(chosenId, chosenName) },
                colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum)
            ) {
                Text("جێگیرکردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("داخستن")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReturnOrderReasonDialog(
    order: DeliveryOrder,
    onDismiss: () -> Unit,
    onConfirmReturn: (reason: String) -> Unit
) {
    val presetReasons = listOf(
        "مۆبایل داخراوە / وەڵام ناداتەوە",
        "ناونیشان هەڵەیە",
        "کڕیار پەشیمان بووەتەوە",
        "داوای دواخستنی بۆ سبەی کرد",
        "کاڵاکەی بەدڵ نەبوو"
    )
    var reason by remember {
        mutableStateOf(order.returnReason.ifBlank { presetReasons.first() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "هۆکاری گەڕانەوەی وەسڵ (${order.trackingCode})",
                style = MaterialTheme.typography.titleLarge,
                color = StatusReturnedRed
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("هۆکارێک هەڵبژێرە یان بینووسە:")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetReasons.forEach { preset ->
                        FilterChip(
                            selected = reason == preset,
                            onClick = { reason = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("هۆکاری گەڕانەوە") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReturn(reason.ifBlank { "گەڕاوەتەوە" }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusReturnedRed,
                    contentColor = Color.White
                )
            ) {
                Text("تۆمارکردن وەک گەڕاوە")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("پاشگەزبوونەوە")
            }
        }
    )
}

@Composable
fun AddOrEditStaffDialog(
    existingMember: StaffMember? = null,
    isAdminEditor: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        email: String,
        role: StaffRole,
        vehicleInfo: String,
        active: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(existingMember?.name ?: "") }
    var phone by remember { mutableStateOf(existingMember?.phone ?: "") }
    var email by remember { mutableStateOf(existingMember?.email ?: "") }
    var vehicleInfo by remember {
        mutableStateOf(existingMember?.vehicleInfo ?: "ماتۆڕ / ناوشار")
    }
    var role by remember {
        mutableStateOf(
            if (existingMember != null) StaffRole.fromCode(existingMember.role) else StaffRole.DRIVER
        )
    }
    var active by remember { mutableStateOf(existingMember?.active ?: true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingMember == null) "زیادکردنی ستاف (٢ ئەدمین و ١٠ شۆفێر)" else "دەستکاریکردنی زانیاری ستاف",
                style = MaterialTheme.typography.titleLarge,
                color = DxpRoyalPlum
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ناوی سیانی شۆفێر / ئەدمین *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_driver_name")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("ژمارە مۆبایل *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_driver_phone")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ئیمەیڵی گووگڵ (بۆ چوونەژوورەوەی ئامێرەکەی)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_driver_email")
                )

                OutlinedTextField(
                    value = vehicleInfo,
                    onValueChange = { vehicleInfo = it },
                    label = { Text("جۆری هۆکاری گواستنەوە و ناوچەی کارکردن") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_driver_vehicle")
                )

                if (isAdminEditor) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ڕۆڵ لە کۆمپانیا:", style = MaterialTheme.typography.labelLarge)
                        FilterChip(
                            selected = role == StaffRole.DRIVER,
                            onClick = { role = StaffRole.DRIVER },
                            label = { Text("شۆفێر (١٠)") }
                        )
                        FilterChip(
                            selected = role == StaffRole.OWNER,
                            onClick = { role = StaffRole.OWNER },
                            label = { Text("ئەدمین (٢)") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ئامادەیە بۆ وەرگرتنی پاکێج:", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorText = "تکایە ناوی شۆفێر یان ئەدمین بنووسە"
                        return@Button
                    }
                    onSave(name, phone, email, role, vehicleInfo, active)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                modifier = Modifier.testTag("confirm_save_driver_btn")
            ) {
                Text("پاشەکەوتکردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("پاشگەزبوونەوە")
            }
        }
    )
}
