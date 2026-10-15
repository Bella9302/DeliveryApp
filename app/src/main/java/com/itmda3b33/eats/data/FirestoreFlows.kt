package com.itmda3b33.eats.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Live query results as a Flow. The listener is removed when the collector stops. */
internal fun <T> Query.observe(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull(map))
        }
    }
    awaitClose { registration.remove() }
}

/** A live single document as a Flow. Emits null when the document does not exist. */
internal fun <T> DocumentReference.observe(map: (DocumentSnapshot) -> T?): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else {
            trySend(snapshot?.takeIf { it.exists() }?.let(map))
        }
    }
    awaitClose { registration.remove() }
}
