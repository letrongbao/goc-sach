package vn.edu.utex.bookstore.review;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public final class ReviewApiController {
  private final ReviewService reviews;
  private final vn.edu.utex.bookstore.config.App app;
  public ReviewApiController(ReviewService reviews,vn.edu.utex.bookstore.config.App app){this.reviews=reviews;this.app=app;}
  @GetMapping public ResponseEntity<?> list(@PathVariable long productId,@RequestParam(required=false) String page){var result=reviews.summary(null,productId,page);return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.count())).header("X-Total-Pages",Integer.toString(result.pages())).body(result.items());}
  @PostMapping(consumes="application/json") public ResponseEntity<?> add(JwtAuthenticationToken auth,@PathVariable long productId,@RequestBody ReviewRequest request){return ResponseEntity.status(201).body(reviews.add(userId(auth),productId,request.rating(),request.body()));}
  @GetMapping("/mine") public Object mine(JwtAuthenticationToken auth,@PathVariable long productId){return reviews.editor(userId(auth),productId);}
  @PutMapping(value="/mine",consumes="application/json") public Object update(JwtAuthenticationToken auth,@PathVariable long productId,@RequestBody ReviewRequest request){return reviews.update(userId(auth),productId,request.rating(),request.body());}
  @PostMapping(consumes="multipart/form-data") public ResponseEntity<?> addMedia(JwtAuthenticationToken auth,@PathVariable long productId,@RequestParam int rating,@RequestParam String body,@RequestParam(defaultValue="image") String mediaType,@RequestPart(required=false) org.springframework.web.multipart.MultipartFile attachment){return ResponseEntity.status(201).body(media(auth,productId,rating,body,mediaType,attachment,false,false));}
  @PutMapping(value="/mine",consumes="multipart/form-data") public Object updateMedia(JwtAuthenticationToken auth,@PathVariable long productId,@RequestParam int rating,@RequestParam String body,@RequestParam(defaultValue="image") String mediaType,@RequestParam(defaultValue="false") boolean removeMedia,@RequestPart(required=false) org.springframework.web.multipart.MultipartFile attachment){return media(auth,productId,rating,body,mediaType,attachment,true,removeMedia);}
  private Object media(JwtAuthenticationToken auth,long productId,int rating,String body,String mediaType,org.springframework.web.multipart.MultipartFile file,boolean update,boolean remove){
    long user=userId(auth);var editor=reviews.editor(user,productId);
    ReviewService.validateInput(rating,body);
    if(!editor.editable())throw new vn.edu.utex.bookstore.common.Problem(409,"Đánh giá hiện không thể chỉnh sửa.");
    if(!update&&editor.existingReview()!=null)throw new vn.edu.utex.bookstore.common.Problem(409,"Bạn đã đánh giá sách này.");
    if(update&&editor.existingReview()==null)throw vn.edu.utex.bookstore.common.Problem.missing();
    String url=update&&!remove?null:"",type=url;
    if(file!=null&&!file.isEmpty()){
      if(!"image".equals(mediaType)&&!"video".equals(mediaType))throw vn.edu.utex.bookstore.common.Problem.invalid("Loại tệp không hợp lệ.");
      try(var input=file.getInputStream()){
        var asset="video".equals(mediaType)?app.images.storeVideo(input,file.getSize()):app.images.storeAsset(input,file.getSize());
        url=asset.secureUrl();type=asset.resourceType();
      }catch(java.io.IOException e){throw new vn.edu.utex.bookstore.common.Problem(502,"Không tải được tệp lên.");}
    }
    return update?reviews.update(user,productId,rating,body,url,type):reviews.add(user,productId,rating,body,url,type);
  }
  public record ReviewRequest(int rating,String body){}
  private static long userId(JwtAuthenticationToken auth){Object v=auth.getToken().getClaim("user_id");if(!(v instanceof Number n)||n.longValue()<=0)throw new IllegalStateException("JWT user_id không hợp lệ.");return n.longValue();}
}
