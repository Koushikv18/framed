package com.aesthetic.framed.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.framed.domain.export.ExifHelper
import com.aesthetic.framed.domain.recipe.EditRecipe
import com.aesthetic.framed.domain.recipe.EditRecipeStore
import com.aesthetic.framed.ui.theme.AmberRetro
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioBlack
import com.aesthetic.framed.ui.theme.StudioBorder
import com.aesthetic.framed.ui.theme.StudioDark
import com.aesthetic.framed.ui.theme.StudioSurface
import com.aesthetic.framed.ui.theme.StudioText
import com.aesthetic.framed.ui.theme.StudioTextMuted
import com.aesthetic.framed.ui.theme.StudioTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun FramedLibraryScreen(
    recipeStore: EditRecipeStore,
    onBack: () -> Unit,
    onRecipeSelected: (EditRecipe) -> Unit,
    onPhotoImported: (Uri, EditRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf(recipeStore.getAllRecipes()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val originalsDir = File(context.filesDir, "originals").apply {
                            if (!exists()) mkdirs()
                        }
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val importedFile = File(originalsDir, "IMPORTED_${timeStamp}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(importedFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val localUri = Uri.fromFile(importedFile)
                        val recipe = EditRecipe(
                            id = UUID.randomUUID().toString(),
                            sourceUriString = localUri.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                        recipeStore.saveRecipe(recipe)
                        withContext(Dispatchers.Main) {
                            onPhotoImported(localUri, recipe)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = StudioText, fontSize = 14.sp)
                }

                Text(
                    text = "FRAMED LIBRARY",
                    color = StudioText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sync Latest Aesthetics Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurface)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.aesthetic.framed.domain.ai.SelfUpgradeWorker>().build()
                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                android.widget.Toast.makeText(context, "Syncing latest aesthetic trends...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "SYNC AI",
                            color = StudioTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Import from Gallery Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurface)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "IMPORT",
                            color = EmeraldLevel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (recipes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO CAPTURES SAVED",
                            color = StudioTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Take a photo or import from gallery to begin.",
                            color = StudioTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        LibraryCard(
                            recipe = recipe,
                            onClick = { onRecipeSelected(recipe) },
                            onDelete = {
                                recipeStore.deleteRecipe(recipe.id)
                                recipes = recipeStore.getAllRecipes()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    recipe: EditRecipe,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(recipe.sourceUriString) {
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(recipe.sourceUriString)
            thumbnail = ExifHelper.decodeRotatedBitmap(context, uri, maxDimension = 512)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioDark)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(StudioBlack),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = "Capture Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = "...", color = StudioTextMuted, fontSize = 11.sp)
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(StudioDark.copy(alpha = 0.85f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = StudioTextMuted, fontSize = 10.sp)
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = recipe.adjustments.preset.title,
                        color = EmeraldLevel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = recipe.exportOptions.framePreset.title,
                        color = StudioTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(recipe.timestamp))
                Text(
                    text = dateStr,
                    color = StudioTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
