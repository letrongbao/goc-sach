package vn.edu.utex.bookstore.review;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.FormProblem;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.web.Web;

@Controller
public final class ReviewWebController {
  private final ReviewService reviews;

  public ReviewWebController(ReviewService reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/account/reviews/{productId}")
  public ModelAndView editor(@PathVariable long productId, HttpServletRequest request,
      HttpServletResponse response) {
    try {
      render(request, reviews.editor(identity(request).id(), productId), false);
    } catch (Problem problem) {
      error(request, response, problem);
      request.setAttribute("view", "error");
    }
    return Web.result(request, response);
  }

  @PostMapping("/account/reviews/{productId}")
  public ModelAndView save(@PathVariable long productId, HttpServletRequest request,
      HttpServletResponse response) throws java.io.IOException {
    try {
      long userId = identity(request).id();
      var editor = reviews.editor(userId, productId);
      int rating = rating(request.getParameter("rating"));
      boolean update = "update".equals(request.getParameter("mode"));
      if (update) reviews.update(userId, productId, rating, request.getParameter("body"));
      else reviews.add(userId, productId, rating, request.getParameter("body"));
      request.getSession().setAttribute("notice", update
          ? "Đã cập nhật đánh giá của bạn. Cảm ơn bạn đã chia sẻ!"
          : "Đánh giá đã được đăng. Cảm ơn bạn đã giúp bạn đọc khác chọn sách!");
      Web.redirect(request, response, editor.accessiblePublicProduct()
          ? "/product/detail?id=" + productId + "#reviews" : "/account/reviews/" + productId);
      return null;
    } catch (Problem problem) {
      error(request, response, problem);
      try {
        render(request, reviews.editor(identity(request).id(), productId), true);
      } catch (Problem unavailable) {
        request.setAttribute("view", "error");
      }
      return Web.result(request, response);
    }
  }

  private static void render(HttpServletRequest request, ReviewService.Editor editor, boolean submitted) {
    request.setAttribute("reviewEditor", editor);
    request.setAttribute("reviewRating", submitted ? Objects.toString(request.getParameter("rating"), "")
        : editor.existingReview() == null ? "" : Integer.toString(editor.existingReview().rating()));
    request.setAttribute("reviewBody", submitted ? Objects.toString(request.getParameter("body"), "")
        : editor.existingReview() == null ? "" : editor.existingReview().body());
    request.setAttribute("view", "review-form");
  }

  private static void error(HttpServletRequest request, HttpServletResponse response, Problem problem) {
    response.setStatus(problem.status);
    request.setAttribute("error", problem.getMessage());
    if (problem instanceof FormProblem form)
      request.setAttribute("fieldErrors", Map.of(form.field, form.getMessage()));
  }

  private static Identity identity(HttpServletRequest request) {
    Identity identity = (Identity) request.getAttribute("identity");
    if (identity == null) throw new Problem(401, "Vui lòng đăng nhập để đánh giá sách.");
    return identity;
  }

  private static int rating(String raw) {
    if (raw != null && raw.matches("[1-5]")) return Integer.parseInt(raw);
    throw new FormProblem("rating", "Hãy chọn điểm đánh giá từ 1 đến 5 sao.");
  }
}
