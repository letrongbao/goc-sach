package vn.edu.utex.bookstore.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
  @RequestMapping("/error")
  public ModelAndView error(HttpServletRequest req, HttpServletResponse res) {
    Object code = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = code instanceof Integer value && value >= 400 && value <= 599 ? value : 404;
    res.setStatus(status);
    res.setCharacterEncoding("UTF-8");
    res.setContentType("text/html;charset=UTF-8");
    res.setHeader("Cache-Control", "no-store");
    String message = switch (status) {
      case 400 -> "Yêu cầu không hợp lệ. Hãy kiểm tra lại thông tin.";
      case 403 -> "Bạn không có quyền truy cập hoặc phiên biểu mẫu đã hết hạn.";
      case 404 -> "Trang hoặc dữ liệu bạn tìm không còn tồn tại.";
      case 405 -> "Thao tác này không được hỗ trợ.";
      case 413 -> "Tệp tải lên quá lớn. Chọn ảnh tối đa 5 MB.";
      case 429 -> "Bạn thao tác quá nhanh. Vui lòng thử lại sau.";
      default -> "Dịch vụ tạm thời chưa sẵn sàng. Vui lòng thử lại sau.";
    };
    return new ModelAndView("http-error", java.util.Map.of("statusCode", status, "safeMessage", message));
  }
}
