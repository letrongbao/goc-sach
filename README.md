# Góc Sách — đồ án cuối kỳ Lập trình Web

Repository này dành riêng cho **đồ án cuối kỳ**, định hướng đề tài 15: quản lý chuỗi cửa hàng sách cũ bằng Spring Boot + JSP/JSTL. Chưa xác nhận giảng viên duyệt đề tài/nhóm. Bài 01–03 nộp tại [repository bài tập riêng](https://github.com/letrongbao/goc-sach-exercises), không dùng repo này thay thế.

**Trạng thái: nền tảng kế thừa, chưa hoàn thành đồ án.** Source bắt đầu từ commit `8a47d12` của repo bài tập, giữ nguyên lịch sử thật. Chưa chuyển Spring Boot; chưa có chuỗi cửa hàng, giỏ hàng, đơn hàng, thanh toán, JWT, WebSocket hoặc Cloudinary. Xem [nguồn gốc và phạm vi](docs/FINAL_PROJECT.md). Các tài liệu bài tập còn lại là hồ sơ nền tảng, không phải minh chứng đạt rubric cuối kỳ.

Website bán sách bằng **Java 21 / Servlet 6 / JSP-JSTL / PostgreSQL / JPA EntityManager**, đóng gói WAR. Đây là project phát triển nối tiếp Bài 01 (JDBC), Bài 02 (JPA) và Bài 03 (OTP + sản phẩm), chưa chuyển Spring Boot.

## Bắt đầu

1. Đọc [SETUP](docs/SETUP.md). Chủ project **tự chuẩn bị schema**; không có DDL/migration tự chạy.
2. Sao chép cấu hình mẫu trong config/ thành file riêng, điền PostgreSQL, thư mục ảnh và otp.secret.
3. Dùng Mailpit để thử email local hoặc cấu hình [Gmail SMTP](docs/OTP.md).
4. Build bằng `./mvnw.cmd clean verify` (Windows) hoặc `./mvnw clean verify`.
5. Deploy `target/bookstore.war` lên Tomcat 10.1 và mở /bookstore/.

Không có tài khoản admin mặc định trên database thật. Tạo hash và nhập dữ liệu demo thủ công theo SETUP. Không đưa mật khẩu/secret thật vào Git.

## Xem giao diện không cần PostgreSQL

Chạy `./scripts/preview.ps1`, mở http://127.0.0.1:18080/bookstore/ .
Bản preview có banner cảnh báo, 13 sách giả trong RAM; không gửi email và không lưu dữ liệu. Tài khoản thử **admin / demo-password-123**, **reader / demo-password-123** chỉ tồn tại trong test fixtures, không được đóng gói vào WAR. Dừng bằng Ctrl+C.

## Chức năng

- Hai cách đăng nhập: Session hoặc Cookie token ngẫu nhiên; phân quyền ADMIN/USER.
- CRUD/tìm kiếm Category, ảnh qua URL hoặc upload hợp lệ.
- Đăng ký/kích hoạt OTP; đặt lại mật khẩu OTP và thu hồi phiên cũ.
- CRUD sách; Category 1:N Product; chặn xóa danh mục đang có sách.
- Trang chủ 10 sách mới; /product phân trang 6; chi tiết từ cả trang chủ/danh sách.

## Hồ sơ nền tảng

- [Đối chiếu yêu cầu](docs/REQUIREMENTS.md)
- [Kết quả và giới hạn xác minh](docs/VERIFICATION.md)
- [Chuyển JDBC → JPA](docs/JPA.md)
- [Thiết kế và cách học code](docs/ARCHITECTURE.md)
- [Lộ trình cuối kỳ](docs/ROADMAP.md)
- [Hướng dẫn nộp ba bài](docs/SUBMISSIONS.md)

| Bài | Mốc source | Trạng thái |
|---|---|---|
| 01 — JDBC | [submission-01-r1](https://github.com/letrongbao/goc-sach-exercises/tree/submission-01-r1) | Snapshot cũ; còn cần backport sửa form/upload và nghiệm thu DB |
| 02 — JPA | [submission-02-r1](https://github.com/letrongbao/goc-sach-exercises/tree/submission-02-r1) | Snapshot cũ; còn đối chiếu/bổ sung count, phân trang Category |
| 03 — OTP/Product | [submission-03-r1](https://github.com/letrongbao/goc-sach-exercises/tree/submission-03-r1) | Có bản sửa, 61 tests pass; chưa nghiệm thu DB/email thật |

Các tag trong bảng thuộc repository bài tập, không phải các bản nộp của repo đồ án. Repo đồ án giữ Private; cấp quyền cho thành viên/người chấm trước khi chia sẻ. Không coi nền tảng này là đồ án đã nghiệm thu.
