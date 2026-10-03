# Trải nghiệm Góc Sách

Cập nhật 03/10/2026. Đợt này mở rộng trải nghiệm khám phá và đánh giá sau mua trên nền Thymeleaf/Bootstrap hiện có. Không thay schema, chạy migration hoặc thay luồng thanh toán.

## Vấn đề và quyết định

| Quan sát source trước thay đổi | Đã triển khai |
|---|---|
| Trang chủ chỉ có hero và danh sách sách mới, thiếu lối khám phá | Tìm kiếm tại hero; danh mục đang bật; kệ sách mới/yêu thích; đường dẫn theo ngân sách/tình trạng; cửa hàng đang có hàng; cẩm nang |
| Bộ lọc chiếm nhiều chiều cao, chỉ có thứ tự mới nhất | Sidebar desktop, phần thu gọn trên điện thoại; chip bỏ từng điều kiện; sắp xếp và phân trang giữ các bộ lọc |
| Cùng tựa sách nhưng từng bản vật lý có giá/tình trạng khác nhau | CTA chọn cuốn, lối chuyển nhanh đến các bản đang bán, cẩm nang ba tình trạng và giải thích giá “Từ” |
| Review chỉ có nền nghiệp vụ/API | Xem tổng/điểm trung bình, phân trang 10; người có đơn hoàn tất viết/sửa review; liên kết từ đơn hàng |
| Khó chuyển giữa các trang tài khoản, tiêu đề tab giống nhau | Thanh điều hướng hồ sơ/đơn/yêu thích/đã xem/địa chỉ và tiêu đề trang theo nội dung |

Phong cách: xanh rêu, giấy kem, màu đất; giữ serif hỗ trợ dấu tiếng Việt và sans-serif cho form. Bìa minh họa là typography/CSS của dự án, không đóng vai trò ảnh chứng minh tình trạng sách. Không thêm font/CDN/tracker, không dùng số bán, sao hoặc lượt yêu thích giả để trang trí.

## Hành vi và dữ liệu

- `/product?sort=newest|price_asc|price_desc|favorites`. Giá sort là giá thấp nhất trong các **bản đang bán khớp bộ lọc**. Yêu thích tính từ bảng favorites; bằng điểm dùng createdAt/id để thứ tự ổn định. Giá giảm dần vẫn so sánh mức giá “Từ”, không phải bản đắt nhất.
- Trang chủ giới thiệu tối đa 5 sách mới và 5 sách có lượt yêu thích; shop ACTIVE có hàng và danh mục đang bật. Đây là các kệ giới thiệu, chưa hoàn thành bốn bảng top 20 theo yêu cầu cuối kỳ. Catalog vẫn 20 đầu sách/trang. Bộ sắp xếp yêu thích trong catalog vẫn giữ sách chưa có lượt thích ở phía sau.
- Các thẻ gợi ý là link bộ lọc thật: giá tối đa 50.000 ₫, LIKE_NEW và sort favorites. Không có ưu đãi/voucher giả.
- `/guide` giải thích tình trạng, bốn bước mua, giỏ chưa giữ tồn, checkout nhiều shop và các câu hỏi thường gặp. Accordion dùng `details` native, hoạt động khi tắt JavaScript.
- `/account/reviews/{productId}` yêu cầu đăng nhập và CSRF; service kiểm tra có đơn COMPLETED thuộc người gửi. Một người có một đánh giá mỗi đầu sách, từ 1–5 sao, text trim 50–5.000 ký tự. Lỗi giữ nội dung và nằm cạnh trường. Có sửa review PUBLISHED; review ẩn không công khai/không tự sửa.
- Trang chi tiết lấy count/average của toàn bộ review PUBLISHED, mỗi trang tối đa 10. DTO công khai chỉ có tên hiển thị, text, sao, thời gian; không trả user/order ID. Sách thuộc danh mục tắt không công khai review; người mua vẫn chỉnh sửa từ lịch sử và quay về form, không chuyển sang trang sách 404.
- Menu header đóng khi click ra ngoài hoặc Escape, trả focus về summary. Form/filter/links tiếp tục hoạt động khi JavaScript tắt. Script chỉ hỗ trợ menu, trạng thái bộ lọc ban đầu trên mobile và bộ đếm ký tự.

## Khả năng tiếp cận

Nhãn form rõ ràng, thứ tự heading, skip link, trạng thái hiện tại trong navigation, focus nhìn thấy, respect reduced motion và vùng bấm được tính trong thiết kế. Tham khảo [W3C về kích thước mục tiêu](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum) và [focus appearance](https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance). Đây là cơ sở thiết kế, không phải chứng nhận đáp ứng toàn bộ WCAG.

## Demo và kiểm tra

`scripts/preview.ps1` dùng fixture riêng trong bộ nhớ: 24 sách, 6 danh mục, 3 shop, đủ ba tình trạng và các trạng thái đơn. Không thay DemoFixtures chung của test.

| Tài khoản | Mật khẩu demo | Dùng để xem |
|---|---|---|
| buyer | demo-password-123 | Hồ sơ, địa chỉ, giỏ nhiều shop, yêu thích, đơn hàng và đánh giá |
| reader | demo-password-123 | Cửa hàng #1 |
| seller | demo-password-123 | Cửa hàng #2 và #3 |
| admin | demo-password-123 | Quản trị |

Tài khoản chỉ tồn tại trong preview. `/product/detail?id=24` có 2 review; buyer có thể viết review cho `id=23` từ đơn #1 đã hoàn tất. Restart preview khôi phục dữ liệu. Không dùng tài khoản này cho production.

`scripts/check-ui-preview.cjs` dùng Playwright, cố định origin preview `127.0.0.1:18080`. Kiểm tra tìm kiếm, filter/sort/phân trang, menu bàn phím, FAQ, tạo/sửa review, render và tràn ngang ở 320/390/768/1366 px. Báo cáo và ảnh nằm trong `target/ui-preview/`; kết quả nghiệm thu ghi tại [VERIFICATION.md](VERIFICATION.md). Biến `PLAYWRIGHT_MODULE`/`PLAYWRIGHT_EXECUTABLE` có thể chỉ tới runtime đã cài, không cài dependency browser vào ứng dụng Java.

## Phần tiếp theo

Ưu tiên hoàn thiện bảng xếp hạng bằng dữ liệu mua thật, voucher/vận chuyển và dashboard doanh thu có định nghĩa rõ, sau đó review media/kiểm duyệt và thông báo đơn hàng. Review text và các query mới vẫn cần nghiệm thu PostgreSQL riêng; preview trong bộ nhớ không chứng minh query hoặc khóa đồng thời trên database thật.
