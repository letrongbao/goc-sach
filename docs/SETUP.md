# Cách chạy

## Demo cuối kỳ cập nhật 28/10/2026

Đọc [FINAL_ACCEPTANCE.md](FINAL_ACCEPTANCE.md). Dùng database mới `goc_sach_final_demo`: chạy schema.sql rồi demo-final.sql đúng một lần trên database trống. Trên máy chuẩn bị database đã nhập xong, không chạy lại. File local `config/final-demo.properties` dùng PostgreSQL port 5434, tắt payOS và SMTP thật. JAR bắt buộc bật Cloudinary với cấu hình hợp lệ; preview bộ nhớ không cần dịch vụ ngoài. Cấu hình thật không nằm trong source zip.

```powershell
java -Dbookstore.config=C:/BaoLT/goc-sach/config/final-demo.properties -jar target/goc-sach.jar --server.address=127.0.0.1 --server.port=18082
```

Mở http://127.0.0.1:18082/ . Accounts: buyer, reader, seller, reviewer, manager, admin; mật khẩu demo-password-123. Preview hiện có 24 đầu sách/6 danh mục/3 shop, thay thông tin 13 sách ở ghi chú cũ bên dưới. Chạy scripts/payment-preview.ps1 để test cổng giả port 18081, không dùng tiền thật.

Database goc_sach cũ chưa áp dụng operations-manual.sql. Không chạy JAR mới vào database cũ trước khi sao lưu, kiểm tra và nâng cấp schema đúng các mốc cần thiết. Không chạy lại schema.sql trên database có bảng.

API: xem [URL và cách cấp Bearer token](API_REFERENCE.md). Các API mới không cần schema mới so với bản 26–27/10. Khởi động lại JAR sau khi build để có controller mới.

## Hướng dẫn nền và cấu hình dịch vụ

Cần Java 21 và PostgreSQL 17. Không cần cài Maven hoặc Tomcat riêng.

Trên Windows, đặt `JAVA_HOME` trỏ tới thư mục JDK 21 đang cài và kiểm tra `java -version` trước khi build. Nếu shim Java trên PATH không chạy hoặc Maven wrapper thoát mà không có log, kiểm tra lại JDK; mã thoát 0 không thay cho dòng BUILD SUCCESS và report test mới.

Chỉ xem UI: chạy `scripts/preview.ps1`, mở `http://127.0.0.1:18080/`. Tài khoản demo `buyer / demo-password-123` có giỏ, địa chỉ, yêu thích và đơn hoàn tất để đánh giá; 24 sách/6 danh mục/3 cửa hàng nằm trong bộ nhớ, restart sẽ reset. Chi tiết tài khoản và kiểm tra trình duyệt ở [UI_UX.md](UI_UX.md).

1. Chuẩn bị database riêng tên `goc_sach`. Với database trống, đọc và tự chạy `sql/schema.sql` đúng một lần. Với database đã có đơn hàng, sao lưu trước rồi xem và tự chạy `sql/payos-manual.sql`; nếu vẫn dùng bản đơn hàng cũ, áp dụng `sql/order-m3-manual.sql` trước. File PayOS chỉ bổ sung bảng/cột và cập nhật trạng thái thanh toán các đơn COD cũ đã hoàn tất hoặc hủy. Không áp dụng các file này vào database bài tập; ứng dụng chỉ kiểm tra bảng, không tự tạo/sửa.
2. Sao chép `config/local.example.properties` hoặc `config/gmail.example.properties` thành `config/local.properties`.
3. Điền kết nối database, khóa OTP và email theo mẫu. Để lưu ảnh trên Cloudinary, đặt `cloudinary.enabled=true`, điền `cloud-name`, `api-key`, `api-secret` và giữ `cloudinary.asset-folder=WEPR`. Các khóa chỉ nằm trong `config/local.properties`, không đưa lên Git. Không dùng database của repo khác. Khi bật API JWT, dùng PowerShell 7 chạy `scripts/new-jwt-keys.ps1 -OutputDirectory C:/secure/goc-sach-jwt` để tạo khóa ngoài repo, rồi điền các dòng `jwt.*` trong mẫu.
4. Chạy `mvnw.cmd clean verify`.
5. Chạy lệnh dưới đây, sửa đường dẫn cấu hình theo máy.

