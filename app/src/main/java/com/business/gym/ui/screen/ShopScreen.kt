package com.business.gym.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.CartViewModel

data class ProductPlaceholder(
    val id: Int,
    val name: String,
    val price: String,
    val description: String
)

@Composable
fun ShopScreen(
    isAdmin: Boolean,
    cartViewModel: CartViewModel = viewModel(),
    onGoToCart: () -> Unit = {}
) {
    val products = listOf(
        ProductPlaceholder(1, "Протеин Whey", "3 500 ₽", "Сывороточный протеин для быстрого восстановления мышц."),
        ProductPlaceholder(2, "Креатин 500г", "1 200 ₽", "Микронизированный креатин моногидрат для силы."),
        ProductPlaceholder(3, "BCAA 2:1:1", "1 800 ₽", "Аминокислотный комплекс для защиты мышц."),
        ProductPlaceholder(4, "Гейнер 2кг", "2 900 ₽", "Высококалорийная смесь для набора массы."),
        ProductPlaceholder(5, "Шейкер 700мл", "550 ₽", "Стильный шейкер с логотипом GYM."),
        ProductPlaceholder(6, "Витамины", "990 ₽", "Мультивитаминный комплекс для спортсменов.")
    )

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    
    val cartItems by cartViewModel.cartItems
    val totalItemsInCart = cartItems.sumOf { it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.shop_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
            
            // Кнопка перехода в корзину с бейджем
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                IconButton(onClick = onGoToCart) {
                    BadgedBox(
                        badge = {
                            if (totalItemsInCart > 0) {
                                Badge(
                                    containerColor = Color.Red,
                                    contentColor = Color.White
                                ) {
                                    Text("$totalItemsInCart")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "В корзину",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                    onAddToCart = { cartViewModel.addToCart(product) },
                    onRemoveFromCart = { cartViewModel.removeFromCart(product) },
                    onBuyNow = {
                        if (countInCart == 0) {
                            cartViewModel.addToCart(product)
                        }
                        onGoToCart()
                    }
                )
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

@Composable
fun ShopProductCard(
    product: ProductPlaceholder,
    countInCart: Int,
    onAddToCart: () -> Unit,
    onRemoveFromCart: () -> Unit,
    onBuyNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Заглушка изображения
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.DarkGray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = Color.Red.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
            }

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f),
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

                // Кнопки
                Button(
                    onClick = onBuyNow,
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
                        IconButton(onClick = onRemoveFromCart) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Text(
                            text = "$countInCart",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        IconButton(onClick = onAddToCart) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onAddToCart,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (countInCart > 0) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White
                                    ) {
                                        Text("$countInCart")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.shop_add_to_cart), fontSize = 12.sp, color = Color.Red)
                    }
                }
            }
        }
    }
}
