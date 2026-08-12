package com.business.gym.ui.screen

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.business.gym.R
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.ProductResponse
import com.business.gym.ui.viewmodel.CartViewModel
import com.business.gym.ui.viewmodel.ShopViewModel

@Composable
fun ShopScreen(
    isAdmin: Boolean,
    cartViewModel: CartViewModel = viewModel(),
    onGoToCart: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    
    val shopViewModel: ShopViewModel = viewModel(
        factory = ShopViewModel.Factory(application)
    )

    val products by shopViewModel.products
    val isLoading by shopViewModel.isLoading
    
    var isShowingCart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shopViewModel.fetchProducts()
    }

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    val cartItems by cartViewModel.cartItems
    val totalItemsInCart = cartItems.sumOf { it.second }

    var editingProduct by remember { mutableStateOf<ProductResponse?>(null) }
    var isAddingProduct by remember { mutableStateOf(false) }
    var selectedProductForDetail by remember { mutableStateOf<ProductResponse?>(null) }

    // Dialogs
    if (editingProduct != null) {
        ProductEditDialog(
            product = editingProduct!!,
            onDismiss = { editingProduct = null },
            onSave = { name, price, desc, uri ->
                shopViewModel.updateProduct(context, editingProduct!!.id, name, price, desc, uri) {
                    editingProduct = null
                }
            },
            onDeletePhoto = {
                shopViewModel.deleteProductPhoto(editingProduct!!.id) {
                    editingProduct = null
                }
            }
        )
    }

    if (isAddingProduct) {
        ProductAddDialog(
            onDismiss = { isAddingProduct = false },
            onSave = { name, price, desc, uri ->
                shopViewModel.addProduct(context, name, price, desc, uri) {
                    isAddingProduct = false
                }
            }
        )
    }

    if (selectedProductForDetail != null) {
        ProductDetailScreen(
            product = selectedProductForDetail!!,
            cartViewModel = cartViewModel,
            onDismiss = { selectedProductForDetail = null },
            onGoToCart = {
                selectedProductForDetail = null
                isShowingCart = true
            }
        )
    }

    if (isShowingCart) {
        CartScreenInternal(
            cartViewModel = cartViewModel,
            onBack = { isShowingCart = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (isAdmin) {
                    IconButton(
                        onClick = { isAddingProduct = true },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, "Add Product", tint = Color.Red)
                    }
                }

                Text(
                    text = stringResource(R.string.shop_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    IconButton(onClick = { isShowingCart = true }) {
                        BadgedBox(
                            badge = {
                                if (totalItemsInCart > 0) {
                                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                                        Text("$totalItemsInCart")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ShoppingCart, "Cart", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading && products.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red)
                }
            } else if (products.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Товары не найдены", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(products) { product ->
                        val cartItem = cartViewModel.cartItems.value.find { it.first.id == product.id }
                        val countInCart = cartItem?.second ?: 0

                        ShopProductCard(
                            product = product,
                            countInCart = countInCart,
                            isAdmin = isAdmin,
                            onClick = { selectedProductForDetail = product },
                            onAddToCart = { 
                                cartViewModel.addToCart(context, ProductPlaceholder(
                                    product.id, product.name, product.price, product.description, product.imageUrl
                                )) 
                            },
                            onRemoveFromCart = { 
                                cartViewModel.removeFromCart(context, ProductPlaceholder(
                                    product.id, product.name, product.price, product.description, product.imageUrl
                                )) 
                            },
                            onBuyNow = {
                                if (countInCart == 0) {
                                    cartViewModel.addToCart(context, ProductPlaceholder(
                                        product.id, product.name, product.price, product.description, product.imageUrl
                                    ))
                                }
                                isShowingCart = true
                            },
                            onEdit = { editingProduct = product },
                            onDelete = { shopViewModel.deleteProduct(product.id) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.shop_coming_soon),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
fun CartScreenInternal(
    cartViewModel: CartViewModel,
    onBack: () -> Unit
) {
    val items = cartViewModel.cartItems.value
    val totalPrice = cartViewModel.getTotalPrice()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text = stringResource(R.string.tab_shop).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(R.string.cart_empty), color = Color.Gray)
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { pair ->
                    val (product, count) = pair
                    CartItemRow(product, count, cartViewModel, context)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.cart_total), style = MaterialTheme.typography.titleLarge)
                    Text(text = cartViewModel.formatPrice(totalPrice), style = MaterialTheme.typography.headlineMedium, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* Checkout */ },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.cart_checkout), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CartItemRow(product: ProductPlaceholder, count: Int, viewModel: CartViewModel, context: android.content.Context) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = NewsApiService.getFullUrl(context, product.imageUrl),
                contentDescription = product.name,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = ColorPainter(Color.DarkGray)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold)
                Text(text = product.price, color = Color.Red, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.removeFromCart(context, product) }) {
                    Icon(Icons.Default.Remove, null, tint = Color.Gray)
                }
                Text(text = "$count", fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.addToCart(context, product) }) {
                    Icon(Icons.Default.Add, null, tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun ShopProductCard(
    product: ProductResponse,
    countInCart: Int,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    onRemoveFromCart: () -> Unit,
    onBuyNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.DarkGray.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = NewsApiService.getFullUrl(context, product.imageUrl),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = ColorPainter(Color.DarkGray)
                    )
                }

                Column(
                    modifier = Modifier.padding(12.dp).weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = product.price,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { onBuyNow() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(stringResource(R.string.shop_buy), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.height(8.dp))

                    if (countInCart > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onRemoveFromCart() }) {
                                Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                            Text(text = "$countInCart", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { onAddToCart() }) {
                                Icon(Icons.Default.Add, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAddToCart() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.shop_add_to_cart), fontSize = 12.sp, color = Color.Red)
                        }
                    }
                }
            }

            if (isAdmin) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { onEdit() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailScreen(
    product: ProductResponse,
    cartViewModel: CartViewModel,
    onDismiss: () -> Unit,
    onGoToCart: () -> Unit
) {
    val context = LocalContext.current
    val cartItem = cartViewModel.cartItems.value.find { it.first.id == product.id }
    val countInCart = cartItem?.second ?: 0

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    AsyncImage(
                        model = NewsApiService.getFullUrl(context, product.imageUrl),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(modifier = Modifier.padding(24.dp).weight(1f).verticalScroll(rememberScrollState())) {
                    Text(text = product.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(text = product.price, style = MaterialTheme.typography.headlineMedium, color = Color.Red, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = product.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (countInCart > 0) {
                        Row(
                            modifier = Modifier.weight(1f).height(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { cartViewModel.removeFromCart(context, ProductPlaceholder(product.id, product.name, product.price, product.description, product.imageUrl)) }) {
                                Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = "$countInCart", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { cartViewModel.addToCart(context, ProductPlaceholder(product.id, product.name, product.price, product.description, product.imageUrl)) }) {
                                Icon(Icons.Default.Add, null, tint = Color.Red)
                            }
                        }
                    } else {
                        Button(
                            onClick = { cartViewModel.addToCart(context, ProductPlaceholder(product.id, product.name, product.price, product.description, product.imageUrl)) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null)
                            Spacer(Modifier.width(8.dp))
                            Text("В корзину")
                        }
                    }

                    Button(
                        onClick = onGoToCart,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Оформить")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    product: ProductResponse,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Uri?) -> Unit,
    onDeletePhoto: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var price by remember { mutableStateOf(product.price) }
    var desc by remember { mutableStateOf(product.description) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selectedUri = it
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать товар") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { photoPicker.launch("image/*") }
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        AsyncImage(model = selectedUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        AsyncImage(
                            model = NewsApiService.getFullUrl(context, product.imageUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = ColorPainter(Color.DarkGray)
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoCamera, null, tint = Color.White)
                        }
                    }
                }
                
                if (product.imageUrl.isNotBlank() && selectedUri == null) {
                    TextButton(
                        onClick = onDeletePhoto,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Удалить текущее фото", color = Color.Red, fontSize = 12.sp)
                    }
                } else {
                    Text("Нажмите, чтобы сменить фото", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Цена (например: 1 500 ₽)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, price, desc, selectedUri) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ProductAddDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Uri) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selectedUri = it
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить товар") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { photoPicker.launch("image/*") }
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        AsyncImage(model = selectedUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Цена (например: 1 500 ₽)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUri?.let { onSave(name, price, desc, it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                enabled = name.isNotBlank() && price.isNotBlank() && selectedUri != null
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

data class ProductPlaceholder(
    val id: Int,
    val name: String,
    val price: String,
    val description: String,
    val imageUrl: String
)
