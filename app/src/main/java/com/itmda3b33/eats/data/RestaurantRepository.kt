package com.itmda3b33.eats.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.itmda3b33.eats.data.model.MenuItem
import com.itmda3b33.eats.data.model.Restaurant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Restaurants and their menu items (Module B). */
class RestaurantRepository(private val db: FirebaseFirestore) {

    private fun menu(restaurantId: String) =
        db.collection(Collections.RESTAURANTS).document(restaurantId).collection(Collections.MENU_ITEMS)

    fun observeRestaurants(): Flow<List<Restaurant>> =
        db.collection(Collections.RESTAURANTS).orderBy(Fields.NAME).observe { it.toRestaurant() }

    fun observeRestaurant(restaurantId: String): Flow<Restaurant?> =
        db.collection(Collections.RESTAURANTS).document(restaurantId).observe { it.toRestaurant() }

    fun observeMenu(restaurantId: String): Flow<List<MenuItem>> =
        menu(restaurantId).orderBy(Fields.NAME, Query.Direction.ASCENDING).observe { it.toMenuItem(restaurantId) }

    suspend fun getMenuItem(restaurantId: String, itemId: String): MenuItem? =
        menu(restaurantId).document(itemId).get().await().takeIf { it.exists() }?.toMenuItem(restaurantId)

    suspend fun addMenuItem(item: MenuItem): String {
        val ref = menu(item.restaurantId).document()
        ref.set(item.toMap()).await()
        return ref.id
    }

    suspend fun updateMenuItem(item: MenuItem) {
        menu(item.restaurantId).document(item.id).set(item.toMap()).await()
    }

    suspend fun deleteMenuItem(restaurantId: String, itemId: String) {
        menu(restaurantId).document(itemId).delete().await()
    }
}
