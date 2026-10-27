package vn.edu.utex.bookstore;
import java.time.Clock;
import java.util.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.operations.OperationsService;
import vn.edu.utex.bookstore.inventory.InventoryService;

/** Explicitly synthetic demonstration data; never run against a user's existing database. */
public final class FinalDemoFixtures {
  public static MemoryStore create(Clock clock) {
    var store=ExperiencePreviewFixtures.create(clock);
    var admin=Identity.of(store.findLogin("admin"));var reader=Identity.of(store.findLogin("reader"));
    var buyer=Identity.of(store.findLogin("buyer"));var ops=new OperationsService(store,clock);
    var orders=new OrderService(store,clock);var buyers=new BuyerService(store,clock);var inventory=new InventoryService(store,clock);
    long shop=store.shopsFor(reader.id(),false).getFirst().id;
    ops.saveCarrier(admin,null,"Giao tiết kiệm","20000",true);ops.saveCarrier(admin,null,"Giao nhanh","45000",true);
    ops.createVoucher(reader,shop,"GOCSACH10","10000","50000",30,clock.instant().plusSeconds(30*86400));
    var manager=new User();manager.username="manager";manager.email="manager@example.test";manager.passwordHash=new Passwords().hash("demo-password-123");manager.role="USER";manager.active=true;manager.createdAt=clock.instant();store.saveUser(manager);
    var membership=new vn.edu.utex.bookstore.shop.ShopMembership();membership.shopId=shop;membership.userId=manager.id;membership.role="MANAGER";membership.active=true;membership.updatedAt=clock.instant();store.saveMembership(membership);
    // Preserve the sample cart while making 12 separate completed purchases of book 1.
    var cart=store.cart(buyer.id());var cartCopies=store.cartItems(cart.id).stream().map(i->i.copyId).toList();
    cartCopies.forEach(id->buyers.removeFromCart(buyer.id(),id));
    long address=buyers.addresses(buyer.id()).getFirst().id;
    for(int i=0;i<12;i++) {
      long copy=inventory.receive(reader,shop,1,"GOOD","Dữ liệu minh họa","65000","Nhập dữ liệu demo");
      inventory.update(reader,shop,copy,0,"GOOD","Dữ liệu minh họa","65000","AVAILABLE","Mở bán demo");buyers.addToCart(buyer.id(),copy);
      long id=orders.checkout(buyer.id(),address,"final-demo-completed-"+i).orders().getFirst().id;
      orders.advance(reader,id,"CONFIRMED");orders.advance(reader,id,"SHIPPING");orders.advance(reader,id,"COMPLETED");
    }
    var completed=store.ordersFor(buyer.id()).stream().filter(o->o.idempotencyKey.equals("final-demo-completed-0")).findFirst().orElseThrow();
    ops.requestReturn(buyer.id(),completed.id,"Bản sách minh họa bị thiếu trang, xin gửi lại để kiểm tra.");
    cartCopies.forEach(id->buyers.addToCart(buyer.id(),id));
    return store;
  }
  public static void main(String[] args) throws Exception {
    if(args.length!=1)throw new IllegalArgumentException("Output JSON path required");
    MemoryStore store=create(Clock.systemUTC());Map<String,List<Object>> entities=new TreeMap<>();
    for(var field:MemoryStore.class.getFields()) {
      Object value=field.get(store);Collection<?> rows=value instanceof Map<?,?> map?map.values():value instanceof List<?> list?list:List.of();
      for(Object row:rows)if(row!=null&&row.getClass().getPackageName().startsWith("vn.edu.utex.bookstore"))entities.computeIfAbsent(row.getClass().getName(),k->new ArrayList<>()).add(row);
    }
    var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(args[0]).toFile(),entities);
    System.out.println("Exported synthetic demo entities: "+entities.size());
  }
}
