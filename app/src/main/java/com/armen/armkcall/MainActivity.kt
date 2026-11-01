package com.armen.armkcall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.*

data class FoodItem(val name: String, val calories: Double, val protein: Double, val fat: Double, val carbs: Double)
data class USDAFood(val description: String, val foodNutrients: List<Nutrient>?)
data class Nutrient(val nutrientName: String, val value: Double)
data class USDAResponse(val foods: List<USDAFood>?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var eatenFood by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dailyCalories = eatenFood.sumOf { it.calories }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("armkcall", style = MaterialTheme.typography.headlineMedium)
        Text("Съедено сегодня: $dailyCalories ккал", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Поиск продукта (на англ.)") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Button(onClick = {
                    if (searchText.isNotBlank()) {
                        isSearching = true
                        scope.launch {
                            searchResults = searchFood(searchText)
                            isSearching = false
                        }
                    }
                }) { Text("Найти") }
            }
        )

        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
            if (searchResults.isNotEmpty()) {
                item { Text("Результаты поиска:", style = MaterialTheme.typography.titleSmall) }
                items(searchResults) { food ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                eatenFood = eatenFood + food
                                searchResults = emptyList()
                                searchText = ""
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(food.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${food.calories} ккал", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                item { Text("Съедено за день:", style = MaterialTheme.typography.titleSmall) }
                items(eatenFood) { food ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(food.name, style = MaterialTheme.typography.bodyLarge)
                        Text("Б: ${food.protein}г Ж: ${food.fat}г У: ${food.carbs}г", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

suspend fun searchFood(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
    val apiKey = "ТВОЙ_КЛЮЧ_USDA"
    val url = "https://api.nal.usda.gov/fdc/v1/foods/search?query=$query&api_key=$apiKey&pageSize=5"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    
    try {
        val response = client.newCall(request).execute()
        val json = response.body?.string() ?: return@withContext emptyList()
        val data = Gson().fromJson(json, USDAResponse::class.java)
        
        data.foods?.map { food ->
            val nutrients = food.foodNutrients ?: emptyList()
            val cal = nutrients.find { it.nutrientName.contains("Energy") }?.value ?: 0.0
            val prot = nutrients.find { it.nutrientName == "Protein" }?.value ?: 0.0
            val fat = nutrients.find { it.nutrientName.contains("Total lipid") }?.value ?: 0.0
            val carb = nutrients.find { it.nutrientName.contains("Carbohydrate") }?.value ?: 0.0
            FoodItem(food.description, cal, prot, fat, carb)
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
