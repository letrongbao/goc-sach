# Tiến độ và kiểm tra

Cập nhật 04/09/2026. Chưa hoàn thành toàn bộ đồ án.

| Mốc | Đã làm / kiểm tra | Còn chờ |
|---|---|---|
| M1 — Nền tảng | Spring quản lý service và transaction; Security bảo vệ web/API; JWT có thu hồi; tách khóa tài khoản với kích hoạt; sửa lỗi trang upload; test hồi quy | Database riêng và schema được duyệt; kiểm tra JPA/lưu sau restart, nhận OTP thật, UI thực tế sau chuyển Security |
| M2 — Cửa hàng và sách cũ | Đăng ký/duyệt/tạm khóa shop, Seller/Manager; kho từng bản và lịch sử; giá/tồn công khai lấy từ kho sẵn bán; test nghiệp vụ/HTTP, Chrome desktop/mobile | Chưa chuyển tồn cũ, ảnh riêng/metadata mở rộng; chưa chạy SQL hoặc kiểm thử PostgreSQL |
| M3 — Tủ sách | Tìm tên/tác giả, danh sách 20 đầu sách/trang; chi tiết các bản đang bán theo cửa hàng | Cloudinary, bộ lọc mở rộng, trang shop và các danh sách top 20 chưa đầy đủ |
| M4–M8 | Giữ phạm vi trong ROADMAP | Chưa triển khai; không đánh dấu đạt bằng test nền tảng |

Review M1 đã xử lý: API không được bỏ bảo vệ khi thiếu khóa, PEM nhiều dòng, token không lộ session ID, không dùng token sau logout/reset, không cấp token từ request của lượt đăng nhập cũ, transaction lồng nhau, error dispatch không đọc lại file quá lớn.

Test mới: HTTP JWT với session/remember, sai chữ ký/issuer/audience/nbf, hết hạn, sai quyền, thu hồi, CSRF và giới hạn cấp token; cấu hình PEM sai/yếu/không khớp; Spring wiring và rollback lồng nhau; OTP không mở khóa tài khoản; kiểm tra SMTP không gửi thư. Kết quả ở [VERIFICATION.md](VERIFICATION.md).

Mỗi mốc tiếp theo phải có chức năng chạy được, test nghiệp vụ/quyền, review phần dùng chung, cập nhật bảng yêu cầu và demo. Nếu chưa qua phần dữ liệu thật thì ghi rõ chờ nghiệm thu, không coi mock là bằng chứng hoàn thành.

Review M2 phần cửa hàng: không đổi quyền toàn hệ thống khi duyệt Seller; Manager chỉ cấp/thu hồi Seller trong shop mình; không đổi quyền chủ cửa hàng bằng form thành viên. Duyệt lại cùng yêu cầu không tạo shop thứ hai, từ chối giữ lịch sử, tạm khóa chặn thay đổi thành viên. Quyền được kiểm tra ở service theo dữ liệu hiện hành, không tin vai trò gửi từ giao diện.

Review M2 phần kho: mỗi bản có mã riêng, không chuyển shop/đầu sách bằng form sửa; nhập mới ngừng bán, chưa phân loại không mở bán; sách đã giữ/đã bán không sửa thủ công. Kiểm tra version chống ghi đè, lưu lý do và giá/trạng thái trước-sau, chặn xóa đầu sách đã có bản trong kho. Test giá bất thường, quyền chéo shop, tạm khóa và trang 20–1. Thời gian lịch sử hiển thị giờ Việt Nam. Chưa chứng minh locking/rollback trên PostgreSQL.

Khác thiết kế ban đầu: grant JWT tạm lưu bộ nhớ cho một instance, restart sẽ thu hồi toàn bộ. Trước khi chạy nhiều instance phải thay bằng kho chung; không được bật nhiều instance trên bản này.

Review nối kho: chỉ lấy bản sẵn bán/đã phân loại trong shop hoạt động và danh mục đang bật; một đầu sách không bị lặp vì có nhiều bản. Giá thấp nhất và số lượng cập nhật theo kho; tạm khóa shop loại ngay hàng khỏi danh sách. Form đầu sách không sửa giá/tồn cũ kể cả gửi thêm tham số. Kiểm tra từ khóa, 20–1 đầu sách/bản sách, trang sai, hết hàng và parse bốn JPQL thật đạt; chưa chứng minh truy vấn thực thi trên PostgreSQL.

SQL chỉ chuẩn bị để xem xét, chưa thực thi. Bước tạo database/schema bị chặn vì chỉ dẫn không chạy SQL tạo bảng vẫn còn hiệu lực; không dùng cách khác để vượt chặn. Cần chủ dự án tự chạy hoặc xác nhận gỡ giới hạn riêng cho database `goc_sach`. Repo bài tập và database `bookstore_03` được giữ nguyên. Lịch sử Git chia theo nền tảng, cửa hàng, kho, tủ sách và tài liệu; các commit trong đợt này là một bộ thay đổi liên quan, kết quả kiểm thử áp dụng cho bản tích hợp cuối.
