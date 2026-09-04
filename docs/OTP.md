# OTP và email

Đăng ký cần kích hoạt email trước khi đăng nhập. Quên mật khẩu dùng OTP riêng để đặt lại mật khẩu.

Mã gồm 6 chữ số, có hạn 10 phút, dùng một lần; tối đa 5 lần nhập sai, gửi lại cách nhau ít nhất 60 giây. Không lưu hoặc ghi log mã rõ. Đổi mật khẩu làm mất hiệu lực phiên, cookie và JWT cũ. Tài khoản bị quản trị khóa không thể tự mở lại bằng OTP kích hoạt hoặc đặt mật khẩu.

## Cấu hình

Dùng Mailpit để thử trên máy hoặc Gmail với App Password. Điền file cấu hình riêng theo mẫu trong `config/`, không đưa mật khẩu thật vào Git.

Gmail dùng STARTTLS cổng 587. Khóa `otp.secret` cần ngẫu nhiên ít nhất 32 byte và giữ ổn định khi khởi động lại.

## Giới hạn

Gửi mail hiện đồng bộ; giới hạn tần suất bổ sung ở bộ nhớ, phù hợp chạy một máy. Khi triển khai nhiều máy cần dùng hàng đợi và bộ đếm chung.

Kiểm thử email giả không chứng minh thư đến hộp thư thật. Trước demo cần thử cả kích hoạt và đặt lại mật khẩu, kiểm tra Inbox/Spam.
