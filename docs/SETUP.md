# Chạy thủ công khi không dùng Docker

Cách chung cho nhóm là [Docker Compose](DOCKER_QUICKSTART.md): nhận `config/team.properties`, chạy `docker compose up -d --build --wait`. Không cần làm các bước thủ công bên dưới nếu dùng Docker.

## Xem giao diện nhanh

Cài Java21, đặt JAVA_HOME, chạy `scripts/preview.ps1`; mở http://127.0.0.1:18080. Tài khoản buyer/admin dùng `demo-password-123`. Dữ liệu bộ nhớ:24 đầu sách,6 danh mục,3 shop; restart sẽ reset. Preview không gửi email, không có JWT cấu hình sẵn. `scripts/payment-preview.ps1` dùng cổng giả tại18081, không chuyển tiền thật.

## JAR và PostgreSQL

1. Cài Java21 và PostgreSQL17. Dùng Maven wrapper, không cần cài Maven/Tomcat riêng.
2. Tạo database mới `goc_sach_final_demo`. Trên database trống, chạy `sql/schema.sql` rồi `sql/demo-final.sql` đúng một lần. Không chạy lại trên database có dữ liệu.
3. Sao chép `config/local.example.properties` thành file riêng ngoài Git. Điền db.url/user/password, otp.secret ngẫu nhiên; bật Cloudinary và điền key hợp lệ. JAR production yêu cầu adapter Cloudinary. Đặt cookie.secure=false nếu chạy HTTP local.
4. SMTP cần server riêng; có thể dùng Mailpit hoặc STARTTLS/Gmail theo [OTP](OTP.md). Để payos.enabled=false nếu không muốn kết nối cổng thật.
5. Nếu dùng API JWT, tạo khóa bằng PowerShell7.2+: `scripts/new-jwt-keys.ps1 -OutputDirectory C:/goc-sach-keys`; điền private/public PEM, issuer và audience vào file cấu hình.
6. Chạy `mvnw.cmd -B -ntp verify`, xác nhận BUILD SUCCESS, rồi chạy:

```powershell
java -Dbookstore.config=C:/duong-dan/config/local.properties -jar target/goc-sach.jar
```

Mở http://localhost:8080. App đọc `.properties` qua bookstore.config/BOOKSTORE_CONFIG, không tự đọc `.env`. Không copy đường dẫn JWT hoặc mật khẩu DB của máy khác mà không điều chỉnh.

## Database cũ và dịch vụ thật

Hibernate chỉ validate. Nâng cấp DB cũ phải sao lưu, đối chiếu schema và áp dụng đúng migration trong `sql/`; không chạy schema.sql để nâng cấp. Lượt đóng gói Compose chỉ tạo/seed volume mới, không migrate database goc_sach hiện có.

Bật payOS cần client-id, api-key, checksum-key và base-url của ứng dụng. Webhook `/payments/payos/webhook` cần địa chỉ HTTPS mà cổng truy cập được; localhost chỉ phục vụ web/return trên máy. App không tự tạo tunnel hoặc đăng ký webhook. Cấu hình này là cổng thật; fake gateway chỉ nằm trong mã test/preview.

Chi tiết quyền và cấp token: [API](API_REFERENCE.md). Giới hạn đã kiểm chứng: [VERIFICATION](VERIFICATION.md).
