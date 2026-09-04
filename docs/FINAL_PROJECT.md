# Phạm vi đồ án Mộc Sách

## Nguồn gốc

Kế thừa repository https://github.com/letrongbao/utex-web-exercises tại commit 8a47d12 (revision submission-03-r1). Lịch sử commit được giữ nguyên, không đổi tác giả hoặc thời gian; các commit trước khi tách là quá trình làm bài tập, không phải đóng góp mới của nhóm đồ án.

Repo này không chứa các folder submissions sao chép, cấu hình bí mật, target hoặc database cá nhân. SQL chỉ để chủ project xem và thực thi thủ công; không chạy tạo bảng/migration tự động.

## Định hướng, chưa phải đăng ký đã duyệt

Đề tài 15: quản lý chuỗi cửa hàng sách cũ, hướng Spring Boot + JSP/JSTL + Bootstrap + JPA + PostgreSQL + JWT + WebSocket + Cloudinary. Cần xác nhận đề tài, thành viên, rubric và hạn nộp chính thức với giảng viên. Chưa có căn cứ coi việc tạo repo là đăng ký đề tài.

## Các mốc cần triển khai

1. Nghiệm thu nền tảng trên PostgreSQL và Gmail thật; xử lý các giới hạn trong VERIFICATION.md.
2. Chuyển Servlet controller/config sang Spring Boot, MVC và Security; giữ WAR/JSP và nghiệp vụ đã kiểm thử.
3. Bổ sung đặc trưng sách cũ, hồ sơ người mua, tìm kiếm, giỏ hàng và đặt hàng/tồn kho.
4. Thêm cửa hàng, Seller/Manager/Admin, phân quyền theo cửa hàng, thanh toán và quản lý đơn.
5. Triển khai các công nghệ/chức năng còn lại theo rubric đã xác nhận; test end-to-end, báo cáo, demo và phân công có lịch sử đóng góp thật.

Hiện chỉ có nền tảng Servlet/JPA của bài tập. Bộ 61 test kiểm tra nền tảng bằng fake/mock/SMTP loopback, không chứng minh đồ án hoàn chỉnh hoặc PostgreSQL/Gmail thật hoạt động.

## Nộp và cộng tác

Bài tập nộp qua repo utex-web-exercises. Đồ án dùng repo moc-sach và mốc nghiệm thu riêng sau này. Mời đúng thành viên/người chấm nếu giữ Private; không tự chuyển Public. Commit mới theo Conventional Commits, không backdate, không sửa tag đã công bố. Bảng phân công chỉ ghi công việc/người làm thực tế.
