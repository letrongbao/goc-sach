package vn.edu.utex.bookstore;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.product.CatalogService;
import vn.edu.utex.bookstore.common.Problem;
class RankingTest {
  @Test void fourRankingsAreCappedPaginatedStableAndExcludeHiddenStock(){
    var store=ExperiencePreviewFixtures.create(java.time.Clock.systemUTC());var catalog=new CatalogService(store);
    for(String sort:java.util.List.of("newest","bestselling","rating","favorites")){
      var first=catalog.ranking(sort,"1"); var second=catalog.ranking(sort,"2");
      assertEquals(20,first.total());assertEquals(10,first.items().size());assertEquals(10,second.items().size());
      var ids=new java.util.HashSet<Long>();first.items().forEach(p->assertTrue(ids.add(p.id())));second.items().forEach(p->assertTrue(ids.add(p.id())));
      assertEquals(first.items().stream().map(p->p.id()).toList(),catalog.ranking(sort,"1").items().stream().map(p->p.id()).toList());
      assertThrows(Problem.class,()->catalog.ranking(sort,"3"));
    }
    assertEquals(23,catalog.ranking("rating","1").items().getFirst().id());
    assertThrows(Problem.class,()->catalog.ranking("unknown","1"));
    store.shops.values().forEach(s->s.status="SUSPENDED");assertEquals(0,catalog.ranking("newest","1").total());
  }
}
