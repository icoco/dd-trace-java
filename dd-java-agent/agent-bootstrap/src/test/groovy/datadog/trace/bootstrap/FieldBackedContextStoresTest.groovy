package datadog.trace.bootstrap

import datadog.trace.test.util.DDSpecification

class FieldBackedContextStoresTest extends DDSpecification {

  def "test FieldBackedContextStore id allocation (#storeId)"() {
    setup:
      int allocatedId = FieldBackedContextStores.getContextStoreId("key${storeId}", "value${storeId}")

    expect:
      storeId == allocatedId
      storeId == FieldBackedContextStores.getContextStore(storeId).storeId

    where:
      storeId << (0..64)
  }
}
