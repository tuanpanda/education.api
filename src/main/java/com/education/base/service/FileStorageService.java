package com.education.base.service;

import com.education.base.entity.FileEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Quản lý lưu trữ và truy xuất file vật lý của hệ thống EDUCATION.
 */
public interface FileStorageService {

    /**
     * Lưu file vật lý vào {@code {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{tenfile}} và ghi metadata
     * vào bảng {@code SYS_ATTACHED_FILES} (cột {@code FILE_PATH} chỉ lưu đường dẫn tương đối).
     * Chỉ nhận định dạng thuộc allow-list (ảnh jpg/png/gif/webp, pdf, doc/docx, xls/xlsx, csv, txt, zip),
     * kiểm tra bằng magic bytes; {@code CONTENT_TYPE} lưu xuống là MIME do server nhận diện.
     *
     * @param file        file multipart do client upload.
     * @param moduleName  Module nghiệp vụ sở hữu file.
     * @param referenceId ID bản ghi nghiệp vụ liên quan (có thể {@code null}).
     * @return {@link FileEntity} đã lưu, đã có ID sinh từ sequence.
     */
    FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId);

    /**
     * Lưu file với thư mục con (ví dụ {@code IMPORT} → {@code STUDENT/IMPORT/YYYY/MM/}).
     *
     * @param subFolder thư mục con chỉ gồm chữ, số, gạch dưới; {@code null} thì dùng cấu trúc mặc định.
     */
    FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId, String subFolder);

    /**
     * Lưu nội dung nhị phân đã đọc sẵn (dùng khi cùng một file vừa parse Excel vừa lưu audit).
     * <p>
     * {@code contentType} của client chỉ mang tính tham khảo: định dạng thực tế được nhận diện lại bằng
     * phần mở rộng + magic bytes (xem {@code SafeFileType}) và file ngoài allow-list bị từ chối.
     */
    FileEntity storeBytes(byte[] content, String originalFilename, String contentType,
                          String moduleName, Long referenceId, String subFolder);

    /**
     * Nạp file vật lý thành {@link Resource} để trả về cho client (view/download),
     * có kiểm tra chống Directory Traversal.
     *
     * @param fileId ID file trong {@code SYS_ATTACHED_FILES}.
     * @return {@link Resource} trỏ tới file vật lý.
     */
    Resource loadFileAsResource(Long fileId);

    /**
     * Lấy metadata của file theo ID (chỉ file chưa bị xóa mềm).
     *
     * @param fileId ID file.
     * @return {@link FileEntity} tương ứng.
     */
    FileEntity getFileEntity(Long fileId);

    /**
     * Lấy danh sách file theo Module và ID tham chiếu thông qua Standalone Procedure.
     *
     * @param moduleName  Module nghiệp vụ.
     * @param referenceId ID bản ghi nghiệp vụ liên quan.
     * @return danh sách file đính kèm.
     */
    List<FileEntity> getFilesByRef(String moduleName, Long referenceId);
}
