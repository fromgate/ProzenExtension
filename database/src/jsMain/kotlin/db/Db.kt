package common.db

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object Db {
    private val dbName = "ProzenDB"
    private var database: Database? = null
    private val mutex = Mutex()


    suspend fun getDb(): Database {
        database?.let { return it }
        return mutex.withLock {
            database?.let { return@withLock it }
            val db = openDatabase(dbName, 1) { db, oldVersion, _ ->
                if (oldVersion < 1) {
                    db.createObjectStore("generic_cache")
                    db.createObjectStore("final_urls")
                }
            }
            database = db
            db
        }
    }

    suspend fun get(storeName: String, key: String): Any? {
        return try {
            getDb().transaction(storeName) {
                objectStore(storeName).get(Key(key))
            }
        } catch (e: Throwable) {
            console.error("Error reading from $storeName ($key): ${e.message}")
            null
        }
    }

    suspend fun put(storeName: String, key: String, value: Any) {
        try {
            getDb().writeTransaction(storeName) {
                objectStore(storeName).put(value, Key(key))
            }
        } catch (e: Throwable) {
            console.error("Error writing to $storeName ($key): ${e.message}")
        }
    }

    suspend fun delete(storeName: String, key: String) {
        try {
            getDb().writeTransaction(storeName) {
                objectStore(storeName).delete(Key(key))
            }
        } catch (e: Throwable) {
            console.error("Error deleting from $storeName ($key): ${e.message}")
        }
    }

    suspend fun deleteByPrefix(storeName: String, prefix: String) {
        try {
            getDb().writeTransaction(storeName) {
                val idbKeyRange: dynamic = js("IDBKeyRange")
                val range = idbKeyRange.bound(prefix, "$prefix\uffff").unsafeCast<Key>()
                objectStore(storeName).delete(range)
            }
        } catch (e: Throwable) {
            console.error("Error deleting by prefix $prefix in $storeName: ${e.message}")
        }
    }

    suspend fun getRange(storeName: String, startKey: String, endKey: String): List<Any> {
        return try {
            getDb().transaction(storeName) {
                val idbKeyRange: dynamic = js("IDBKeyRange")
                val range = idbKeyRange.bound(startKey, endKey).unsafeCast<Key>()
                val results = mutableListOf<Any>()

                objectStore(storeName).openCursor(range, autoContinue = true).collect { cursor ->
                    val value = cursor.value
                    if (value != null) {
                        results.add(value)
                    }
                }
                results
            }
        } catch (e: Throwable) {
            console.error("Error reading range from $storeName ($startKey to $endKey): ${e.message}")
            emptyList()
        }
    }

    suspend fun clearStore(storeName: String) {
        try {
            getDb().writeTransaction(storeName) {
                objectStore(storeName).clear()
            }
        } catch (e: Throwable) {
            console.error("Error clearing store $storeName: ${e.message}")
        }
    }
}