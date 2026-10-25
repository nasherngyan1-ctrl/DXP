package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DeliveryOrder
import com.example.data.model.OrderStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.formatIqd
import com.example.ui.components.decodeBase64ToImageBitmap
import com.example.ui.theme.DxpRoyalPlum
import com.example.ui.theme.StatusReturnedRed
import java.io.ByteArrayOutputStream
import kotlin.math.max

private fun compressBitmapToBase64(bitmap: Bitmap, maxDim: Int = 480, quality: Int = 65): String {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val largest = max(width, height)
    val scaled = if (largest > maxDim) {
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun encodeUriToBase64(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream) ?: return null
            compressBitmapToBase64(bitmap)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AdminSecretCodeDialog(
    onDismiss: () -> Unit,
    onUnlockWithCode: (code: String, onError: (String) -> Unit) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = DxpRoyalPlum
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "چوونە ناو بەشی ئەدمین",
                    style = MaterialTheme.typography.titleLarge,
                    color = DxpRoyalPlum
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "بۆ کردنەوەی دەسەڵاتی تەواوی ئەدمین (بێ سنوور)، کۆدی تایبەتی ئەدمین بنووسە:",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        errorMsg = null
                    },
                    label = { Text("کۆدی تایبەتی ئەدمین") },
                    placeholder = { Text("DXPPOST") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_secret_code")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUnlockWithCode(codeInput) { err ->
                        errorMsg = err
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                modifier = Modifier.testTag("confirm_admin_secret_code_btn")
            ) {
                Text("چالاککردنی ئەدمین")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("پاشگەزبوونەوە")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditOrderDialog(
    existingOrder: DeliveryOrder? = null,
    drivers: List<StaffMember>,
    currentUserId: String = "",
    currentUserDisplayName: String = "",
    isOwnerView: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        trackingCode: String,
        senderName: String,
        senderPhone: String,
        customerName: String,
        customerPhone: String,
        cityArea: String,
        addressDetails: String,
        notes: String,
        packageImageBase64: String,
        itemPriceIqd: Long,
        deliveryFeeIqd: Long,
        assignedDriverId: String,
        assignedDriverName: String,
        status: String,
        returnReason: String,
        isSettled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var trackingCode by remember {
        mutableStateOf(existingOrder?.trackingCode ?: "DXP-${(1000..9999).random()}")
    }
    var senderName by remember { mutableStateOf(existingOrder?.senderName ?: "") }
    var senderPhone by remember { mutableStateOf(existingOrder?.senderPhone ?: "") }
    var customerName by remember { mutableStateOf(existingOrder?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(existingOrder?.customerPhone ?: "") }
    var cityArea by remember { mutableStateOf(existingOrder?.cityArea ?: "هەولێر") }
    var addressDetails by remember { mutableStateOf(existingOrder?.addressDetails ?: "") }
    var notes by remember { mutableStateOf(existingOrder?.notes ?: "") }
    var packageImageBase64 by remember { mutableStateOf(existingOrder?.packageImageBase64 ?: "") }
    var itemPriceText by remember {
        mutableStateOf(existingOrder?.itemPriceIqd?.toString() ?: "25000")
    }
    var deliveryFeeText by remember {
        mutableStateOf(existingOrder?.deliveryFeeIqd?.toString() ?: "4000")
    }
    var selectedDriverId by remember {
        mutableStateOf(
            existingOrder?.assignedDriverId
                ?: if (!isOwnerView) currentUserId else ""
        )
    }
    var selectedDriverName by remember {
        mutableStateOf(
            existingOrder?.assignedDriverName
                ?: if (!isOwnerView) currentUserDisplayName else ""
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = encodeUriToBase64(context, uri)
            if (encoded != null) {
                packageImageBase64 = encoded
            }
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            packageImageBase64 = compressBitmapToBase64(bitmap)
        }
    }

    val previewBitmap = remember(packageImageBase64) {
        decodeBase64ToImageBitmap(packageImageBase64)
    }

    val parsedItemPrice = itemPriceText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val parsedDeliveryFee = deliveryFeeText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val totalAmount = parsedItemPrice + parsedDeliveryFee

    val presetCities = listOf("هەولێر", "سلێمانی", "دهۆک", "کەرکووک", "هەڵەبجە", "سۆران", "زاخۆ")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingOrder == null) "تۆمارکردنی کاڵا / وەسڵی نوێ" else "دەستکاریکردنی وەسڵ",
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Package Photo Section (وێنەی کاڵا)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DxpRoyalPlum.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, DxpRoyalPlum.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "وێنەی کاڵا / پاکێج:",
                                style = MaterialTheme.typography.labelLarge,
                                color = DxpRoyalPlum,
                                fontWeight = FontWeight.Bold
                            )
                            if (packageImageBase64.isNotBlank()) {
                                IconButton(
                                    onClick = { packageImageBase64 = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "سڕینەوەی وێنە",
                                        tint = StatusReturnedRed
                                    )
                                }
                            }
                        }

                        if (previewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "وێنەی کاڵا",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_package_photo_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("گەلەری", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    cameraPreviewLauncher.launch(null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DxpRoyalPlum),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("capture_package_photo_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("کامێرا", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
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

                // Sender Details (نێرەر / ئەو کەسەی ناردوویەتی)
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("ناوی نێرەر / فرۆشگا (ئەو کەسەی ناردوویەتی)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_sender_name")
                )

                OutlinedTextField(
                    value = senderPhone,
                    onValueChange = { senderPhone = it },
                    label = { Text("ژمارە مۆبایلی نێرەر (ئەو کەسەی ناردوویەتی) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_sender_phone")
                )

                // Receiver Details (وەرگر / ئەو کەسەی وەری دەگرێت)
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("ناوی وەرگر (کڕیار) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_name")
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("ژمارە مۆبایلی وەرگر (ئەو کەسەی وەری دەگرێت) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_customer_phone")
                )

                // Location Details (شوێن و ناونیشان)
                Text(
                    text = "شوێن (شار / ناوچە) هەڵبژێرە یان بنووسە:",
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
                    label = { Text("شوێن (شار و گەڕەک) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_city_area")
                )

                OutlinedTextField(
                    value = addressDetails,
                    onValueChange = { addressDetails = it },
                    label = { Text("وردەکاری شوێن و ناونیشانی تەواو") },
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
                            text = "کۆی گشتی وەرگرتن لە وەرگر:",
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
                    label = { Text("تێبینی لەسەر کاڵا") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_order_notes")
                )

                if (isOwnerView) {
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
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DxpRoyalPlum.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "شۆفێری تۆمارکەر: ${currentUserDisplayName.ifBlank { "شۆفێر" }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = DxpRoyalPlum,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customerName.isBlank() || customerPhone.isBlank() || cityArea.isBlank()) {
                        errorText = "تکایە ناوی وەرگر، ژمارەی مۆبایلی وەرگر و شوێن پڕ بکەرەوە"
                        return@Button
                    }
                    val finalDriverId = if (!isOwnerView && selectedDriverId.isBlank()) {
                        currentUserId
                    } else {
                        selectedDriverId
                    }
                    val finalDriverName = if (!isOwnerView && selectedDriverName.isBlank()) {
                        currentUserDisplayName.ifBlank { "شۆفێر" }
                    } else {
                        selectedDriverName
                    }
                    val resolvedStatus = existingOrder?.status ?: if (finalDriverId.isNotBlank()) {
                        OrderStatus.ASSIGNED.code
                    } else {
                        OrderStatus.PENDING.code
                    }
                    onSave(
                        trackingCode,
                        senderName,
                        senderPhone,
                        customerName,
                        customerPhone,
                        cityArea,
                        addressDetails,
                        notes,
                        packageImageBase64,
                        parsedItemPrice,
                        parsedDeliveryFee,
                        finalDriverId,
                        finalDriverName,
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
                text = if (existingMember == null) "زیادکردنی ئەدمین یان شۆفێر" else "دەستکاریکردنی زانیاری ستاف",
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
                            label = { Text("شۆفێر (مەندووب)") }
                        )
                        FilterChip(
                            selected = role == StaffRole.OWNER,
                            onClick = { role = StaffRole.OWNER },
                            label = { Text("ئەدمین (بێ سنوور)") }
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
