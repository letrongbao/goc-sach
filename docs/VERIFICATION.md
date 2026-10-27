# Kết quả kiểm tra

## Docker Compose ngày 28/10/2026

- Full Maven verify chạy lại hoàn tất 03:07:07 UTC+7: **230 tests, 0 failures/errors/skipped**. Log local `target/compose-maven-verify.log`.
- Build image Linux từ source, không lấy JAR hoặc thư viện cài trên host. Project kiểm thử riêng, PostgreSQL 17.11 và Mailpit; không kết nối container `utex-bookstore-postgres-17`.
- Volume trống tự tạo schema, seed và điều chỉnh ngày demo. App chỉ chạy sau khi dấu khởi tạo hoàn tất được ghi; PostgreSQL không publish cổng ra host.
- `scripts/check-compose.py` đạt trên PostgreSQL: catalog, đăng ký/OTP qua SMTP Mailpit, kích hoạt, JWT, quyền admin/shop, hồ sơ/địa chỉ, version kho cũ, checkout COD 80.000 + 20.000 − 10.000 = 90.000, gửi lặp, giao hàng, yêu cầu trả và ghi nhận hoàn tiền giả có xác nhận.
- Sau `down` rồi `up --wait`, số user/đơn/bản sách và hash khóa JWT/OTP giữ nguyên; đăng nhập lại đọc đúng hồ sơ, đơn hoàn tất và yêu cầu REFUNDED. Không seed lại.
- Dựng lại từ thư mục chỉ có file trong Git cộng duy nhất `config/team.properties`, không có target/JAR/config máy cũ; lệnh Compose một bước và toàn bộ acceptance script tiếp tục đạt trên volume mới thứ hai.
- Quét image config và 18 blob/layer (giải nén gzip khi cần), không thấy giá trị bí mật lấy từ cấu hình local. `config/team.properties` được Git ignore, build context chỉ cho phép pom, source main và entrypoint.
- Không upload Cloudinary, gọi payOS hay gửi email Internet trong kiểm thử Compose. Mailpit là hộp thư thử; trạng thái hoàn tiền là dữ liệu giả. Đã tạo mới/seed database trong volume Compose riêng, **không migrate database cũ**. Kiểm tra trên Docker Desktop Linux containers x86_64; chưa chạy trực tiếp trên máy ARM/macOS.

Cập nhật: 28/10/2026. Full `verify` đạt **230 test**, không failure/error/skipped, hoàn tất 02:39:35 UTC+7. Log local `target/api-final-verified.log`.

## API và phân công ngày 28/10/2026

Bổ sung 10 test HTTP trong ExpandedApiWebTest: public catalog, Bearer/CSRF separation, JSON sai, địa chỉ khác chủ, kho khác shop/version cũ, admin/khóa tài khoản thu hồi JWT, checkout voucher/phí/gửi lặp, trả hàng và xác nhận hoàn tiền ngoài, quyền shop, review và upload, duyệt shop/CRUD catalog. Toàn bộ payment/JWT/WebSocket và test cũ cũng đạt. Đóng gói JAR thành công. Không chạy migration hoặc thanh toán thật. Xem [nghiệm thu](FINAL_ACCEPTANCE.md) và [API](API_REFERENCE.md).

Các đợt bên dưới là bằng chứng lịch sử, không thay thế kết quả mới.

## Đợt UI/UX ngày 03/10/2026

