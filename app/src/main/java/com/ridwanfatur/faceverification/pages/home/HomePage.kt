package com.ridwanfatur.faceverification.pages.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ridwanfatur.faceverification.RouteConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ridwanfatur.faceverification.database.AppDatabase
import com.ridwanfatur.faceverification.models.FaceItem
import com.ridwanfatur.faceverification.pages.home.components.DeleteFaceDataDialog
import com.ridwanfatur.faceverification.pages.home.components.FaceItemCard
import com.ridwanfatur.faceverification.pages.home.components.rememberCameraPermissionLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson

@Composable
fun HomePage(navController: NavHostController) {
    var isLoading by remember { mutableStateOf(true) }
    var faceItems by remember { mutableStateOf(listOf<FaceItem>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FaceItem?>(null) }
    var hasDataLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val faceItemDao = db.faceItemDao()
    val coroutineScope = rememberCoroutineScope()

    val toAddFacePage = rememberCameraPermissionLauncher {
        navController.navigate(RouteConstants.ADD_FACE_PAGE)
    }
    val toVerifyFacePage = rememberCameraPermissionLauncher {
        val gson = Gson()
        val listJson = gson.toJson(faceItems)
        navController.currentBackStackEntry?.savedStateHandle?.set("faceItems", listJson)
        navController.navigate(RouteConstants.VERIFY_FACE_PAGE)
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isCurrentlyOnHomePage =
        currentBackStackEntry?.destination?.route == RouteConstants.HOME_PAGE

    LaunchedEffect(isCurrentlyOnHomePage) {
        if (!hasDataLoaded && isCurrentlyOnHomePage) {
            isLoading = true
            faceItemDao.getAllFaceItemsFlow().collectLatest { items ->
                faceItems = items
                isLoading = false
                hasDataLoaded = true
            }
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(50.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading faces...",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(faceItems) { item ->
                        FaceItemCard(
                            item = item,
                            onDeleteClick = {
                                itemToDelete = item
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    toAddFacePage()
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add Face",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Add Face",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    toVerifyFacePage()
                                },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = "Verify Face",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Verify Face",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        DeleteFaceDataDialog(
            itemName = itemToDelete?.name ?: "",
            onDismissRequest = {
                showDeleteDialog = false
                itemToDelete = null
            },
            onConfirm = {
                coroutineScope.launch {
                    withContext(Dispatchers.Default) {
                        if (itemToDelete != null) {
                            faceItemDao.delete(itemToDelete!!)

                            itemToDelete?.let { item ->
                                faceItems = faceItems.filter { it.id != item.id }
                            }

                            showDeleteDialog = false
                            itemToDelete = null
                        }
                    }
                }
            },
            onCancel = {
                showDeleteDialog = false
                itemToDelete = null
            },
        )
    }
}
