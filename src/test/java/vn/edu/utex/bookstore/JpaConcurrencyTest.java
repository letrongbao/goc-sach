package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.*;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.persistence.JpaStore;

class JpaConcurrencyTest {
  @Test
  void lockedReadRefreshesCachedStockButNestedLockedReadKeepsOwnChanges() {
    var factory = mock(EntityManagerFactory.class);
    var em = mock(EntityManager.class);
    var transaction = mock(EntityTransaction.class);
    when(factory.createEntityManager()).thenReturn(em);
    when(em.isOpen()).thenReturn(true);
    when(em.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    var copy = new BookCopy();
    copy.id = 7L;
    copy.status = "AVAILABLE";
    when(em.find(BookCopy.class, 7L)).thenReturn(copy);
    var lock = new AtomicReference<>(LockModeType.NONE);
    when(em.getLockMode(copy)).thenAnswer(call -> lock.get());
    doAnswer(call -> {
      copy.status = "RESERVED";
      lock.set(LockModeType.PESSIMISTIC_WRITE);
      return null;
    }).when(em).refresh(copy, LockModeType.PESSIMISTIC_WRITE);
    var store = new JpaStore(factory);

    store.tx(data -> {
      assertEquals("AVAILABLE", data.copy(7L, false).status);
      assertEquals("RESERVED", data.copy(7L, true).status);
      copy.status = "SOLD";
      store.tx(nested -> {
        assertEquals("SOLD", nested.copy(7L, true).status);
        return null;
      });
      return null;
    });

    verify(em, times(1)).refresh(copy, LockModeType.PESSIMISTIC_WRITE);
    verify(transaction, times(1)).commit();
    verify(factory, times(1)).createEntityManager();
  }

  @Test
  void concurrencyFailuresRollBackAndReturnConflictWithoutDatabaseDetails() {
    RuntimeException[] failures = {
      new OptimisticLockException("private database detail"),
      new PessimisticLockException("private database detail"),
      new LockTimeoutException("private database detail"),
      new org.springframework.dao.ConcurrencyFailureException("private database detail"),
      new RuntimeException(new SQLException("private database detail", "40001")),
      new RuntimeException(new SQLException("private database detail", "40P01")),
      new RuntimeException(new SQLException("private database detail", "55P03"))
    };
    for (RuntimeException failure : failures) {
      var factory = mock(EntityManagerFactory.class);
      var em = mock(EntityManager.class);
      var transaction = mock(EntityTransaction.class);
      when(factory.createEntityManager()).thenReturn(em);
      when(em.isOpen()).thenReturn(true);
      when(em.getTransaction()).thenReturn(transaction);
      when(transaction.isActive()).thenReturn(true);
      var store = new JpaStore(factory);

      Problem problem = assertThrows(Problem.class, () -> store.tx(data -> { throw failure; }));
      assertEquals(409, problem.status);
      assertFalse(problem.getMessage().contains("private database detail"));
      verify(transaction).rollback();
      verify(transaction, never()).commit();
      verify(em).close();
    }
  }
}
