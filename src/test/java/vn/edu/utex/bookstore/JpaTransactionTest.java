package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.persistence.JpaStore;

class JpaTransactionTest {
  @Test
  void commitsAndCloses() {
    var factory = mock(EntityManagerFactory.class);
    var em = mock(EntityManager.class);
    var tx = mock(EntityTransaction.class);
    when(factory.createEntityManager()).thenReturn(em);
    when(em.isOpen()).thenReturn(true);
    when(em.getTransaction()).thenReturn(tx);
    var store = new JpaStore(factory);
    assertEquals("ok", store.tx(d -> "ok"));
    verify(tx).begin();
    verify(tx).commit();
    verify(em).close();
    verify(tx, never()).rollback();
    store.close();
    verify(factory).close();
  }

  @Test
  void rollsBackAndCloses() {
    var factory = mock(EntityManagerFactory.class);
    var em = mock(EntityManager.class);
    var tx = mock(EntityTransaction.class);
    when(factory.createEntityManager()).thenReturn(em);
    when(em.isOpen()).thenReturn(true);
    when(em.getTransaction()).thenReturn(tx);
    when(tx.isActive()).thenReturn(true);
    assertThrows(
        IllegalStateException.class,
        () ->
            new JpaStore(factory)
                .tx(
                    d -> {
                      throw new IllegalArgumentException();
                    }));
    verify(tx).rollback();
    verify(em).close();
    verify(tx, never()).commit();
  }

  @Test
  void nestedCallsUseOneTransactionAndRollbackTogether() {
    var factory = mock(EntityManagerFactory.class);
    var em = mock(EntityManager.class);
    var tx = mock(EntityTransaction.class);
    when(factory.createEntityManager()).thenReturn(em);
    when(em.isOpen()).thenReturn(true);
    when(em.getTransaction()).thenReturn(tx);
    when(tx.isActive()).thenReturn(true);
    var store = new JpaStore(factory);
    assertThrows(IllegalStateException.class, () -> store.tx(outer -> {
      store.tx(inner -> { inner.saveUser(new vn.edu.utex.bookstore.auth.User()); return null; });
      throw new IllegalArgumentException("rollback outer");
    }));
    verify(factory, times(1)).createEntityManager();
    verify(tx, times(1)).begin();
    verify(tx, times(1)).rollback();
    verify(tx, never()).commit();
    verify(em).close();
  }

  @Test
  void caughtInnerFailureStillPreventsOuterCommit() {
    var factory = mock(EntityManagerFactory.class);
    var em = mock(EntityManager.class);
    var tx = mock(EntityTransaction.class);
    when(factory.createEntityManager()).thenReturn(em);
    when(em.isOpen()).thenReturn(true);
    when(em.getTransaction()).thenReturn(tx);
    when(tx.isActive()).thenReturn(true);
    var rollbackOnly = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(call -> { rollbackOnly.set(true); return null; }).when(tx).setRollbackOnly();
    when(tx.getRollbackOnly()).thenAnswer(call -> rollbackOnly.get());
    doAnswer(call -> {
      if (rollbackOnly.get()) throw new RollbackException("rollback-only");
      return null;
    }).when(tx).commit();
    var store = new JpaStore(factory);
    assertThrows(IllegalStateException.class, () -> store.tx(outer -> {
      try { store.tx(inner -> { throw new IllegalArgumentException("inner"); }); }
      catch (IllegalStateException expected) { }
      return null;
    }));
    verify(tx).setRollbackOnly();
    // JPA commit on rollback-only must throw and roll back inside the provider.
    verify(tx).commit();
    verify(em).close();
  }

  @Test
  void mappingsBuildWithoutDatabaseOrDdl() {
    var registry =
        new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .build();
    try {
      var metadata =
          new org.hibernate.boot.MetadataSources(registry)
              .addResource("META-INF/orm.xml")
              .addResource("META-INF/shop-orm.xml")
              .addResource("META-INF/inventory-orm.xml")
              .buildMetadata();
      assertEquals(11, metadata.getEntityBindings().size());
    } finally {
      org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
