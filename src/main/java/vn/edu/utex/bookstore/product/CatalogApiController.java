package vn.edu.utex.bookstore.product;

import org.springframework.web.bind.annotation.*;
import vn.edu.utex.bookstore.config.App;

@RestController
@RequestMapping("/api")
public final class CatalogApiController {
  private final App app;
  public CatalogApiController(App app) { this.app = app; }
  @GetMapping("/categories") public Object categories() { return app.categories.list("").stream().filter(c -> c.active).toList(); }
  @GetMapping("/products") public Object products(@RequestParam(required=false) String page,
      @RequestParam(required=false) String q, @RequestParam(required=false) String category,
      @RequestParam(required=false) String minPrice, @RequestParam(required=false) String maxPrice,
      @RequestParam(required=false) String condition, @RequestParam(required=false) String shop,
      @RequestParam(required=false) String sort) {
    return app.catalog.page(page,q,category,minPrice,maxPrice,condition,shop,sort);
  }
  @GetMapping("/products/{id}") public Object detail(@PathVariable long id,@RequestParam(required=false) String page) { return app.catalog.detail(id,page); }
  @GetMapping("/rankings/{sort}") public Object ranking(@PathVariable String sort,@RequestParam(required=false) String page) { return app.catalog.ranking(sort,page); }
  @GetMapping("/shops") public Object shops() { return app.catalog.shops(); }
  @GetMapping("/shops/{slug}") public Object shop(@PathVariable String slug,@RequestParam(required=false) String page) { return app.catalog.shop(slug,page); }
}
