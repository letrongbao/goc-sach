package vn.edu.utex.bookstore.inventory;

import java.util.List;

public interface InventoryRepository {
  BookCopy copy(long id, boolean lock);
  BookCopy saveCopy(BookCopy copy);
  List<BookCopy> copies(long shopId, int offset, int limit);
  long copyCount(long shopId);
  long copyCountForProduct(long productId);
  void recordMovement(InventoryMovement movement);
  List<InventoryMovement> movements(long copyId);
}
