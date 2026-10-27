package vn.edu.utex.bookstore.common;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps expected API failures as small JSON responses instead of HTML error pages. */
@RestControllerAdvice(annotations = RestController.class)
public final class ApiProblemHandler {
  @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
      org.springframework.web.bind.MissingServletRequestParameterException.class,
      org.springframework.web.multipart.support.MissingServletRequestPartException.class})
  public ResponseEntity<Map<String,String>> invalid(Exception failure) {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error","invalid_request"));
  }
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String,String>> tooLarge(Exception failure) {
    return ResponseEntity.status(413).body(Map.of("error","Tệp tối đa 5 MB."));
  }
  @ExceptionHandler(Problem.class)
  public ResponseEntity<Map<String, String>> problem(Problem problem) {
    return ResponseEntity.status(problem.status).cacheControl(CacheControl.noStore())
        .body(Map.of("error", problem.getMessage()));
  }
}
