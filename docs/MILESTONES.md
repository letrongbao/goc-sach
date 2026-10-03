# Tiến độ và kiểm tra

Cập nhật 03/10/2026. Chưa hoàn thành toàn bộ đồ án.

| Mốc | Đã làm / kiểm tra | Còn chờ |
|---|---|---|
| M1 — Nền tảng | Spring quản lý service và transaction; Security bảo vệ web/API; JWT có thu hồi; database riêng đã tạo, JPA validate/rollback/lưu qua JVM mới đạt | Nhận OTP thật và nghiệm thu toàn bộ giao diện với dữ liệu thật |
| M2 — Cửa hàng và sách cũ | Đăng ký/duyệt/tạm khóa shop, Seller/Manager; trang shop công khai; kho từng bản và lịch sử; giá/tồn từ kho, schema PostgreSQL đã validate | Còn nghiệm thu toàn bộ luồng xét duyệt và quyền shop qua giao diện thật |
| M3 — Tủ sách | Tìm/lọc, sắp xếp mới nhất/giá/yêu thích; 20 đầu sách/trang; home có kệ giới thiệu/danh mục/shop; cẩm nang chọn sách và chi tiết các bản; adapter Cloudinary | Còn upload thật vào `WEPR`, nghiệm thu query sort mới và đủ bốn bảng top 20 |
| M4 — Người mua | Hồ sơ/địa chỉ, yêu thích, đã xem, giỏ DB/JWT API; luồng dữ liệu và rollback PostgreSQL đạt | Còn nghiệm thu đầy đủ form/quyền và giỏ sau đăng nhập lại qua giao diện thật |
| M5 — Đơn hàng COD | Checkout đa shop, tổng tiền/phí, snapshot, idempotency; hủy/hoàn tất, tranh một cuốn và lưu qua JVM mới đã kiểm tra PostgreSQL | Còn carrier và trả/hoàn tiền |
| M6 — Thanh toán/vận hành | Bản tích hợp 198/198 test; khóa đã cấu hình, tạo/query/hủy PayOS thật qua HTTP đạt; webhook ký và khoản đối chiếu đã test tự động | Chờ URL HTTPS/đăng ký webhook, nhận tiền thật và xác nhận cổng với thầy; còn trả/hoàn tiền, voucher/carrier/doanh thu |
| M7–M8 | Đã có UI tạo/sửa review sau mua, phân trang/thống kê, navigation tài khoản; thiết kế và demo ghi trong UI_UX.md | Còn nghiệm thu DB review, media/moderation, WebSocket nghiệp vụ và các phần vận hành |

Review M1 đã xử lý: API không được bỏ bảo vệ khi thiếu khóa, PEM nhiều dòng, token không lộ session ID, không dùng token sau logout/reset, không cấp token từ request của lượt đăng nhập cũ, transaction lồng nhau, error dispatch không đọc lại file quá lớn.

Test mới: HTTP JWT với session/remember, sai chữ ký/issuer/audience/nbf, hết hạn, sai quyền, thu hồi, CSRF và giới hạn cấp token; cấu hình PEM sai/yếu/không khớp; Spring wiring và rollback lồng nhau; OTP không mở khóa tài khoản; kiểm tra SMTP không gửi thư. Kết quả ở [VERIFICATION.md](VERIFICATION.md).

Mỗi mốc tiếp theo phải có chức năng chạy được, test nghiệp vụ/quyền, review phần dùng chung, cập nhật bảng yêu cầu và demo. Nếu chưa qua phần dữ liệu thật thì ghi rõ chờ nghiệm thu, không coi mock là bằng chứng hoàn thành.

Ngày 03/10/2026: mở rộng UI/UX khám phá sách, guide, filter/sort, review sau mua và dữ liệu preview. Review chéo sửa trường hợp sách ẩn: public reviews trả 404, chủ đánh giá vẫn chỉnh sửa từ lịch sử và quay lại form/đơn. Không chạy migration hay dịch vụ thật trong đợt này. Xem [UI_UX.md](UI_UX.md) và kết quả kiểm tra mới trong VERIFICATION.md.

Review M2 phần cửa hàng: không đổi quyền toàn hệ thống khi duyệt Seller; Manager chỉ cấp/thu hồi Seller trong shop mình; không đổi quyền chủ cửa hàng bằng form thành viên. Duyệt lại cùng yêu cầu không tạo shop thứ hai, từ chối giữ lịch sử, tạm khóa chặn thay đổi thành viên. Quyền được kiểm tra ở service theo dữ liệu hiện hành, không tin vai trò gửi từ giao diện.

Review M2 phần kho: mỗi bản có mã riêng, không chuyển shop/đầu sách bằng form sửa; nhập mới ngừng bán, chưa phân loại không mở bán; sách đã giữ/đã bán không sửa thủ công. Kiểm tra version chống ghi đè, lưu lý do và giá/trạng thái trước-sau, chặn xóa đầu sách đã có bản trong kho. Test giá bất thường, quyền chéo shop, tạm khóa và trang 20–1. Thời gian lịch sử hiển thị giờ Việt Nam. Chưa chứng minh locking/rollback trên PostgreSQL.

Khác thiết kế ban đầu: grant JWT tạm lưu bộ nhớ cho một instance, restart sẽ thu hồi toàn bộ. Trước khi chạy nhiều instance phải thay bằng kho chung; không được bật nhiều instance trên bản này.

Review nối kho: chỉ lấy bản sẵn bán/đã phân loại trong shop hoạt động và danh mục đang bật; một đầu sách không bị lặp vì có nhiều bản. Giá thấp nhất và số lượng cập nhật theo kho; tạm khóa shop loại ngay hàng khỏi danh sách. Form đầu sách không sửa giá/tồn cũ kể cả gửi thêm tham số. Kiểm tra từ khóa, 20–1 đầu sách/bản sách, trang sai, hết hàng và parse bốn JPQL thật đạt; chưa chứng minh truy vấn thực thi trên PostgreSQL.

Ngày 02/10/2026, chủ dự án cho phép tạo database thiếu. Đã tạo riêng `goc_sach` và `goc_sach_test`, chạy schema nền trên hai database trống (26 bảng mỗi database); không chuyển/xóa dữ liệu bài tập `bookstore_03`. Ứng dụng và test mặc định vẫn chỉ validate, không tự chạy DDL. Công cụ nghiệm thu PostgreSQL được gọi riêng; fixture rollback hoặc cleanup theo marker. Lịch sử Git chia theo chức năng; kết quả kiểm thử áp dụng cho bản tích hợp cuối.

Review PayOS: số tiền lấy từ đơn đã chốt, khóa mã cổng trước gọi mạng và giữ nguyên mã khi kiểm tra lại. Không đánh dấu đã trả từ browser; webhook kiểm tra chữ ký/mã link/số tiền và chống mã giao dịch lặp. Lỗi cổng giữ sách chờ đối chiếu; khoản sai tiền hoặc đến sau hủy cần quản trị viên xem xét. Khôi phục lần tạo link chưa tới cổng còn chờ xác minh mã nghiệp vụ NOT_FOUND, có thể cần xử lý thủ công; không suy từ HTTP 404. Chưa tự hoàn tiền hoặc payout. Nghiệm thu bắt buộc gồm đặt hai lần, webhook trùng/sai/sớm/muộn, chéo người mua/shop, quá hạn và restart; kết quả cuối ở [VERIFICATION.md](VERIFICATION.md).
