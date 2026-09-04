# Đối chiếu yêu cầu đồ án

Đọc trực tiếp [quy định cuối khóa UTEx](https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010) ngày 04/09/2026. Trang ghi sửa lần cuối 19/08/2026. Bảng này tách yêu cầu của thầy khỏi hiện trạng và quyết định triển khai.

“Có nền tảng” không có nghĩa đã nghiệm thu DB/email thật. Test tự động hiện tại không thay cho kiểm thử các chức năng cuối kỳ chưa viết. Kết quả mới nhất ở [VERIFICATION.md](VERIFICATION.md).

## Stack và chức năng

| Mã | Yêu cầu từ đề/quy định chung | Hiện trạng source | Mốc / minh chứng phải có |
|---|---|---|---|
| T01 | Spring Boot + Thymeleaf + Bootstrap | Có nền tảng JAR và giao diện | M1/M8: chạy JAR, form và mobile thực tế |
| T02 | JPA + SQLServer/MySQL/PostgreSQL | Đã chọn PostgreSQL; JPA mapping XML | M1/M5: lưu thật, rollback và chống bán trùng |
| T03 | Spring Security khi dùng Spring Boot | Web/API chain riêng; quyền và CSRF do Spring xử lý, test HTTP đạt | M1: còn nghiệm thu DB/email thật và màn hình quản lý khóa user ở mốc vận hành |
| T04 | JWT | RS256, issuer/audience/hạn, grant thu hồi theo phiên, active/blocked/auth_version; `/api/me` đã test HTTP | M4: nối giỏ/yêu thích vào UI; grant hiện chỉ dùng cho một instance |
| T05 | WebSocket | Chưa có | M7: hai trình duyệt nhận cập nhật đơn đúng quyền |
| T06 | Cloudinary (trang ghi Cloudairy) | Chưa có; ảnh lưu local | M3/M7: ảnh sách, logo, ảnh/video bình luận lưu thật |
| F01 | Đăng ký OTP email, login/logout, quên mật khẩu OTP, mật khẩu băm | Có nghiệp vụ và test nền | M1: Gmail thật, OTP sai/hết hạn/dùng lại, thu hồi đăng nhập |
| F02 | Tìm kiếm, lọc sản phẩm | Có tìm tên/tác giả, phân trang giữ từ khóa | M3: còn lọc danh mục/giá/tình trạng/shop và test DB |
| F03 | Guest: trang chủ, nội dung bán trên 10 sản phẩm của các shop, thứ tự giảm dần | Tối đa 20 đầu sách mới đang có hàng; chưa có số liệu bán | M0 xác nhận cách hiểu; M3/M6 kiểm tra số liệu bán thật |
| F04 | User: danh mục, top 20 mới/bán chạy/đánh giá/yêu thích, phân trang hoặc lazy loading, chi tiết | Có danh sách 20 đầu sách/trang và chi tiết các bản sẵn bán; chưa đủ bốn top 20 | M3/M7: bốn danh sách top 20 có thứ tự ổn định, dữ liệu đủ và rỗng |
| F05 | Profile và nhiều địa chỉ nhận hàng | Chưa có | M4: địa chỉ mặc định, sửa/xóa, quyền sở hữu |
| F06 | Giỏ hàng lưu database | Chưa có | M4: tải lại/đăng nhập lại vẫn còn; kiểm tra giá và tồn |
| F07 | Thanh toán COD, VNPAY hoặc MOMO | Chưa có | Chọn COD + VNPAY sandbox ở M5/M6 |
| F08 | Lịch sử mua hàng theo trạng thái, hủy, trả hàng/hoàn tiền | Chưa có | M5/M6: chuyển trạng thái đúng role, hoàn trả không lặp |
| F09 | Thích, đã xem, đánh giá sản phẩm đã mua | Chưa có | M4/M7: không trùng, chỉ người mua đã nhận được đánh giá |
| F10 | Bình luận sản phẩm đã mua: text tối thiểu 50 ký tự, ảnh/video | Chưa có | M7: biên 49/50 ký tự, file sai và vượt giới hạn bị chặn |
| F11 | Chọn mã giảm giá | Chưa có | M6: hết hạn, hết lượt, sai shop, đua lượt cuối |
| F12 | Seller có quyền User; đăng ký shop, trang shop, sản phẩm, đơn, khuyến mãi, doanh thu riêng | Có đăng ký/duyệt, quản lý shop/kho và hàng sẵn bán trên catalog; còn trang shop công khai/đơn/khuyến mãi/doanh thu; chưa test DB thật | M2/M3/M5/M6: shop chờ duyệt không bán; shop A không đọc/sửa shop B |
| F13 | Manager quản lý user, sản phẩm từng shop, danh mục, chiết khấu, khuyến mãi, nhà vận chuyển | Có cấp/thu hồi Seller và sửa bản sách theo shop; phần vận hành còn thiếu | M2/M6: phạm vi shop được giao; giới hạn cấu hình toàn hệ thống cần xác nhận |
| F14 | Admin quản lý user, sản phẩm/shop, danh mục, chiết khấu, khuyến mãi, nhà vận chuyển/phí | CRUD danh mục/sách; duyệt/tạm khóa shop, phân quyền và ghi nhật ký; còn thiếu phần vận hành | M2/M6: đủ chức năng, khóa thay vì xóa lịch sử, có nhật ký |
| F15 | Shipper quản lý/thống kê đơn được giao | Chưa có; thầy ghi phần thêm | Sau M8 nếu còn thời gian; không làm trước phần bắt buộc |
| D01 | Đặc thù đề tài 14: chuỗi cửa hàng sách cũ | Có cửa hàng, từng bản sách, tình trạng/giá/lịch sử; catalog lấy từ kho; còn chuyển tồn cũ và test DB | M2/M5: mỗi bản sách thuộc một shop, tình trạng/giá/tồn riêng |