- Full Maven `verify` đạt **208/208 test**, 0 failure/error/skipped; đóng gói `target/goc-sach.jar` thành công lúc 14:57 (UTC+7). Bao gồm hồi quy PayOS, web/auth/CSRF/JWT, filter/sort và review sau mua.
- Catalog: 16 test service + 1 test parse mapping/JPQL, kèm HTTP assertions trong WebTest. Giá sort dùng các bản khớp điều kiện, favorites từ dữ liệu thật, tie-break ổn định; tham số sort giữ khi phân trang. Chưa thực thi query sort mới trên PostgreSQL ở lượt này.
- Review: tạo/sửa đúng chủ có đơn COMPLETED; chặn CSRF, chưa mua, trùng, nội dung/rating sai; nội dung escape và lỗi giữ input. Public review có phân trang 10, tổng/average và không lộ user/order ID. Danh mục tắt trả 404 public; chủ đơn sửa review từ lịch sử và quay về form, không redirect vào trang sách 404. Chưa nghiệm thu review/query/khóa đồng thời trên PostgreSQL thật.
- Chromium headless + Playwright trên preview: **60 lượt trang** (15 route × 320/390/768/1366 px), HTTP 200, một h1/trang, không tràn ngang hoặc lỗi console/JavaScript. Tương tác tìm kiếm, filter kết hợp/sort/phân trang/bỏ riêng khoảng giá, FAQ, Escape/focus của menu, buyer tạo/sửa review đạt. Ảnh desktop/mobile đã xem trực tiếp; không dùng kết quả này để khẳng định kiểm tra thiết bị di động thật.
- Browser script `scripts/check-ui-preview.cjs`; báo cáo `target/ui-preview/results.json`, ảnh `target/ui-preview/*.png`. Fixture 24 sách/6 danh mục/3 shop trong bộ nhớ. Restart preview trước khi chạy lại toàn bộ script vì ca tạo review thay đổi dữ liệu demo.
- Sau chỉnh CSS cuối, kiểm lại 4 trang ở 4 kích thước (16 lượt) đạt; menu tài khoản ở 320/390 px nằm trong màn hình và đóng bằng Escape; tìm kiếm và accordion FAQ khi tắt JavaScript đạt. Ảnh đầu trang để xem nhanh: `target/ui-preview/home-desktop.png` và `home-mobile.png`. Đóng gói lại assets bằng `package -DskipTests` đạt lúc 15:03; không sửa Java sau full verify.
- UI giữ CSP `script-src 'self'`; payment HTTP test chỉ cho phép đúng asset `/assets/experience.js` với defer, không chấp nhận script inline/khác nguồn. Menu vẫn dùng details native. Không thêm CDN/dependency production.
- Lần đầu test trong sandbox Windows bị chặn loopback/named pipe của Mockito; dừng rồi chạy lại ngoài sandbox mới có kết quả đầy đủ trên. Java shim không hoạt động trong phiên sandbox; dùng JDK 21.0.11 và cached Maven 3.9.11, chỉ đặt JAVA_HOME trong command.
- Không chạy SQL/migration, không gọi PostgreSQL/Gmail/Cloudinary/PayOS thật, không commit hoặc push. Thay đổi thanh toán tồn tại trước lượt UI được giữ và kiểm tra hồi quy.

## Mốc nền và PayOS ngày 02/10/2026

