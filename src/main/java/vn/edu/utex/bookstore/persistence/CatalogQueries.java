package vn.edu.utex.bookstore.persistence;

/** Reused JPQL definitions so offline parser tests validate the actual repository queries. */
public final class CatalogQueries {
  private CatalogQueries() {}
  private static final String SELLABLE = "c.status='AVAILABLE' and c.condition<>'UNCLASSIFIED' and s.status='ACTIVE'";
  private static final String FILTER = "p.category.active=true and (locate(:q,lower(p.title))>0 or locate(:q,lower(p.author))>0) "
      + "and (:category=0 or p.category.id=:category) "
      + "and exists (select c.id from BookCopy c, Shop s where c.shopId=s.id and c.productId=p.id and " + SELLABLE
      + " and (:shop=0 or s.id=:shop) and (:condition='' or c.condition=:condition)"
      + " and c.price>=:minimum and c.price<=:maximum)";
  public static final String PRODUCTS = "select p from Product p join fetch p.category where " + FILTER + " order by p.createdAt desc,p.id desc";
  public static final String COUNT = "select count(p) from Product p where " + FILTER;
  public static final String STOCK = "select c.productId,count(c),min(c.price) from BookCopy c, Shop s, Product p "
      + "where c.shopId=s.id and c.productId=p.id and p.category.active=true and c.productId in :ids and " + SELLABLE
      + " and (:shop=0 or s.id=:shop) and (:condition='' or c.condition=:condition)"
      + " and c.price>=:minimum and c.price<=:maximum group by c.productId";
  public static final String OFFERS = "select c.id,s.id,s.name,c.code,c.condition,c.defects,c.price from BookCopy c, Shop s, Product p "
      + "where c.shopId=s.id and c.productId=p.id and p.category.active=true and c.productId=:product and " + SELLABLE + " order by c.price,c.id";
  public static final String SHOPS = "select distinct s.id,s.name,s.address from Shop s, BookCopy c, Product p "
      + "where c.shopId=s.id and c.productId=p.id and p.category.active=true and " + SELLABLE + " order by s.name,s.id";
  public static final String SHOP = "select s.id,s.name,s.address from Shop s where s.id=:shop and s.status='ACTIVE'";
}
