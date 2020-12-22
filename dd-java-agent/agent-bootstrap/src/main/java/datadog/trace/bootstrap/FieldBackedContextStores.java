package datadog.trace.bootstrap;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FieldBackedContextStores {

  // provide fast lookup for a fixed number of stores
  public static final int FAST_STORE_ID_LIMIT = 32;

  public static final FieldBackedContextStore contextStore0 = new FieldBackedContextStore(0);
  public static final FieldBackedContextStore contextStore1 = new FieldBackedContextStore(1);
  public static final FieldBackedContextStore contextStore2 = new FieldBackedContextStore(2);
  public static final FieldBackedContextStore contextStore3 = new FieldBackedContextStore(3);
  public static final FieldBackedContextStore contextStore4 = new FieldBackedContextStore(4);
  public static final FieldBackedContextStore contextStore5 = new FieldBackedContextStore(5);
  public static final FieldBackedContextStore contextStore6 = new FieldBackedContextStore(6);
  public static final FieldBackedContextStore contextStore7 = new FieldBackedContextStore(7);
  public static final FieldBackedContextStore contextStore8 = new FieldBackedContextStore(8);
  public static final FieldBackedContextStore contextStore9 = new FieldBackedContextStore(9);
  public static final FieldBackedContextStore contextStore10 = new FieldBackedContextStore(10);
  public static final FieldBackedContextStore contextStore11 = new FieldBackedContextStore(11);
  public static final FieldBackedContextStore contextStore12 = new FieldBackedContextStore(12);
  public static final FieldBackedContextStore contextStore13 = new FieldBackedContextStore(13);
  public static final FieldBackedContextStore contextStore14 = new FieldBackedContextStore(14);
  public static final FieldBackedContextStore contextStore15 = new FieldBackedContextStore(15);
  public static final FieldBackedContextStore contextStore16 = new FieldBackedContextStore(16);
  public static final FieldBackedContextStore contextStore17 = new FieldBackedContextStore(17);
  public static final FieldBackedContextStore contextStore18 = new FieldBackedContextStore(18);
  public static final FieldBackedContextStore contextStore19 = new FieldBackedContextStore(19);
  public static final FieldBackedContextStore contextStore20 = new FieldBackedContextStore(20);
  public static final FieldBackedContextStore contextStore21 = new FieldBackedContextStore(21);
  public static final FieldBackedContextStore contextStore22 = new FieldBackedContextStore(22);
  public static final FieldBackedContextStore contextStore23 = new FieldBackedContextStore(23);
  public static final FieldBackedContextStore contextStore24 = new FieldBackedContextStore(24);
  public static final FieldBackedContextStore contextStore25 = new FieldBackedContextStore(25);
  public static final FieldBackedContextStore contextStore26 = new FieldBackedContextStore(26);
  public static final FieldBackedContextStore contextStore27 = new FieldBackedContextStore(27);
  public static final FieldBackedContextStore contextStore28 = new FieldBackedContextStore(28);
  public static final FieldBackedContextStore contextStore29 = new FieldBackedContextStore(29);
  public static final FieldBackedContextStore contextStore30 = new FieldBackedContextStore(30);
  public static final FieldBackedContextStore contextStore31 = new FieldBackedContextStore(31);

  // fall-back to slightly slower lookup for any additional stores
  private static volatile FieldBackedContextStore[] extraStores = new FieldBackedContextStore[8];

  public static FieldBackedContextStore getContextStore(final int storeId) {
    switch (storeId) {
      case 0:
        return contextStore0;
      case 1:
        return contextStore1;
      case 2:
        return contextStore2;
      case 3:
        return contextStore3;
      case 4:
        return contextStore4;
      case 5:
        return contextStore5;
      case 6:
        return contextStore6;
      case 7:
        return contextStore7;
      case 8:
        return contextStore8;
      case 9:
        return contextStore9;
      case 10:
        return contextStore10;
      case 11:
        return contextStore11;
      case 12:
        return contextStore12;
      case 13:
        return contextStore13;
      case 14:
        return contextStore14;
      case 15:
        return contextStore15;
      case 16:
        return contextStore16;
      case 17:
        return contextStore17;
      case 18:
        return contextStore18;
      case 19:
        return contextStore19;
      case 20:
        return contextStore20;
      case 21:
        return contextStore21;
      case 22:
        return contextStore22;
      case 23:
        return contextStore23;
      case 24:
        return contextStore24;
      case 25:
        return contextStore25;
      case 26:
        return contextStore26;
      case 27:
        return contextStore27;
      case 28:
        return contextStore28;
      case 29:
        return contextStore29;
      case 30:
        return contextStore30;
      case 31:
        return contextStore31;
      default:
        return extraStores[storeId - FAST_STORE_ID_LIMIT];
    }
  }

  private static final ConcurrentHashMap<String, FieldBackedContextStore> STORES_BY_NAME =
      new ConcurrentHashMap<>();

  public static int getContextStoreId(final String keyClassName, final String contextClassName) {
    final String storeName = storeName(keyClassName, contextClassName);
    FieldBackedContextStore existingStore = STORES_BY_NAME.get(storeName);
    if (null == existingStore) {
      synchronized (STORES_BY_NAME) {
        // speculatively create the next store in the sequence and attempt to map this name to it;
        // if another thread has mapped this name then the store will be kept for the next mapping
        final int newStoreId = STORES_BY_NAME.size();
        existingStore = STORES_BY_NAME.putIfAbsent(storeName, createStore(newStoreId));
        if (null == existingStore) {
          log.debug(
              "Allocated ContextStore #{} to {} -> {}", newStoreId, keyClassName, contextClassName);
          return newStoreId;
        }
      }
    }
    return existingStore.storeId;
  }

  private static String storeName(final String keyClassName, final String contextClassName) {
    return keyClassName + ';' + contextClassName;
  }

  private static FieldBackedContextStore createStore(final int storeId) {
    if (storeId < FAST_STORE_ID_LIMIT) {
      return getContextStore(storeId);
    }
    final int arrayIndex = storeId - FAST_STORE_ID_LIMIT;
    if (extraStores.length <= arrayIndex) {
      extraStores = Arrays.copyOf(extraStores, extraStores.length << 1);
    }
    // check in case an earlier thread created the store but didn't end up using it
    FieldBackedContextStore store = extraStores[arrayIndex];
    if (null == store) {
      store = new FieldBackedContextStore(storeId);
      extraStores[arrayIndex] = store;
    }
    return store;
  }

  /** Injection helper that immediately delegates to the weak-map for the given context store. */
  public static Object weakGet(final Object key, final int storeId) {
    return getContextStore(storeId).weakStore().get(key);
  }

  /** Injection helper that immediately delegates to the weak-map for the given context store. */
  public static void weakPut(final Object key, final int storeId, final Object context) {
    getContextStore(storeId).weakStore().put(key, context);
  }
}
