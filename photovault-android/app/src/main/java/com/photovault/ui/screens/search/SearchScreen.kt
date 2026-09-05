package com.photovault.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.screens.gallery.GalleryTile
import com.photovault.ui.theme.IrisLight
import com.photovault.ui.theme.IrisPrimary
import com.photovault.ui.theme.IrisSubtle
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.Slate200
import com.photovault.ui.theme.Slate400
import com.photovault.ui.theme.Slate50
import com.photovault.ui.theme.Slate800
import com.photovault.ui.theme.Slate900
import com.photovault.ui.theme.Slate950
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onMediaSelected: (fileId: String) -> Unit
) {
    val app = PhotoVaultApplication.instance
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val columns by app.preferenceStore.gridColumns.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    fun executeSearch() {
        if (searchQuery.isBlank() && selectedFilter == "all") {
            results = emptyList()
            return
        }
        isSearching = true
        scope.launch {
            val mime = when (selectedFilter) {
                "photos" -> "image/"
                "videos" -> "video/"
                else -> ""
            }
            val fav = selectedFilter == "favorites"
            val res = app.apiClient.fetchMedia(
                query = searchQuery.trim(),
                mimeType = mime,
                favoriteOnly = fav
            )
            isSearching = false
            res.onSuccess {
                results = it
            }
        }
    }

    LaunchedEffect(searchQuery, selectedFilter) {
        executeSearch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Input Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Search",
                        style = MnemosType.PageTitle20,
                        color = Slate50
                    )

                    // Text Input Bar with Hairline Border
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            textStyle = MnemosType.CardTitle15.copy(color = Slate50),
                            singleLine = true,
                            cursorBrush = SolidColor(IrisPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                executeSearch()
                            }),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search filename, date, node…",
                                        style = MnemosType.CardTitle15,
                                        color = Slate400
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    HapticHelper.performClick(view)
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Filter Row (Slate900 pills with 1dp Slate800 border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "all" to "ALL",
                            "photos" to "PHOTOS",
                            "videos" to "VIDEOS",
                            "favorites" to "FAVORITES"
                        ).forEach { (key, label) ->
                            val isSelected = selectedFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) IrisSubtle else Slate900)
                                    .border(1.dp, if (isSelected) IrisPrimary else Slate800, RoundedCornerShape(6.dp))
                                    .clickable {
                                        HapticHelper.performClick(view)
                                        selectedFilter = key
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MnemosType.Label11,
                                    color = if (isSelected) IrisLight else Slate400
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Search Content / Grid
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IrisPrimary, strokeWidth = 2.dp)
                }
            } else if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "INDEXED VAULT" else "NO MATCHES",
                            style = MnemosType.Label11,
                            color = Slate400
                        )
                        Text(
                            text = if (searchQuery.isBlank())
                                "Search across all paired nodes by filename, file type, or capture date"
                            else
                                "No media found matching \"$searchQuery\"",
                            style = MnemosType.BodySecondary13,
                            color = Slate400,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Full-bleed search grid with 1dp hairline gutter
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.fileId }) { item ->
                        GalleryTile(
                            media = item,
                            onClick = { onMediaSelected(item.fileId) }
                        )
                    }
                }
            }
        }
    }
}
