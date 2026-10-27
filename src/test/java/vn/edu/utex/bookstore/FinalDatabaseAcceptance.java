package vn.edu.utex.bookstore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.Persistence;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.operations.OperationsService;
import vn.edu.utex.bookstore.persistence.JpaStore;
import vn.edu.utex.bookstore.payment.*;

/** Manual database acceptance; every test write rolls back. Payment provider is a fake. */
public class FinalDatabaseAcceptance {
  public static void main(String[] args) {
    if(args.length!=1||!"--rollback".equals(args[0]))throw new IllegalArgumentException("--rollback required");
    var settings=Settings.load();String url="jdbc:postgresql://127.0.0.1:5434/goc_sach_final_demo";
    try(var factory=Persistence.createEntityManagerFactory("bookstore",Map.of("jakarta.persistence.jdbc.url",url,
        "jakarta.persistence.jdbc.user",settings.require("db.user"),"jakarta.persistence.jdbc.password",settings.require("db.password"),"hibernate.hbm2ddl.auto","validate"))) {
      var manager=new JpaTransactionManager(factory);var store=new JpaStore(factory,manager);var transaction=new TransactionTemplate(manager);
      var before=store.tx(d->d.ordersFor(d.findLogin("buyer").id).size());
      transaction.execute(status->{status.setRollbackOnly();Clock clock=Clock.systemUTC();
        var buyer=store.tx(d->Identity.of(d.findLogin("buyer")));var admin=store.tx(d->Identity.of(d.findLogin("admin")));
        var orders=new OrderService(store,clock);var ops=new OperationsService(store,clock);var buyers=new BuyerService(store,clock);
        var catalog=new vn.edu.utex.bookstore.product.CatalogService(store);
        for(String sort:List.of("newest","bestselling","rating","favorites"))require(catalog.ranking(sort,"2").items().size()==10,"ranking "+sort);
        require(!catalog.shelf("popular",20).isEmpty(),"over-ten-sales catalog");
        var cart=buyers.cart(buyer.id());cart.forEach(c->buyers.removeFromCart(buyer.id(),c.copyId()));
        long copy=store.tx(d->d.catalogOffers(1,0,20).getFirst().id());buyers.addToCart(buyer.id(),copy);
        long shop=store.tx(d->d.copy(copy,false).shopId),address=buyers.addresses(buyer.id()).getFirst().id;
        var carrier=ops.saveCarrier(admin,null,"Rollback carrier","11000",true);
        var voucher=ops.createVoucher(admin,shop,"TEST"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"10000","0",1,clock.instant().plusSeconds(3600));
        var quote=orders.quote(buyer.id(),carrier.id,voucher.code);
        var o=orders.checkout(buyer.id(),address,"acceptance-"+UUID.randomUUID(),quote.fingerprint(),"COD",carrier.id,voucher.code).orders().getFirst();
        require(o.total.compareTo(o.subtotal.add(new BigDecimal("1000")))==0,"discount + carrier total");
        orders.advance(admin,o.id,"CONFIRMED");orders.advance(admin,o.id,"SHIPPING");orders.advance(admin,o.id,"COMPLETED");
        ops.requestReturn(buyer.id(),o.id,"Sách demo bị hỏng, yêu cầu trả lại.");ops.resolveReturn(admin,o.id,"APPROVED","Duyệt yêu cầu demo","");
        ops.resolveReturn(admin,o.id,"REFUNDED","Đã nhận và đối soát giả lập","FAKE-REFUND-ACCEPTANCE");
        require("UNAVAILABLE".equals(store.tx(d->d.copy(copy,false).status)),"return quarantines stock");
        long onlineCopy=store.tx(d->d.catalogOffers(2,0,20).getFirst().id());buyers.addToCart(buyer.id(),onlineCopy);
        var fake=new Gateway();var payments=new PaymentService(store,orders,fake,clock);
        var online=payments.checkout(buyer.id(),address,"payos-"+UUID.randomUUID(),orders.quote(buyer.id()).fingerprint(),"PAYOS").orders().getFirst();
        payments.start(buyer.id(),online.id);fake.paid=true;payments.webhook("signed-fake");payments.webhook("signed-fake");
        require("PAID".equals(store.tx(d->d.order(online.id,false).paymentStatus)),"fake paid webhook");
        long cancelCopy=store.tx(d->d.catalogOffers(3,0,20).getFirst().id());buyers.addToCart(buyer.id(),cancelCopy);
        var cancelled=payments.checkout(buyer.id(),address,"cancel-"+UUID.randomUUID(),orders.quote(buyer.id()).fingerprint(),"PAYOS").orders().getFirst();
        fake.paid=false;payments.start(buyer.id(),cancelled.id);payments.cancel(buyer.id(),cancelled.id);
        require("AVAILABLE".equals(store.tx(d->d.copy(cancelCopy,false).status)),"fake cancel releases stock");
        System.out.println("PASS: PostgreSQL mappings, four rankings, >10 sales, voucher/carrier snapshots, return/refund, signed fake paid replay, fake cancellation.");return null;});
      require(before.equals(store.tx(d->d.ordersFor(d.findLogin("buyer").id).size())),"rollback order count");
      System.out.println("PASS: acceptance writes rolled back; demo fixture remains intact; no real payment or refund.");
    }
  }
  private static void require(boolean ok,String label){if(!ok)throw new IllegalStateException(label);}
  private static class Gateway implements PaymentGateway {
    long code,amount;boolean paid,cancelled;String id;
    public boolean enabled(){return true;}
    public Link create(long code,long amount,String description,Instant expires){this.code=code;this.amount=amount;id="fake-"+code;cancelled=false;return new Link(code,amount,id,"https://pay.payos.vn/fake-acceptance","PENDING");}
    public Snapshot query(long code){return new Snapshot(code,id,amount,paid?amount:0,paid?"PAID":cancelled?"CANCELLED":"PENDING","https://pay.payos.vn/fake-acceptance");}
    public Snapshot cancel(long code,String reason){cancelled=true;return query(code);}
    public Receipt verify(String raw){if(!"signed-fake".equals(raw))throw new IllegalArgumentException();return new Receipt(code,id,amount,"fake-receipt-"+code,"VND",paid);}
  }
}