- `mvnw.cmd -B -ntp verify` đạt **198/198 test**, 0 lỗi/bỏ qua; đóng gói `target/goc-sach.jar` thành công. Lệnh `clean verify` còn bị quyền Windows chặn ở thư mục tạm `target/junit-tmp/hsperfdata_Admin`; chưa thay đổi quyền thư mục. Khi build trên Windows phải dừng tiến trình đang giữ chính file JAR đó; lần build cuối không còn lỗi khóa file.
- PayOS: **16 test adapter/cấu hình/kết nối, 28 test nghiệp vụ, 6 test HTTP, 1 test mapping/JPQL, 4 test setup và 5 test relay đạt**. Bao phủ chữ ký, JSON giả, callback, quyền, hủy/hết hạn, giao dịch lặp/sớm/muộn, lỗi cổng và phục hồi sau gián đoạn. Client chỉ gọi host PayOS cố định và không theo redirect để tránh truyền khóa sang host khác. Test tự động không gửi tiền.
- Lượt Chrome nền trước PayOS đã kiểm tra 320, 390, 768 và 1366 px trên 11 trang chính. Lượt PayOS này Chrome bị ngắt điều khiển: chưa xác nhận responsive hoặc chuyển trang sang cổng bằng Chrome; không dùng kết quả cũ để kết luận UI mới đã đạt.
- Preview PayOS riêng ở `127.0.0.1:18081`: đăng nhập và 5 trang checkout/thanh toán/người bán trả HTTP 200, có CSRF, no-store và không còn biểu thức Thymeleaf chưa xử lý. Đây là dữ liệu/cổng giả, không thay nghiệm thu thật.
- Bộ lọc giữ điều kiện khi chuyển trang; sản phẩm ẩn không xuất hiện trong yêu thích/đã xem; địa chỉ báo lỗi cạnh đúng trường.
- Checkout chốt đúng các dòng trong giỏ, khóa dữ liệu theo thứ tự ổn định, tách đơn theo cửa hàng và không xóa nhầm sách vừa thêm. Khóa yêu cầu được chuẩn hóa để chống tạo đơn trùng.
- Đơn hàng lưu ảnh/tên/tình trạng/giá tại thời điểm mua. Người mua và người bán chỉ xem, hủy hoặc đổi trạng thái đơn đúng phạm vi và đúng thứ tự.
- Cửa hàng bị tạm khóa không nhận đơn mới nhưng vẫn có thể hoàn tất hoặc hủy an toàn những đơn đã nhận, tránh giữ sách vô thời hạn.
- Lỗi nghiệp vụ API được trả về JSON với mã 4xx phù hợp; không biến dữ liệu sai thành trang lỗi 500.
- JPA tiếp tục dùng `hibernate.hbm2ddl.auto=validate`. Chủ dự án đã cho phép tạo database thiếu: chạy schema nền trên hai database mới trống `goc_sach` và `goc_sach_test`, 26 bảng mỗi database; không sửa `bookstore_03` hoặc database dự án khác. Không bật DDL/migration tự động.
- PostgreSQL thật: JPA validate đủ mapping đạt. Hồ sơ/địa chỉ/yêu thích/đã xem/giỏ, COD hai shop (phí 60.000 ₫), idempotency, hủy/hoàn tất và PayOS bằng cổng giả đạt; outer transaction rollback, kết nối độc lập không còn fixture.
- PostgreSQL đồng thời: hai buyer tranh một bản sách, một thành công/một 409; đúng một đơn, một item, một reservation. Retry trả đúng đơn cũ. JVM mới đọc lại được đơn/bản RESERVED đã commit; fixture `race-b8c7985576934d8bba80` được cleanup đúng phạm vi, `goc_sach_test` trống lại. Không khởi động lại container trong ca này.
- PayOS thật qua HTTP ứng dụng tại loopback 8080: đơn nghiệm thu 1, tổng 31.000 ₫; login/session và CSRF, tạo link 302, đối chiếu PENDING, bấm lại dùng cùng mã/link và một attempt, hủy chưa trả tiền 302. Database xác nhận đơn/thanh toán CANCELLED, bản sách AVAILABLE và reservation RELEASED. Không chuyển khoản/hoàn tiền; giữ lịch sử nghiệm thu, không xóa đơn/attempt.
- Chrome đã xác nhận cấu hình riêng lưu thành công và trang chủ ứng dụng thật hiển thị bản sách trả lại kho. Chưa dùng kết quả này để đánh dấu toàn bộ UI/responsive PayOS đạt.
- Quét literal 7 giá trị cấu hình bí mật trên 226 file Git/source: 0 file trùng; cấu hình local và credential nghiệm thu đều Git-ignored. Khóa không xuất hiện trong output hoặc form kết quả.
- JAR cuối: không có config riêng, PEM/key, fixture/setup/relay/preview class; 0 entry trùng literal bí mật, có client PayOS đã chặn redirect. File `config/local.properties` đã giới hạn quyền đọc/ghi cho owner, SYSTEM và Administrators, bỏ quyền Users/Authenticated Users; nội dung/owner không đổi, không sửa ACL thư mục hoặc file khác.
- Relay thử nghiệm chỉ cho JSON POST đúng `/payments/payos/webhook`, chặn đường dẫn/query/method/header không hợp lệ và body quá 32 KiB. Sau sửa đóng response stream ngay, 5/5 test relay riêng đạt; chưa khởi chạy hoặc công khai relay.

