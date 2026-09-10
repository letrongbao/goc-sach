# Cách chạy

Cần Java 21 và PostgreSQL 17. Không cần cài Maven hoặc Tomcat riêng.

1. Chuẩn bị database riêng tên `goc_sach`. Đọc và tự chạy `sql/schema.sql` đúng một lần trên database trống. Schema đã gồm tài khoản, shop và kho sách; không áp dụng vào database bài tập. Ứng dụng chỉ kiểm tra bảng, không tự tạo/sửa.
2. Sao chép `config/local.example.properties` hoặc `config/gmail.example.properties` thành `config/local.properties`.
3. Điền kết nối database, khóa OTP và email theo mẫu. Để lưu ảnh trên Cloudinary, đặt `cloudinary.enabled=true`, điền `cloud-name`, `api-key`, `api-secret` và giữ `cloudinary.asset-folder=WEPR`. Các khóa chỉ nằm trong `config/local.properties`, không đưa lên Git. Không dùng database của repo khác. Khi bật API JWT, dùng PowerShell 7 chạy `scripts/new-jwt-keys.ps1 -OutputDirectory C:/secure/goc-sach-jwt` để tạo khóa ngoài repo, rồi điền các dòng `jwt.*` trong mẫu.
4. Chạy `mvnw.cmd clean verify`.
5. Chạy lệnh dưới đây, sửa đường dẫn cấu hình theo máy.

```powershell
java -Dbookstore.config=C:/BaoLT/goc-sach/config/local.properties -jar target/goc-sach.jar
```

Mở [http://localhost:8080/](http://localhost:8080/).

Tên thuộc tính `bookstore.config` được giữ để tương thích cấu hình hiện có. Không sao chép mật khẩu thật vào Git.

## Xem giao diện không cần database

Chạy `scripts/preview.ps1`, mở [http://127.0.0.1:18080/](http://127.0.0.1:18080/). Nhấn Enter trong cửa sổ chạy để dừng.

Bản xem thử có 13 đầu sách, mỗi đầu sách một bản sẵn bán tại Góc Sách Demo của `reader`. Dữ liệu giả, không lưu lâu dài và không gửi email. Tài khoản: `admin` hoặc `reader`, mật khẩu `demo-password-123`. Các tài khoản này chỉ thuộc mã kiểm thử, không có trong JAR chạy thật.

Thử cửa hàng: đăng nhập `reader` → Cửa hàng → Đăng ký cửa hàng. Đăng xuất, vào `admin` → Duyệt cửa hàng → duyệt hoặc từ chối. Khi được duyệt, `reader` thấy cửa hàng của mình. Admin cấp Manager tại trang cửa hàng; Manager chỉ quản lý Seller của shop được giao.

Thử kho: mở cửa hàng → Kho sách cũ → Nhập một bản sách. Dùng mã đầu sách `1` trong preview, nhập giá/lý do. Bản mới ngừng bán; phân loại rồi chuyển sang Sẵn bán, kiểm tra lịch sử và tải lại trang. Không thể xóa đầu sách có bản trong kho. Ra Tủ sách để kiểm tra giá/số lượng và danh sách bản đang bán. Admin tạm khóa shop thì các bản của shop đó không còn xuất hiện để bán. Bản preview có trang giỏ, checkout COD và lịch sử đơn; dữ liệu sẽ mất khi dừng preview.

Trong database thật, thêm đầu sách chưa làm nó xuất hiện trên Tủ sách: cần có cửa hàng được duyệt, bản sách đã phân loại và mở bán, danh mục đang bật. Giá/tồn cũ không được tự chuyển sang kho.

## Dữ liệu demo

Sau khi build, chạy `scripts/hash-password.ps1`, nhập mật khẩu và điền hash vào `sql/demo.sql`. Kiểm tra SQL rồi tự chạy trên database demo.

Ảnh production bắt buộc tải qua Cloudinary vào folder `WEPR`; ứng dụng chỉ lưu URL HTTPS do Cloudinary trả về. Bản preview/test dùng bộ nhớ tạm, không tạo thư mục ảnh trên máy. Khi triển khai HTTPS, đặt `cookie.secure=true`.

API JWT cần đủ `jwt.private-key-file`, `jwt.public-key-file`, `jwt.issuer` và `jwt.audience`. Thiếu một phần hoặc khóa sai thì ứng dụng báo lỗi khi khởi động; không cấu hình thì API luôn từ chối truy cập. Đăng nhập web, gửi `POST /auth/api-token` kèm `_csrf` hoặc header `X-CSRF-TOKEN`, rồi gọi `GET /api/me` với `Authorization: Bearer <access_token>`. Mặc định 10 phút, cấp lại làm token trước mất hiệu lực; tối đa 10 lần/phút/tài khoản. Không lưu token vào localStorage/URL. Restart sẽ thu hồi tất cả token; chỉ chạy một instance.

Có thể chạy `scripts/check-environment.ps1 -Config C:/BaoLT/goc-sach/config/local.properties` để kiểm tra kết nối và khóa: chỉ đọc metadata các bảng nền M1, đăng nhập SMTP rồi ngắt, không tạo bảng hoặc gửi email. Mã thoát khác 0 nghĩa là còn mục chưa sẵn sàng. Công cụ này chưa kiểm tra đủ schema M2; ứng dụng vẫn phải qua JPA validate khi khởi động. Kiểm tra này không thay cho test cửa hàng, mua hàng hoặc nhận OTP thật.

Để xoay khóa: dừng ứng dụng, tạo cặp mới ở thư mục khác, cập nhật hai đường dẫn rồi chạy lại; toàn bộ token cũ mất hiệu lực. Không xóa/ghi đè khóa cũ trong script.
