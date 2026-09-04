# Ba bản nộp độc lập

## Cập nhật 04/09 — tách repository

Repository bài tập: https://github.com/letrongbao/goc-sach-exercises . Đồ án cuối kỳ phát triển riêng: https://github.com/letrongbao/goc-sach . Xem [phân chia](REPOSITORIES.md).

`submission-03-r1` là mốc source có bản sửa, đã chạy 61 tests pass ngày 04/09; chưa nghiệm thu PostgreSQL/email thật. Bài 01/02 vẫn dùng snapshot cũ và chưa backport sửa form/upload. Các folder submissions trên máy và script export vẫn trỏ mốc cũ, không tự đồng bộ theo source chính. Khi xuất bản sửa cần tag mới/folder mới, không ghi đè.

Các repository được tạo Private. Trước khi nộp, phải cấp quyền đúng người chấm hoặc được chủ project đồng ý chuyển Public. Bài 02–03 nộp link repo bài tập kèm `/tree/<tag>` vào UTEx, không nộp link đồ án thay thế. Chưa có bài nào được gửi lên UTEx.

Còn cần đọc đủ PDF Bài 01, xác nhận/bổ sung count và phân trang Category theo hướng dẫn JPA, backport bản sửa, và kiểm thử DB/email thật. Tag không phải chứng nhận đã hoàn tất mọi yêu cầu.

## Ghi nhận lịch sử sau rà soát 03/09 (đã được cập nhật ở trên)

Các tag và thư mục trong bảng dưới là snapshot cũ, vẫn có các lỗi đã ghi ở [FIXES](FIXES.md); không coi chúng là bản sửa mới. Bản sửa Bài 03 được chốt riêng bằng submission-03-r1. Lỗi xử lý ID danh mục cần được backport vào Bài 01/02 khi phát hành revision tương ứng.

| Tag local | Thư mục xuất | Phạm vi |
|---|---|---|
| submission-01-r1 | submissions/Bai_01_Servlet_JDBC | Cookie/Session, Category JDBC |
| submission-02-r1 | submissions/Bai_02_Servlet_JPA | Chức năng Bài 01 dùng JPA |
| submission-03 | submissions/Bai_03_OTP_Product | OTP, Product và phân trang |

Chạy scripts/export-submissions.ps1 sau khi các tag tồn tại. Script xuất đúng cây Git của tag, không sửa tag, không ZIP và không push. Từ chối ghi đè folder có sẵn. Thư mục export không kèm .git hoặc cấu hình riêng; clone repository để xem lịch sử.

Trong mỗi thư mục: README, config mẫu, source, SQL thủ công, tài liệu, Maven Wrapper. Build từng bản bằng mvnw.cmd clean verify; database theo cấu hình riêng. Manifest ở thư mục submissions ghi SHA/tag, không ghép source khác phiên bản.

Bài 01 nộp source theo UTEx. Bài 02/03 dùng URL GitHub trỏ đúng tag đã nghiệm thu, kèm repository URL ở đầu tài liệu. Kiểm tra tag đã được push và người chấm có quyền truy cập trước khi nộp. Bài 03 dùng revision submission-03-r1 thay cho snapshot submission-03 khi kiểm tra bản sửa.

Tag submission-01 và submission-02 gốc giữ nguyên để đối chiếu lịch sử. Dùng bản r1 khi kiểm tra/nộp vì đã sửa xử lý URL quản trị có tham số đường dẫn và font tiếng Việt. Không đổi hoặc ghi đè tag cũ.

## Trước khi tự ZIP/nộp

- [ ] Chạy ứng dụng bằng PostgreSQL đã chuẩn bị; restart vẫn giữ dữ liệu.
- [ ] Chạy CRUD, phân quyền và các trang bắt buộc trên DB thật.
- [ ] Bài 03: nhận và dùng cả OTP kích hoạt/reset qua Gmail thật.
- [ ] Đảm bảo người chấm được quyền xem GitHub nếu repository private.
- [ ] Không nộp .git, target, .tools, config bí mật, uploads cá nhân.
- [ ] Giữ nguyên tag đã công bố. Nếu sửa sau đó, tạo tag revision mới và ghi rõ.

Deadline đã đọc: Bài 01 26/08/2026 08:37; Bài 02 26/08/2026 10:45; Bài 03 06/09/2026 23:45 (UTC+7). Hai bài đầu đã quá hạn khi bắt đầu triển khai; hỏi giảng viên việc mở lại. Không backdate commit.
