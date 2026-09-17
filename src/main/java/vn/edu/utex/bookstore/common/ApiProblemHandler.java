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
  @ExceptionHandler(Problem.class)
  public ResponseEntity<Map<String, String>> problem(Problem problem) {
    return ResponseEntity.status(problem.status).cacheControl(CacheControl.noStore())
        .body(Map.of("error", problem.getMessage()));
  }
}
