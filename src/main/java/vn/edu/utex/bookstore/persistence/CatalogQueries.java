package vn.edu.utex.bookstore.persistence;

import vn.edu.utex.bookstore.product.CatalogRepository;

/** Reused JPQL definitions so offline parser tests validate the actual repository queries. */
public final class CatalogQueries {
  private CatalogQueries() {}
  private static final String SELLABLE = "c.status='AVAILABLE' and c.condition<>'UNCLASSIFIED' and s.status='ACTIVE'";
  private static final String MATCHING_COPIES = "c.shopId=s.id and c.productId=p.id and " + SELLABLE
      + " and (:shop=0 or s.id=:shop) and (:condition='' or c.condition=:condition)"
      + " and c.price>=:minimum and c.price<=:maximum";
  private static final String FILTER = "p.category.active=true and (locate(:q,lower(p.title))>0 or locate(:q,lower(p.author))>0) "
      + "and (:category=0 or p.category.id=:category) "
      + "and exists (select c.id from BookCopy c, Shop s where " + MATCHING_COPIES + ")";
  private static final String PRODUCT_BASE = "select p from Product p join fetch p.category where " + FILTER;
  private static final String FAVORITED = " and exists (select f.id from Favorite f where f.productId=p.id)";
  private static final String MINIMUM_PRICE = "(select min(c.price) from BookCopy c, Shop s where " + MATCHING_COPIES + ")";
  private static final String NEWEST = "p.createdAt desc,p.id desc";
  public static final String PRODUCTS = PRODUCT_BASE + " order by " + NEWEST;
  public static final String COUNT = "select count(p) from Product p where " + FILTER;

  public static String products(CatalogRepository.Filter filter) {
    String order = switch (filter.sort()) {
      case NEWEST -> NEWEST;
      case PRICE_ASC -> MINIMUM_PRICE + " asc," + NEWEST;
      case PRICE_DESC -> MINIMUM_PRICE + " desc," + NEWEST;
      case FAVORITES -> "(select count(f) from Favorite f where f.productId=p.id) desc," + NEWEST;
    };
    return PRODUCT_BASE + (filter.favoritesOnly() ? FAVORITED : "") + " order by " + order;
  }

  public static String count(CatalogRepository.Filter filter) {
    return COUNT + (filter.favoritesOnly() ? FAVORITED : "");
  }
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