## Báo cáo và tiêu chí chấm

| Nội dung chính thức | Điểm trên thang 10 | Kết quả cần chuẩn bị |
|---|---:|---|
| Quản lý/phân công và quá trình commit GitHub | 1 | Bảng việc, người phụ trách, commit và review thật |
| Mô hình 3 tầng | 0,5 | Controller → service → repository; không xử lý SQL trong giao diện |
| Đầy đủ logic, validation, security theo role | 6 | Test và demo cho F01–F14; phạm vi điều chỉnh có xác nhận của thầy |
| Giao diện đẹp, dễ dùng, responsive | 1 | Một giao diện Bootstrap thống nhất; desktop/mobile, lỗi/rỗng/loading |
| Chức năng sáng tạo, ví dụ thanh toán, JWT, voucher, WebSocket | 1 | Tích hợp chạy thật và có kịch bản lỗi; không chỉ thêm dependency |
| Báo cáo và trả lời câu hỏi | 0,5 | Phân tích, thiết kế, kết quả, giới hạn và phần từng người thực hiện |

Thầy yêu cầu khảo sát hiện trạng, phân tích thiết kế, phân công theo chức năng/vai trò, dùng một template Bootstrap và viết báo cáo phần mỗi thành viên. Giao diện hiện tại dùng Bootstrap, nhưng cần xác nhận cách đáp ứng yêu cầu “01 template” và ghi nguồn/giấy phép tài nguyên; không tự thay toàn bộ giao diện chỉ để đổi template.

Nhóm tối đa 3 người. Chưa có ngày nộp/bảo vệ cụ thể trong trang quy định. Link Word khảo sát chưa đọc được do Chrome chặn tải; không khẳng định đã đối chiếu file này. Những điểm mơ hồ cần hỏi được liệt kê trong [ROADMAP.md](ROADMAP.md).
