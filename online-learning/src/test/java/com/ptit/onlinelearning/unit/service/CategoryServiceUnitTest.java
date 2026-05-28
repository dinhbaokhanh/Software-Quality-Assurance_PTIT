package com.ptit.onlinelearning.unit.service;

import com.ptit.onlinelearning.exception.DataNotFoundException;
import com.ptit.onlinelearning.model.Category;
import com.ptit.onlinelearning.repository.CategoryRepository;
import com.ptit.onlinelearning.request.CategoryRequest;
import com.ptit.onlinelearning.request.UpdateCategoryRequest;
import com.ptit.onlinelearning.service.category.CategoryService;
import com.ptit.onlinelearning.unit.config.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import static org.junit.jupiter.api.Assertions.*;

/**
 **/
public class CategoryServiceUnitTest extends BaseUnitTest {

    @Autowired
    CategoryService categoryService;
    // Repository dùng để Arrange & CheckDB
    @Autowired
    CategoryRepository categoryRepository;

    // Tạo và lưu một Category mẫu vào DB
    private Category persistCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDescription("Mô tả cho " + name);
        category.setImage("https://education.oracle.com/file/general/p-80-java.png");
        category.setIsActive(true);
        return categoryRepository.save(category);
    }


    // createCategory
    // TEST CASE ID: TC_CA_01
    @Test
    @org.springframework.transaction.annotation.Transactional
    void createCategory_ValidInput_ReturnsCategory() {
        // 1. Arrange
        CategoryRequest request = new CategoryRequest();
        request.setName("Lập trình Java");
        request.setDescription("Khóa học Java cơ bản đến nâng cao");
        request.setImage("https://education.oracle.com/file/general/p-80-java.png");
        request.setParentId(null);

        long countBefore = categoryRepository.count();

        // 2. Act
        Category result = categoryService.createCategory(request);

        // 3. Assert & CheckDB
        assertNotNull(result, "Category trả về không được null");
        assertNotNull(result.getId(), "Category phải có ID sau khi save");
        assertEquals("Lập trình Java", result.getName(), "Name phải khớp với request");
        assertEquals("Khóa học Java cơ bản đến nâng cao", result.getDescription());
        assertEquals("https://education.oracle.com/file/general/p-80-java.png", result.getImage());
        // Kiểm tra isActive mặc định phải là true
        assertNotNull(result.getIsActive(), "isActive không được null sau khi tạo");
        assertTrue(result.getIsActive(), "Category mới tạo phải có isActive = true mặc định");

        long countAfter = categoryRepository.count();
        assertEquals(countBefore + 1, countAfter, "DB phải có thêm 1 category sau khi tạo thành công");
    }


    //  getCategoryById
    //  Branch 1: ID tồn tại  → trả về Category
    //  Branch 2: ID không tồn tại → throw DataNotFoundException

    // TEST CASE ID: TC_CA_02
    // Branch 1: ID tồn tại — trả về đúng Category.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategoryById_ExistingId_ReturnsCategory() {
        // 1. Arrange
        Category inserted = persistCategory("Thiết kế đồ họa");
        long countBefore = categoryRepository.count();

        // 2. Act
        Category result = categoryService.getCategoryById(inserted.getId());

        // 3. Assert & CheckDB
        assertNotNull(result, "Kết quả không được null");
        assertEquals(inserted.getId(), result.getId(), "ID phải khớp");
        assertEquals("Thiết kế đồ họa", result.getName(), "Name phải khớp");

        assertEquals(countBefore, categoryRepository.count(),
                "getCategoryById không được thay đổi số lượng record trong DB");
    }

    // TEST CASE ID: TC_CA_03
    // Branch 2: ID không tồn tại — throw DataNotFoundException.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategoryById_NonExistingId_ThrowsDataNotFoundException() {
        // 1. Arrange
        Long nonExistingId = -999L;
        long countBefore = categoryRepository.count();

        // 2. Act & Assert
        DataNotFoundException exception = assertThrows(
                DataNotFoundException.class,
                () -> categoryService.getCategoryById(nonExistingId),
                "ID không tồn tại phải throw DataNotFoundException"
        );

        assertTrue(exception.getMessage().contains(String.valueOf(nonExistingId)),
                "Message phải chứa ID không tồn tại");

        assertEquals(countBefore, categoryRepository.count(),
                "DB không được thay đổi khi getCategoryById với ID không tồn tại");
    }


    //  getCategories
    //  Branch A: tất cả null
    //  Branch B: search != null
    //  Branch C: isActive != null
    //  Branch D: parentId != null
    //  Branch E: sortOrder không hợp lệ → Sort.Direction.fromString throw

    // TEST CASE ID: TC_CA_04
    // Branch A: search=null, isActive=null, parentId=null — phân trang cơ bản.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategories_AllNullFilters_ReturnsValidPage() {
        // 1. Arrange
        persistCategory("Category A");
        persistCategory("Category B");
        persistCategory("Category C");
        long totalInDB = categoryRepository.count();

        // 2. Act
        Page<Category> resultPage = categoryService.getCategories(
                1, 10, "id", "asc", null, null, null
        );

        // 3. Assert & CheckDB
        assertNotNull(resultPage, "Page trả về không được null");
        assertEquals(totalInDB, resultPage.getTotalElements(),
                "TotalElements phải khớp chính xác với count() trong DB");
    }

    // TEST CASE ID: TC_CA_05
    // Branch B: search != null — chỉ trả về category khớp keyword.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategories_WithSearchFilter_ReturnsMatchingCategories() {
        // 1. Arrange
        persistCategory("Khoa hoc du lieu");
        persistCategory("Marketing Online");

        // 2. Act
        Page<Category> resultPage = categoryService.getCategories(
                1, 10, "id", "asc", "Khoa hoc du lieu", null, null
        );

        // 3. Assert
        assertNotNull(resultPage);
        assertTrue(
                resultPage.getContent().stream()
                        .anyMatch(c -> c.getName().contains("Khoa hoc du lieu")),
                "Kết quả phải chứa category có tên khớp keyword"
        );
    }

    // TEST CASE ID: TC_CA_06
    // Branch C: isActive != null — chỉ trả về category theo trạng thái.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategories_FilterByIsActiveFalse_ReturnsOnlyInactiveCategories() {
        // 1. Arrange
        persistCategory("Category Active");

        Category inactiveCategory = new Category();
        inactiveCategory.setName("Category Inactive");
        inactiveCategory.setIsActive(false);
        categoryRepository.save(inactiveCategory);

        // 2. Act
        Page<Category> resultPage = categoryService.getCategories(
                1, 10, "id", "asc", null, false, null
        );

        // 3. Assert
        assertNotNull(resultPage);
        assertTrue(resultPage.getTotalElements() >= 1,
                "Phải có ít nhất 1 category inactive trong kết quả");
        assertTrue(
                resultPage.getContent().stream()
                        .allMatch(c -> Boolean.FALSE.equals(c.getIsActive())),
                "Tất cả category trong kết quả phải có isActive = false"
        );
    }

    // TEST CASE ID: TC_CA_07
    // Branch D: parentId != null — chỉ trả về category con của parentId đó.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategories_FilterByParentId_ReturnsChildCategories() {
        // 1. Arrange — tạo category cha
        Category parent = persistCategory("Category Cha");
        Long parentId = parent.getId();

        // Tạo category con gán parentId
        Category child = new Category();
        child.setName("Category Con");
        child.setParentId(parentId);
        child.setIsActive(true);
        categoryRepository.save(child);

        // 2. Act
        Page<Category> resultPage = categoryService.getCategories(
                1, 10, "id", "asc", null, null, parentId
        );

        // 3. Assert
        assertNotNull(resultPage);
        assertTrue(
                resultPage.getContent().stream()
                        .allMatch(c -> parentId.equals(c.getParentId())),
                "Tất cả kết quả phải có parentId khớp"
        );
    }

    // TEST CASE ID: TC_CA_08
    // Branch E: sortOrder không hợp lệ → throw IllegalArgumentException.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void getCategories_InvalidSortOrder_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getCategories(1, 10, "id", "INVALID", null, null, null),
                "sortOrder không hợp lệ phải throw IllegalArgumentException"
        );
    }


    //  updateCategory
    //  Branch 1:  ID không tồn tại → throw DataNotFoundException
    //  Branch 2:  name != null hợp lệ → set name
    //  Branch 2b: name != null nhưng blank → không được ghi đè
    //  Branch 3:  name == null → giữ nguyên
    //  Branch 4:  description != null → set description
    //  Branch 5:  description == null → giữ nguyên
    //  Branch 6:  image != null → set image
    //  Branch 7:  image == null → giữ nguyên
    //  Branch 8:  parentId != null hợp lệ → set parentId
    //  Branch 8b: parentId != null nhưng = chính nó → throw IllegalArgumentException
    //  Branch 9:  parentId == null → giữ nguyên

    // TEST CASE ID: TC_CA_09
    // Branch 1: ID không tồn tại → throw DataNotFoundException.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_NonExistingId_ThrowsDataNotFoundException() {
        // 1. Arrange
        Long nonExistingId = -1L;
        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setName("Tên mới");
        long countBefore = categoryRepository.count();

        // 2. Act & Assert
        DataNotFoundException exception = assertThrows(
                DataNotFoundException.class,
                () -> categoryService.updateCategory(nonExistingId, updateRequest),
                "ID không tồn tại phải throw DataNotFoundException"
        );

        assertTrue(exception.getMessage().contains(String.valueOf(nonExistingId)),
                "Message phải chứa ID không tồn tại");
        assertEquals(countBefore, categoryRepository.count(),
                "DB không được thay đổi khi updateCategory với ID không tồn tại");
    }

    // TEST CASE ID: TC_CA_10
    // Branch 2,3,4,5,6,7: name != null hợp lệ, description != null, image = null → giữ nguyên,
    //                      parentId = null → giữ nguyên.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_NonNullFields_UpdatesCorrectlyInDB() {
        // 1. Arrange
        Category original = persistCategory("Tên cũ");
        Long categoryId = original.getId();
        String oldImage = original.getImage();

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setName("Tên mới");
        updateRequest.setDescription("Mô tả mới");
        updateRequest.setImage(null);       // null → không được ghi đè
        updateRequest.setParentId(null);    // null → không được ghi đè

        // 2. Act
        Category updated = categoryService.updateCategory(categoryId, updateRequest);

        // 3. Assert & CheckDB
        assertEquals("Tên mới", updated.getName(), "Name phải được cập nhật");
        assertEquals("Mô tả mới", updated.getDescription(), "Description phải được cập nhật");
        assertEquals(oldImage, updated.getImage(), "Image phải giữ nguyên khi request null");

        Category fromDb = categoryRepository.findById(categoryId).orElseThrow();
        assertEquals("Tên mới", fromDb.getName(), "Name trong DB phải là giá trị mới");
        assertEquals("Mô tả mới", fromDb.getDescription(), "Description trong DB phải là giá trị mới");
        assertEquals(oldImage, fromDb.getImage(), "Image trong DB phải giữ nguyên");
    }

    // TEST CASE ID: TC_CA_11
    // Branch 2b: name != null nhưng là blank "   " → không được ghi đè
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_BlankName_ShouldNotUpdateName() {
        // 1. Arrange
        Category original = persistCategory("Tên hợp lệ");
        String oldName = original.getName();

        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setName("   "); // blank nhưng != null → service hiện tại vẫn set

        // 2. Act
        Category updated = categoryService.updateCategory(original.getId(), req);

        // 3. Assert & CheckDB
        assertEquals(oldName, updated.getName(),
                "Không được cập nhật name bằng chuỗi toàn khoảng trắng");

        Category fromDb = categoryRepository.findById(original.getId()).orElseThrow();
        assertEquals(oldName, fromDb.getName(),
                "Name trong DB không được thay đổi khi request là blank");
    }

    // TEST CASE ID: TC_CA_12
    // Branch 3,5,7,9: tất cả field đều null → không có gì thay đổi.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_AllNullFields_NothingChangedInDB() {
        // 1. Arrange
        Category original = new Category();
        original.setName("Tên gốc không đổi");
        original.setDescription("Mô tả gốc");
        original.setImage("https://education.oracle.com/file/general/p-80-java.png");
        original.setIsActive(true);
        Category saved = categoryRepository.save(original);

        UpdateCategoryRequest allNullRequest = new UpdateCategoryRequest();

        // 2. Act
        Category result = categoryService.updateCategory(saved.getId(), allNullRequest);

        // 3. Assert & CheckDB
        assertEquals("Tên gốc không đổi", result.getName(), "Name không được thay đổi khi request null");
        assertEquals("Mô tả gốc", result.getDescription(), "Description không được thay đổi khi request null");
        assertEquals("https://education.oracle.com/file/general/p-80-java.png", result.getImage(),
                "Image không được thay đổi khi request null");

        Category fromDb = categoryRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Tên gốc không đổi", fromDb.getName(), "Name trong DB phải giữ nguyên");
        assertEquals("Mô tả gốc", fromDb.getDescription(), "Description trong DB phải giữ nguyên");
    }

    // TEST CASE ID: TC_CA_13
    // Branch 6,8: image != null và parentId != null hợp lệ → cả hai được ghi đè.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_ImageAndParentIdNonNull_UpdatesBothFields() {
        // 1. Arrange — tạo category cha và category cần update
        Category parent = persistCategory("Category Cha");
        Category original = persistCategory("Tên Gốc");
        Long categoryId = original.getId();

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setImage("https://example.com/new-image.png");  // != null → ghi đè
        updateRequest.setParentId(parent.getId());                     // != null → ghi đè

        // 2. Act
        Category updated = categoryService.updateCategory(categoryId, updateRequest);

        // 3. Assert & CheckDB
        assertEquals("https://example.com/new-image.png", updated.getImage(),
                "Image phải được ghi đè khi request != null");
        assertEquals(parent.getId(), updated.getParentId(),
                "ParentId phải được ghi đè khi request != null");

        Category fromDb = categoryRepository.findById(categoryId).orElseThrow();
        assertEquals("https://example.com/new-image.png", fromDb.getImage(),
                "Image trong DB phải là giá trị mới");
        assertEquals(parent.getId(), fromDb.getParentId(),
                "ParentId trong DB phải là giá trị mới");
    }

    // TEST CASE ID: TC_CA_14
    // Branch 8b: parentId != null nhưng = chính ID của category → throw IllegalArgumentException
    @Test
    @org.springframework.transaction.annotation.Transactional
    void updateCategory_SelfParentId_ShouldThrowException() {
        // 1. Arrange
        Category cat = persistCategory("Category tự tham chiếu");
        long countBefore = categoryRepository.count();

        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setParentId(cat.getId()); // parentId = chính nó

        // 2. Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> categoryService.updateCategory(cat.getId(), req),
                "Không được phép set parentId = chính category đó");

        assertEquals(countBefore, categoryRepository.count(),
                "DB không được thay đổi khi self-reference");
    }


    //  deleteCategory
    //  Branch 1:  ID tồn tại, không có con → xóa thành công
    //  Branch 1b: ID tồn tại, còn con → throw IllegalStateException
    //  Branch 2:  ID không tồn tại → throw DataNotFoundException

    // TEST CASE ID: TC_CA_15
    // Branch 1: ID tồn tại, không có con — xóa thành công, count giảm 1.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void deleteCategory_ExistingId_DeletedFromDB() {
        // 1. Arrange
        Category toDelete = persistCategory("Category cần xóa");
        Long deletedId = toDelete.getId();
        assertTrue(categoryRepository.findById(deletedId).isPresent(),
                "Category phải tồn tại trong DB trước khi xóa");
        long countBefore = categoryRepository.count();

        // 2. Act
        assertDoesNotThrow(
                () -> categoryService.deleteCategory(deletedId),
                "deleteCategory không được throw exception khi ID hợp lệ"
        );

        // 3. Assert & CheckDB
        categoryRepository.flush();
        assertEquals(countBefore - 1, categoryRepository.count(),
                "DB phải giảm 1 category sau khi xóa thành công");
        assertFalse(categoryRepository.findById(deletedId).isPresent(),
                "Category đã xóa không được tìm thấy trong DB");
    }

    // TEST CASE ID: TC_CA_16
    // Branch 1b: ID tồn tại nhưng còn category con → throw IllegalStateException
    @Test
    @org.springframework.transaction.annotation.Transactional
    void deleteCategory_HasChildren_ShouldThrowException() {
        // 1. Arrange
        Category parent = persistCategory("Category Cha");

        Category child = new Category();
        child.setName("Category Con");
        child.setParentId(parent.getId());
        child.setIsActive(true);
        categoryRepository.save(child);

        long countBefore = categoryRepository.count();

        // 2. Act & Assert
        assertThrows(IllegalStateException.class,
                () -> categoryService.deleteCategory(parent.getId()),
                "Không được xóa category khi còn category con");

        // 3. CheckDB
        assertEquals(countBefore, categoryRepository.count(),
                "DB không được thay đổi khi xóa category còn con");
        assertTrue(categoryRepository.findById(parent.getId()).isPresent(),
                "Category cha vẫn phải tồn tại trong DB");
    }

    // TEST CASE ID: TC_CA_17
    // Branch 2: ID không tồn tại — throw DataNotFoundException.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void deleteCategory_NonExistingId_ThrowsDataNotFoundException() {
        // 1. Arrange
        Long nonExistingId = -999L;
        long countBefore = categoryRepository.count();

        // 2. Act & Assert
        DataNotFoundException exception = assertThrows(
                DataNotFoundException.class,
                () -> categoryService.deleteCategory(nonExistingId),
                "ID không tồn tại phải throw DataNotFoundException"
        );

        assertTrue(exception.getMessage().contains(String.valueOf(nonExistingId)),
                "Message phải chứa ID không tồn tại");
        assertEquals(countBefore, categoryRepository.count(),
                "DB không được thay đổi khi xóa ID không tồn tại");
    }
}