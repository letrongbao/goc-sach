package vn.edu.utex.bookstore.web;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.common.Problem;

@Controller
public class RankingController {
  private final App app;
  public RankingController(App app) { this.app = app; }
  @GetMapping("/rankings/{sort}")
  public ModelAndView ranking(@PathVariable String sort, @RequestParam(required=false) String page,
      HttpServletRequest req, HttpServletResponse res) {
    try {
      req.setAttribute("ranking", app.catalog.ranking(sort, page));
      req.setAttribute("sort", sort); req.setAttribute("view", "rankings");
    } catch (Problem e) { res.setStatus(e.status); req.setAttribute("error", e.getMessage()); req.setAttribute("view", "error"); }
    return Web.result(req, res);
  }
}
