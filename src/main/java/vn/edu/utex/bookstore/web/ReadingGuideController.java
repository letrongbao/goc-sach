package vn.edu.utex.bookstore.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class ReadingGuideController {
  @GetMapping("/guide")
  public ModelAndView guide(HttpServletRequest request, HttpServletResponse response) {
    request.setAttribute("view", "reading-guide");
    return Web.result(request, response);
  }
}
