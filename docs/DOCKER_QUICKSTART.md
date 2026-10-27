# Chạy Góc Sách bằng Docker

## Dành cho hai thành viên trong nhóm

1. Cài và mở **Docker Desktop**, dùng Linux containers. Không cần cài Java, Maven hoặc PostgreSQL riêng.
2. Clone repository hoặc tải ZIP và giải nén. Nhận riêng **một file `team.properties` từ Bảo**, đặt vào thư mục `config` của dự án. Không đổi tên thành `.env`.
3. Mở terminal tại thư mục có `compose.yaml`, chạy:

```sh
docker compose up -d --build --wait
```

Lần đầu cần Internet để tải image và thư viện nên có thể mất vài phút. Khi lệnh hoàn tất:

| Nội dung | Địa chỉ |
|---|---|
| Góc Sách | http://localhost:8080 |
| Hộp thư OTP thử nghiệm | http://localhost:8025 |

Đăng nhập `buyer`, `reader`, `seller`, `manager`, `reviewer` hoặc `admin`, mật khẩu chung `demo-password-123`. Buyer dùng mua hàng; reader là Seller shop 1; manager quản lý thành viên shop 1; admin quản trị toàn hệ thống.

Đăng ký hoặc quên mật khẩu với email thử nghiệm, rồi mở Mailpit để xem OTP. Email không gửi đến hộp thư Internet. Không cần tài khoản Gmail để demo.

## Dữ liệu và cấu hình đã làm sẵn

- PostgreSQL 17 chạy trong Compose, không mở cổng database ra máy host và không kết nối database trên máy Bảo.
- Volume mới tự chạy `schema.sql`, `demo-final.sql`, điều chỉnh ngày demo theo ngày cài và đánh dấu khởi tạo hoàn tất. Có 6 tài khoản, 24 đầu sách, 3 shop, 84 bản sách và 18 đơn minh họa.
- App chờ database khởi tạo xong; JPA chỉ validate, không tự migrate. Lỗi seed sẽ ngăn app khởi động thay vì dùng dữ liệu nửa chừng.
- Dữ liệu và cặp khóa JWT lưu trong volume riêng. Restart hoặc `down` thông thường không xóa dữ liệu và không chạy lại seed.
- Khóa JWT và OTP secret tự sinh trên mỗi máy. Không cần nhận `private.pem`, mật khẩu DB hoặc sửa đường dẫn Windows.
- `team.properties` chứa Cloudinary và key payOS, kèm cấu hình SMTP thật dạng chú thích để dùng khi cần. Cloudinary được bật; chỉ khi upload mới gọi dịch vụ thật. Mặc định payOS tắt, thanh toán demo dùng COD.
- Khi bật `payos.enabled=true`, đó là kết nối payOS thật, không phải fake gateway. `payos.base-url` phải đúng địa chỉ app; webhook Internet cần HTTPS riêng. Compose không tạo tunnel và không đăng ký webhook.
- API Bearer dùng được: đăng nhập web, cấp token qua `/auth/api-token` có CSRF. Xem [hướng dẫn API](API_REFERENCE.md). Restart app thu hồi token đang dùng, cần cấp lại.

## Các lệnh thường dùng

```sh
# Xem trạng thái
docker compose ps

# Xem lỗi khởi động
docker compose logs --tail=80 app db

# Tạm dừng, giữ dữ liệu
docker compose stop

# Chạy lại
docker compose up -d --wait

# Sau khi pull code mới
docker compose up -d --build --wait

# Gỡ container/network, vẫn giữ dữ liệu
docker compose down
```

Không thêm `-v` vào lệnh down nếu muốn giữ dữ liệu. Đừng chạy lại schema/seed bằng tay. Cập nhật schema về sau cần sao lưu và migration riêng; rebuild image không nâng cấp schema cũ.

Nếu cổng 8080 hoặc 8025 bận, trên PowerShell:

```powershell
$env:APP_PORT='18084'
$env:MAIL_PORT='18085'
docker compose up -d --build --wait
```

Khi đổi cổng và muốn bật payOS, sửa cả `payos.base-url` trong file riêng rồi recreate app. Thay nội dung key cũng dùng `docker compose up -d --force-recreate app` để app đọc lại cấu hình.

Nếu báo thiếu `config/team.properties`, kiểm tra file đã nằm đúng thư mục, không phải `team.properties.txt`. File mẫu `team.example.properties` chỉ chỉ ra tên cấu hình, chưa có key để chạy Cloudinary. Nếu DB unhealthy, xem log DB và giữ volume để kiểm tra, không xóa dữ liệu theo phản xạ.

## File nào gửi riêng

Bảo chỉ gửi **`config/team.properties`** qua kênh riêng cho hai bạn. Source, SQL, Dockerfile và tài liệu đã có trên Git. File này bị Git ignore và nằm ngoài Docker build context; được mount lúc chạy, không nhúng vào image. Không gửi `local.properties`, `final-demo.properties`, file kiểm thử, backup DB hoặc khóa JWT máy Bảo.

Compose này dành cho học nhóm trên máy cá nhân, chỉ mở web tại localhost. Mật khẩu DB và tài khoản seed là dữ liệu demo cố định, không dùng nguyên bộ này để triển khai công khai.

## Kiểm tra sau khi chạy

Mở catalog, đăng nhập buyer, thử đặt COD, vào reader xử lý đơn và kiểm tra email OTP trong Mailpit. Có thể chạy kiểm thử HTTP tự động khi máy có Python 3:

```sh
python scripts/check-compose.py
```

Script tạo tài khoản/đơn thử trên database Compose đang chạy; không gọi payOS hoặc upload Cloudinary. Dùng trên môi trường demo, không trỏ vào hệ thống thật.

Thiết kế tham khảo [Docker Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/), [khởi tạo PostgreSQL image](https://hub.docker.com/_/postgres) và [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