Máy hiện tại đã được chủ dự án cho phép tạo `goc_sach` và `goc_sach_test` ngày 02/10/2026; schema đã chạy trên database trống. **Không chạy lại schema trên hai database này.** Dữ liệu bài tập `bookstore_03` được giữ nguyên. Các bước trên dùng cho môi trường mới hoặc sau khi kiểm tra dữ liệu hiện có.

```powershell
java -Dbookstore.config=C:/BaoLT/goc-sach/config/local.properties -jar target/goc-sach.jar
```

Mở [http://localhost:8080/](http://localhost:8080/).

Tên thuộc tính `bookstore.config` được giữ để tương thích cấu hình hiện có. Không sao chép mật khẩu thật vào Git.

## Thanh toán PayOS

Lấy Client ID, API Key và Checksum Key từ kênh thanh toán đã tạo trên [PayOS](https://my.payos.vn/). Điền trực tiếp vào `config/local.properties`, giữ file này ngoài Git:

```properties
payos.enabled=true
payos.client-id=REPLACE_WITH_PAYOS_CLIENT_ID
payos.api-key=REPLACE_WITH_PAYOS_API_KEY
payos.checksum-key=REPLACE_WITH_PAYOS_CHECKSUM_KEY
payos.base-url=https://dia-chi-ung-dung-cua-ban
```

`payos.base-url` là địa chỉ Góc Sách. Đăng ký webhook trên kênh PayOS trỏ đến `https://dia-chi-ung-dung-cua-ban/payments/payos/webhook`; máy chủ phải truy cập được từ Internet và dùng HTTPS. Return/cancel lần lượt là `/payments/payos/return` và `/payments/payos/cancel`. Localhost được dùng để phát triển return/cancel, nhưng không nhận được webhook từ PayOS. Ứng dụng không tự đăng ký webhook khi khởi động. Thiếu khóa khi đã bật PayOS thì ứng dụng dừng với thông báo cấu hình; khi chưa bật, trang đặt hàng chỉ dùng COD.

Khi thử trên máy, có thể giữ `payos.base-url=http://localhost:8080` và đặt riêng `payos.webhook-url=https://dia-chi-cong-khai/payments/payos/webhook`. Chỉ mở endpoint POST webhook qua proxy giới hạn; không công khai toàn bộ ứng dụng, trang quản trị hoặc công cụ cấu hình. Tunnel tạm hết hiệu lực khi dừng, không phải cấu hình triển khai lâu dài.

PayOS hiện công bố môi trường production, không có sandbox trong tài liệu API. Tạo link chỉ tạo yêu cầu; quét QR/chuyển khoản là tiền thật và do bạn tự thực hiện. Không dùng số dư thật trong test tự động. Xem [tài liệu API](https://payos.vn/docs/api/) và [hướng dẫn kênh thanh toán](https://payos.vn/docs/huong-dan-su-dung/tao-kenh-thanh-toan/).

`payment.PaymentSetupCheck` là công cụ kiểm tra riêng: chỉ đọc cấu hình và metadata database, báo tên khóa/cột thiếu, không hiển thị giá trị khóa hoặc sửa dữ liệu. Chỉ khi truyền `--probe` mới gọi thêm một GET PayOS để đọc mã HTTP/mã lỗi cho mã đơn chưa phát hành; không tạo link hay chuyển tiền. Công cụ này không đăng ký webhook và không thay cho nghiệm thu mua hàng.

Cách thử: chọn chuyển khoản khi đặt hàng → mở thanh toán từng đơn/cửa hàng → chuyển khoản → quay về đơn và kiểm tra trạng thái. Phải kiểm tra cả webhook thật, nút kiểm tra thanh toán, hủy khi chưa trả tiền và dữ liệu sau restart. Trang quay về không tự đánh dấu đã trả tiền từ tham số URL. Người bán chỉ xử lý đơn PayOS khi đã xác nhận đủ tiền. Khoản sai tiền hoặc đến sau khi đã hủy được giữ ở “Cửa hàng đang kiểm tra khoản chuyển”; quản trị viên xem danh sách để đối chiếu. Bản này chưa tự hoàn tiền hoặc chuyển tiền cho cửa hàng.

Giới hạn khôi phục: nếu lần tạo link bị lỗi trước khi PayOS nhận yêu cầu, đơn có thể tiếp tục giữ sách chờ đối chiếu. Adapter chưa có căn cứ chính thức cho mã lỗi “link không tồn tại”, nên không suy từ HTTP 404 để tạo lại hoặc thả sách. Nút kiểm tra có thể khôi phục link đã tồn tại trên cổng; trường hợp chưa có link cần người vận hành kiểm tra và xử lý riêng. Không coi luồng lỗi này đã nghiệm thu.

## Xem giao diện không cần database

Chạy `scripts/preview.ps1`, mở [http://127.0.0.1:18080/](http://127.0.0.1:18080/). Nhấn Enter trong cửa sổ chạy để dừng.

Bản xem thử có 13 đầu sách, mỗi đầu sách một bản sẵn bán tại Góc Sách Demo của `reader`. Dữ liệu giả, không lưu lâu dài và không gửi email. Tài khoản: `admin` hoặc `reader`, mật khẩu `demo-password-123`. Các tài khoản này chỉ thuộc mã kiểm thử, không có trong JAR chạy thật.

Thử cửa hàng: đăng nhập `reader` → Cửa hàng → Đăng ký cửa hàng. Đăng xuất, vào `admin` → Duyệt cửa hàng → duyệt hoặc từ chối. Khi được duyệt, `reader` thấy cửa hàng của mình. Admin cấp Manager tại trang cửa hàng; Manager chỉ quản lý Seller của shop được giao.

Thử kho: mở cửa hàng → Kho sách cũ → Nhập một bản sách, chọn đầu sách rồi nhập giá/lý do. Bản mới ngừng bán; phân loại rồi chuyển sang Sẵn bán, kiểm tra lịch sử và tải lại trang. Không thể xóa đầu sách có bản trong kho. Ra Tủ sách để kiểm tra giá/số lượng và danh sách bản đang bán. Admin tạm khóa shop thì các bản của shop đó không còn xuất hiện để bán. Bản preview có giỏ, đặt hàng COD, lịch sử đơn và màn hình người bán xử lý đơn; dữ liệu sẽ mất khi dừng preview.

Trong database thật, thêm đầu sách chưa làm nó xuất hiện trên Tủ sách: cần có cửa hàng được duyệt, bản sách đã phân loại và mở bán, danh mục đang bật. Giá/tồn cũ không được tự chuyển sang kho.

## Dữ liệu demo

Sau khi build, chạy `scripts/hash-password.ps1`, nhập mật khẩu và điền hash vào `sql/demo.sql`. Kiểm tra SQL rồi tự chạy trên database demo.

Ảnh production bắt buộc tải qua Cloudinary vào folder `WEPR`; ứng dụng chỉ lưu URL HTTPS do Cloudinary trả về. Bản preview/test dùng bộ nhớ tạm, không tạo thư mục ảnh trên máy. Khi triển khai HTTPS, đặt `cookie.secure=true`.

API JWT cần đủ `jwt.private-key-file`, `jwt.public-key-file`, `jwt.issuer` và `jwt.audience`. Thiếu một phần hoặc khóa sai thì ứng dụng báo lỗi khi khởi động; không cấu hình thì API luôn từ chối truy cập. Đăng nhập web, gửi `POST /auth/api-token` kèm `_csrf` hoặc header `X-CSRF-TOKEN`, rồi gọi `GET /api/me` với `Authorization: Bearer <access_token>`. Mặc định 10 phút, cấp lại làm token trước mất hiệu lực; tối đa 10 lần/phút/tài khoản. Không lưu token vào localStorage/URL. Restart sẽ thu hồi tất cả token; chỉ chạy một instance.

Có thể chạy `scripts/check-environment.ps1 -Config C:/BaoLT/goc-sach/config/local.properties` để kiểm tra kết nối và khóa: chỉ đọc metadata các bảng nền M1, đăng nhập SMTP rồi ngắt, không tạo bảng hoặc gửi email. Mã thoát khác 0 nghĩa là còn mục chưa sẵn sàng. Công cụ này chưa kiểm tra đủ schema M2; ứng dụng vẫn phải qua JPA validate khi khởi động. Kiểm tra này không thay cho test cửa hàng, mua hàng hoặc nhận OTP thật.

Để xoay khóa: dừng ứng dụng, tạo cặp mới ở thư mục khác, cập nhật hai đường dẫn rồi chạy lại; toàn bộ token cũ mất hiệu lực. Không xóa/ghi đè khóa cũ trong script.
