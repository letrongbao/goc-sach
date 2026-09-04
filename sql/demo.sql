-- MANUAL ONLY. Replace password hash placeholders first; never execute against production.
-- Use your own demo passwords, hash with HashPassword CLI. No live credentials.
INSERT INTO users(username,email,password_hash,role,active,auth_version,created_at) VALUES
 ('admin','admin@example.test','REPLACE_WITH_BCRYPT_HASH','ADMIN',true,0,CURRENT_TIMESTAMP),
 ('reader','reader@example.test','REPLACE_WITH_BCRYPT_HASH','USER',true,0,CURRENT_TIMESTAMP);
INSERT INTO categories(name,image,active) VALUES
 ('Văn học','',true),('Kỹ năng sống','',true),('Công nghệ','',true),('Thiếu nhi','',true);
-- 13 fictional sample books to verify latest-10 and pagination 6/6/1.
INSERT INTO products(category_id,title,author,description,price,stock,image,created_at,updated_at)
SELECT c.id, books.title, 'Tủ sách Góc', 'Dữ liệu minh họa cho bài tập Lập trình Web, không phải nội dung sách thương mại.',
       75000 + books.n * 5000, 5 + books.n, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
 (1,'Miền ký ức xanh'),(2,'Những ngày có nắng'),(3,'Lập trình từ trang đầu'),
 (4,'Một hành trình nhỏ'),(5,'Bên kia mùa hạ'),(6,'Chuyện của những vì sao'),
 (7,'Sống một đời sâu sắc'),(8,'Tư duy của người làm nghề'),(9,'Gửi những ngày mai'),
 (10,'Đi qua miền gió'),(11,'Khoảng lặng giữa thành phố'),(12,'Nghệ thuật bắt đầu lại'),(13,'Một đời đáng đọc')
) AS books(n,title)
CROSS JOIN categories c WHERE c.name='Văn học'
ORDER BY books.n;
