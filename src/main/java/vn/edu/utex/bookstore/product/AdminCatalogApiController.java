package vn.edu.utex.bookstore.product;

import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;
import vn.edu.utex.bookstore.common.Problem;

@RestController
@RequestMapping("/api/admin")
public final class AdminCatalogApiController {
  private final App app;
  public AdminCatalogApiController(App app){this.app=app;}
  @GetMapping("/categories") public Object categories(JwtAuthenticationToken a,@RequestParam(required=false) String q){ApiActor.admin(app,a);return app.categories.list(q);}
  @GetMapping("/categories/{id}") public Object category(JwtAuthenticationToken a,@PathVariable long id){ApiActor.admin(app,a);return app.categories.get(id);}
  @PostMapping("/categories") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public void category(JwtAuthenticationToken a,@RequestBody CategoryRequest r){saveCategory(a,null,r);}
  @PutMapping("/categories/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void category(JwtAuthenticationToken a,@PathVariable long id,@RequestBody CategoryRequest r){saveCategory(a,id,r);}
  private void saveCategory(JwtAuthenticationToken a,Long id,CategoryRequest r){ApiActor.admin(app,a);app.categories.save(id,r.name(),app.images.validateReference(r.image()),r.active());}
  @DeleteMapping("/categories/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteCategory(JwtAuthenticationToken a,@PathVariable long id){ApiActor.admin(app,a);app.categories.delete(id);}
  @GetMapping("/products") public Object products(JwtAuthenticationToken a,@RequestParam(required=false) String page){ApiActor.admin(app,a);return app.products.page(page);}
  @GetMapping("/products/{id}") public Object product(JwtAuthenticationToken a,@PathVariable long id){ApiActor.admin(app,a);return app.products.get(id);}
  @PostMapping("/products") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public void product(JwtAuthenticationToken a,@RequestBody ProductRequest r){saveProduct(a,null,r);}
  @PutMapping("/products/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void product(JwtAuthenticationToken a,@PathVariable long id,@RequestBody ProductRequest r){saveProduct(a,id,r);}
  private void saveProduct(JwtAuthenticationToken a,Long id,ProductRequest r){ApiActor.admin(app,a);app.products.saveDetails(id,r.categoryId(),r.title(),r.author(),r.description(),app.images.validateReference(r.image()),r.isbn(),r.publisher(),r.publishedYear(),r.language());}
  @DeleteMapping("/products/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteProduct(JwtAuthenticationToken a,@PathVariable long id){ApiActor.admin(app,a);app.products.delete(id);}
  @PostMapping(value="/media/images",consumes="multipart/form-data")
  public Object image(JwtAuthenticationToken a,@RequestPart MultipartFile file){
    ApiActor.admin(app,a);
    if(file.isEmpty())throw Problem.invalid("Vui lòng chọn ảnh.");
    try(var input=file.getInputStream()){return app.images.storeAsset(input,file.getSize());}
    catch(IOException e){throw new Problem(502,"Không tải được ảnh lên.");}
  }
  public record CategoryRequest(String name,String image,boolean active){}
  public record ProductRequest(long categoryId,String title,String author,String description,String image,String isbn,String publisher,Integer publishedYear,String language){}
}