## Phần Chưa Nghiệm Thu Thật

- PostgreSQL: các ca ghi/đọc, rollback, đồng thời và JVM mới ở trên đã đạt; còn nghiệm thu toàn bộ màn hình/quyền trên dữ liệu thật và dữ liệu sau restart container, không đánh đồng restart JVM với restart database.
- Gmail/Cloudinary: cấu hình đã có nhưng lượt này không gửi OTP hoặc upload/xóa ảnh thật; test tự động không thay cho nghiệm thu hai dịch vụ.
- PayOS: ba khóa thật đã lưu an toàn, bật trong local config; tạo/query/hủy thật đã đạt. Chưa có URL HTTPS công khai hoặc đăng ký webhook; đang chờ chủ dự án cho phép tunnel chỉ nhận webhook hoặc cung cấp URL triển khai. Chưa nhận tiền/webhook giao dịch thật, chưa nghiệm thu trạng thái PAID hoặc phục hồi giao dịch thật sau restart. Không gọi tạo/hủy link là test thanh toán đủ tiền.
- Khôi phục lỗi tạo link trước khi gửi tới cổng còn giới hạn: chưa xác minh được mã nghiệp vụ NOT_FOUND từ tài liệu/SDK chính thức. Adapter giữ lỗi 503, không suy từ HTTP 404; có thể cần người vận hành kiểm tra đơn giữ sách. Test bổ sung chặn tự phân loại 401/404/500 và mã nghiệp vụ chưa được chứng minh.
- Các phần cuối kỳ còn thiếu: các bảng top 20 theo lượt bán/đánh giá/yêu thích, đơn vị vận chuyển, voucher, trả/hoàn tiền, media review, thông báo WebSocket và báo cáo doanh thu. PayOS là lựa chọn triển khai; F07 vẫn cần thầy xác nhận thay VNPAY/MOMO.
- Một số controller cũ và `App`/`Store` service locator vẫn cần tiếp tục tách theo module; đây là nợ kiến trúc, không phải lỗi build hiện tại.

## Cách Kiểm Tra Lại

1. Chạy `mvnw.cmd -B -ntp verify`.
2. Chạy `scripts/preview.ps1` để xem UI không cần database.
   Với thanh toán giả, dùng `scripts/payment-preview.ps1` tại cổng 18081; không chuyển tiền thật.
3. Trên máy hiện tại database/schema đã được chuẩn bị theo cho phép riêng. Máy khác vẫn phải kiểm tra và thực thi schema thủ công; chạy JAR với cấu hình riêng, không dùng credential nghiệm thu hoặc database bài tập.

Các công cụ `scripts/PostgresAcceptance.java`, `scripts/PostgresConcurrencyAcceptance.java` và `RuntimePaymentScenario`/`RuntimePaymentWebFlow` chỉ chạy khi gọi riêng. Không thuộc startup/test mặc định; các lớp setup/relay/fixture source test không nằm trong JAR production. Manifest `config/payment-acceptance.properties` lưu tài khoản ngẫu nhiên và ID nghiệm thu, không in hoặc commit. Shop ACTIVE fixture có audit `ACCEPTANCE_SETUP`, không giả là đã được admin duyệt qua giao diện.

Chi tiết phạm vi còn lại nằm trong [MILESTONES.md](MILESTONES.md) và [ROADMAP.md](ROADMAP.md).
