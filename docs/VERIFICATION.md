# Kết quả kiểm tra

Ngày kiểm tra: 04/09/2026.

- `mvnw.cmd -B -ntp clean verify`: đạt **121 kiểm thử**, không lỗi hoặc bỏ qua; build sạch thành công lúc 16:51 ngày 04/09/2026.
- JWT được thử qua HTTP thật: cấp token bằng session/remember + CSRF, gọi API, sai chữ ký/issuer/audience/nbf, hết hạn, đổi quyền, logout/reset/khóa user, cấp lại và giới hạn tần suất. Khóa PEM sai/thiếu/yếu/không khớp bị từ chối.
- Spring quản lý quyền/CSRF, service và transaction; test wiring một factory, commit/rollback lồng nhau và lỗi upload 413 đạt. Bộ lọc không còn chạy trước firewall; URL bất thường bị chặn bằng redirect hoặc 400.
- Đã kiểm tra đăng nhập, OTP, quyền quản trị, danh mục, sách, phân trang, upload và dữ liệu sai. Catalog mới lấy tối đa 20 đầu sách có hàng ở trang chủ, danh sách 20 đầu sách/trang; 21 đầu sách chia 20–1. Danh sách quản trị vẫn 6/trang, không phải danh sách công khai.
- File `target/goc-sach.jar` chứa Thymeleaf và tài nguyên cần thiết; không chứa JSP/JSTL/Jasper, fixture test, local.properties, PEM hoặc hai class cấu hình Security cũ đã bỏ. Quét giá trị secret đã cấu hình trong các file Git/có thể thêm Git không thấy trùng; local.properties được Git bỏ qua.
- Cửa hàng: 11 test nghiệp vụ và 2 test HTTP kiểm tra xét duyệt/từ chối, quyền theo shop, cấp/thu hồi thành viên, tạm khóa/mở lại, yêu cầu trùng, CSRF và escape dữ liệu.
- Kho: thêm 12 test nghiệp vụ và 1 test HTTP cho nhập/sửa, lịch sử, giá/tình trạng sai, version cũ, shop khác, khóa/thu hồi quyền, phân trang 20–1, chặn xóa đầu sách có bản trong kho. Giá dạng số mũ cực lớn bị từ chối trước khi chuyển đổi; giờ lịch sử được test theo Việt Nam. Mapping 11 entity xây dựng được mà không mở database hoặc tạo bảng.
- Chrome trên bản preview mới: đăng nhập reader → gửi yêu cầu → đăng nhập admin → duyệt → mở trang quản lý và thành viên. Đã xem bố cục desktop, 390 px và 320 px, gồm form thành viên. Kết thúc đã dừng preview và trả lại kích thước Chrome. Chưa kiểm tra hết mọi màn hình bằng mắt.
- Chrome lượt kho: kho rỗng → nhập một bản → phân loại/mở bán → tải lại còn trạng thái và lịch sử. Đã xem form desktop/390px/320px bằng dữ liệu giả trong bộ nhớ. Sau đó sửa định dạng giờ (được unit/HTTP test lại, chưa xem lại bằng Chrome); đã đóng tab và dừng preview.
- Nối kho vào tủ sách: thêm 11 test nghiệp vụ, 1 test parse bốn JPQL thật và 1 test HTTP. Kiểm tra giá thấp nhất/số bản giữa nhiều shop, shop tạm khóa, danh mục ẩn, hết hàng, bản ngừng bán/giữ/đã bán/chưa phân loại, từ khóa và phân trang bản sách 20–1. Form đầu sách bỏ giá/tồn; gửi thêm hai tham số này cũng không sửa kho. Test JPQL không mở kết nối hoặc thực thi SQL.
- Chrome lượt catalog: 13 đầu sách → tìm “Lập trình” còn 1 → mở chi tiết đúng bản giá 85.000 đồng, cửa hàng và tình trạng. Đã xem desktop, 390px/320px và tìm kiếm rỗng ở 320px; không thấy tràn ngang trong các vùng đã xem. Đã trả kích thước Chrome, đóng tab thử và dừng preview. Đây là dữ liệu giả.

Kiểm tra cấu hình riêng đã sao chép: SMTP kết nối/xác thực thành công, không gửi thư; cặp khóa JWT hợp lệ và ở ngoài repo. PostgreSQL trên cổng 5434 hiện có `bookstore_03`, chưa có `goc_sach`; kiểm tra kết nối đồ án trả SQLState 3D000. Database và thư mục ảnh bài tập không bị dùng để ghi dữ liệu đồ án.

Chạy thử JAR thật lúc 16:03: đọc được persistence unit, nhưng dừng khởi động vì PostgreSQL báo database `goc_sach` không tồn tại. Không chạy DDL, không để server thử tiếp tục chạy. Đây là bước xác nhận lỗi môi trường, không phải ứng dụng đã chạy thành công trên DB thật.

Giới hạn: 121 test dùng dữ liệu trong bộ nhớ, mock, parser Hibernate và SMTP loopback. MemoryStore tuần tự hóa và không mô phỏng rollback, không chứng minh khóa/rollback trên PostgreSQL. Chưa kiểm tra JPA trên PostgreSQL thật, độ bền sau restart, nhận OTP thật hoặc giao diện đầy đủ. SQL nền, `users.blocked`, `m2-shops.sql`, `m2-inventory.sql` chỉ được chuẩn bị, chưa chạy vì giới hạn tạo bảng vẫn còn hiệu lực. M1 còn chờ nghiệm thu; M2 đã nối kho vào catalog nhưng chưa chuyển tồn cũ/metadata mở rộng; M3 mới có tìm kiếm cơ bản; M4–M8 chưa triển khai. Repo bài tập vẫn sạch, không thay đổi trong mốc này. Kiểm thử áp dụng cho bản tích hợp cuối của chuỗi commit, không phải từng commit trung gian.

Trước khi chốt đồ án: cấu hình riêng theo [SETUP.md](SETUP.md), kiểm tra lưu dữ liệu sau khởi động lại, nhận OTP qua email thật và hoàn thành các phần còn thiếu trong [ROADMAP.md](ROADMAP.md).
