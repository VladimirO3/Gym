package com.business.gym.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProductPlaceholder(
    val id: Int,
    val name: String,
    val price: String,
    val description: String
)

@Composable
fun ShopScreen(isAdmin: Boolean) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "МАГАЗИН",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(products) { product ->
                ShopProductCard(product)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Скоро открытие полноценного магазина...",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 16.dp)
        )
    }
}

@Composable
fun ShopProductCard(product: ProductPlaceholder) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f) 
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
                    .background(Color.DarkGray.copy(alpha = 0.3f)),
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
                    color = Color.White,
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
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Описание внизу
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
